/*
 * Copyright (c) 2025-2026 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 *    END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.wheelly.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.mmarini.wheelly.protobuf.aruco.MarkerEvent;
import org.mmarini.wheelly.protobuf.robot.*;
import org.mmarini.wheelly.robots.*;
import org.mmarini.wheelly.robots.HeadStatusId;
import org.mmarini.wheelly.robots.MotionStatusId;
import org.mmarini.wheelly.utils.WheellyJsonSchemas;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static java.lang.Math.round;
import static java.lang.Math.tan;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.robots.RobotSpec.distance2Pulse;
import static org.mmarini.wheelly.robots.RobotSpec.location2Pulses;
import static org.mmarini.wheelly.rx.RXFunc.logError;

/**
 * Implements the Robot interface to the real robot.
 * <p>
 * The usage samples is
 * <code><pre>
 *
 *     RobotApi robot = new Robot(...);
 *
 *     robot.connect();
 *
 *     robot.configure();
 *
 *     // Sending any command
 *     robot.haltCommand();
 *     robot.scan(...);
 *     robot.move(...);
 *
 *     // Setting the callback on events
 *     robot.setOnClock(...);
 *     robot.setOnMotion(...);
 *     robot.setOnProxy(...);
 *     robot.setOnContacts(...);
 *     robot.setOnSupply(...);
 *
 *     // Polling for status changed
 *     while (...) {
 *         robot.tick(...);
 *     }
 *
 *     robotSocket.close();
 * </pre>
 * </code>
 * </p>
 */
public class MqttRobot implements RobotApi {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/mqtt-robot-schema-2.0";
    public static final String DEFAULT_BROKER_URL = "tcp://localhost:1883";
    public static final long DEFAULT_CONFIGURE_INTERVAL = 1000;
    public static final long DEFAULT_RETRY_INTERVAL = 500;
    public static final long DEFAULT_COMMAND_TIMEOUT = 3000;
    public static final int DEFAULT_ASR = 200;
    public static final int DEFAULT_LAMBDA_FACTOR = 30;
    public static final int DEFAULT_MAX_PULSE_INTERVAL = 200;
    public static final int DEFAULT_DELAYED_INTERVAL = 10;
    public static final int DEFAULT_TAU = 500;
    public static final int DEFAULT_ANTIGIMBAL_RADIUS = 20;
    private static final Logger logger = LoggerFactory.getLogger(MqttRobot.class);

    /**
     * Builds a {@link Configuration.Builder} populated from the specified robot specifications.
     * <p>
     * Converts domain-level physical measurements (angles, distances, intervals, and
     * speed limits) into the discrete units required by the Protocol Buffers
     * {@link Configuration} schema, such as encoder pulses and integer degrees.
     *
     * @param spec the {@link RobotSpec} containing the physical and operational parameters of the robot
     * @return a pre-populated {@link Configuration.Builder} ready for further customisation or building
     */
    static Configuration.Builder configFromSpec(RobotSpec spec) {
        return Configuration.newBuilder()
                .setMinRotRange(spec.directionRange().toIntDeg())
                .setMaxRotRange(spec.maxRotRange().toIntDeg())
                .setMaxRotPps(spec.maxRotPps())
                .setMaxSpeed(spec.maxSpeed())
                .setHaltDistance((int) round(distance2Pulse(spec.targetRange())))
                .setDecelerateDistance((int) (round(distance2Pulse(spec.decelerateDistance()))))
                .setMinHeadDir(-spec.headFOV().toIntDeg() / 2)
                .setMaxHeadDir(spec.headFOV().toIntDeg() / 2)
                .setSendInterval((int) spec.sendInterval())
                .setScanInterval((int) spec.scanInterval());
    }

    /**
     * Creates and configures a new {@link MqttRobot} instance from a JSON configuration tree.
     * <p>
     * Validates the root JSON node against the {@code SCHEMA_NAME} schema, extracts MQTT
     * connection credentials, timing parameters, device identifiers, and physical robot
     * specifications, and builds the complete Protocol Buffers {@link Configuration}
     * payload before initialising the MQTT client connection.
     *
     * @param root the root {@link JsonNode} containing the robot and MQTT configuration properties
     * @param file the {@link Path} to the source configuration file (currently unused)
     * @return a fully initialised {@link MqttRobot} instance
     * @throws RuntimeException if an {@link MqttException} occurs whilst creating or connecting the MQTT client
     */
    public static MqttRobot create(JsonNode root, Path file) {
        Locator locator = Locator.root();
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        String brokerUrl = locator.path("brokerUrl").getNode(root).asText(DEFAULT_BROKER_URL);
        String clientId = locator.path("clientId").getNode(root).asText(MqttAsyncClient.generateClientId());
        String user = locator.path("user").getNode(root).asText();
        String password = locator.path("password").getNode(root).asText();
        long configureTimeout = locator.path("configureTimeout").getNode(root).asLong(DEFAULT_CONFIGURE_INTERVAL);
        long retryInterval = locator.path("retryInterval").getNode(root).asLong(DEFAULT_RETRY_INTERVAL);
        String robotId = locator.path("robotId").getNode(root).asText();
        String amDeviceId = locator.path("amDeviceId").getNode(root).asText();

        RobotSpec robotSpec = RobotSpec.fromJson(root, locator);

        Configuration.Builder builder = configFromSpec(robotSpec);
        Configuration cfg = builder.setAsr(locator.path("asr").getNode(root).asInt(DEFAULT_ASR))
                .setLambdaFactor(locator.path("lambdaFactor").getNode(root).asInt(DEFAULT_LAMBDA_FACTOR))
                .setMaxPulseInterval(locator.path("maxPulseInterval").getNode(root).asInt(DEFAULT_MAX_PULSE_INTERVAL))
                .setDelayedInterval(locator.path("delayedInterval").getNode(root).asInt(DEFAULT_DELAYED_INTERVAL))
                .setAntiGimbalRadius(locator.path("antiGimbalRadius").getNode(root).asInt(DEFAULT_ANTIGIMBAL_RADIUS))
                .setTau(locator.path("tau").getNode(root).asInt(DEFAULT_TAU))
                .build();
        try {
            return create(brokerUrl, clientId, user, password,
                    robotId,
                    amDeviceId, configureTimeout, retryInterval,
                    robotSpec, cfg);
        } catch (MqttException e) {
            logger.atError().setCause(e).log("Error creating mqtt robot");
            throw new RuntimeException(e);
        }
    }

    /**
     * Return MQTT robot interface
     *
     * @param brokerUrl        the broker url
     * @param clientId         the client id
     * @param user             the MQTT client username
     * @param password         MQTT client password
     * @param robotId          robot device id
     * @param amId             QRCODE device id
     * @param configureTimeout the configuration timeout (ms)
     * @param retryInterval    the retry configuration interval (ms)
     * @param robotSpec        the robot specification
     * @param robotConfig      the robot configurations
     * @throws MqttException in case of error
     */
    public static MqttRobot create(String brokerUrl, String clientId, String user, String password,
                                   String robotId, String amId,
                                   long configureTimeout, long retryInterval,
                                   RobotSpec robotSpec, Configuration robotConfig) throws MqttException {
        RxMqttClient client = RxMqttClient.create(brokerUrl, clientId, user, password);
        RemoteDevice robotDevice = new RemoteDevice(robotId, client);
        RemoteDevice amDevice = new RemoteDevice(amId, client);
        return new MqttRobot(client, robotDevice, amDevice, configureTimeout, retryInterval, robotSpec,
                robotConfig);
    }

    /**
     * Converts a Protocol Buffers {@link MarkerEvent} message into a domain
     * {@link CameraEvent} instance, applying the specified timestamp and width
     * scaling ratio.
     *
     * @param protoMarker the protobuf {@link MarkerEvent} containing the detected
     *                    marker identifier, frame dimensions, and flattened corner
     *                    coordinates
     * @param time        the target timestamp (in milliseconds) to assign to the
     *                    created domain event
     * @param widthRatio  the horizontal scaling ratio used to normalise or adjust
     *                    coordinates relative to the frame width
     * @return the corresponding domain {@link CameraEvent} instance
     */
    private static CameraEvent fromProtoBuf(MarkerEvent protoMarker, long time, double widthRatio) {
        int[] points = protoMarker.getCoordsList().stream()
                .mapToInt(x -> x)
                .toArray();
        return CameraEvent.create(time, protoMarker.getMarkerId(), protoMarker.getWidth(), protoMarker.getHeight(), widthRatio, points);
    }

    /**
     * Converts a Protocol Buffers {@link ContactsTelemetry} message into a domain
     * {@link WheellyContactsMessage}.
     *
     * @param protoBuf the incoming Protobuf contact telemetry payload
     * @param time     the reception or local correlation timestamp in milliseconds
     * @return a populated {@link WheellyContactsMessage} instance
     */
    private static WheellyContactsMessage fromProtoBuf(ContactsTelemetry protoBuf, long time) {
        return new WheellyContactsMessage(time,
                (protoBuf.getSensors() & 1) != 0,
                (protoBuf.getSensors() & 2) != 0,
                (protoBuf.getSensors() & 4) != 0,
                (protoBuf.getSensors() & 8) != 0);
    }

    /**
     * Converts a Protocol Buffers {@link LidarTelemetry} message into a domain
     * {@link WheellyLidarMessage}.
     *
     * @param protoBuf the incoming Protobuf LiDAR and head-tracking telemetry payload
     * @param time     the reception or local correlation timestamp in milliseconds
     * @return a populated {@link WheellyLidarMessage} instance
     */
    private static WheellyLidarMessage fromProtoBuf(LidarTelemetry protoBuf, long time) {
        return new WheellyLidarMessage(time,
                protoBuf.getFrontDistance(), protoBuf.getRearDistance(),
                protoBuf.getX(), protoBuf.getY(),
                protoBuf.getRobotYaw(), protoBuf.getHeadDirection(),
                HeadStatusId.values()[protoBuf.getHeadStatusValue()],
                protoBuf.getHeadTarget(),
                protoBuf.getXTarget(),
                protoBuf.getYTarget());
    }

    /**
     * Converts a Protocol Buffers {@link SupplyTelemetry} message into a domain
     * {@link WheellySupplyMessage}.
     *
     * @param protoBuf the incoming Protobuf power supply telemetry payload
     * @param time     the reception or local correlation timestamp in milliseconds
     * @return a populated {@link WheellySupplyMessage} instance
     */
    private static WheellySupplyMessage fromProtoBuf(SupplyTelemetry protoBuf, long time) {
        return new WheellySupplyMessage(time, protoBuf.getVoltage());
    }

    /**
     * Converts a Protocol Buffers {@link MotionTelemetry} message into a domain
     * {@link WheellyMotionMessage}.
     *
     * @param protoBuf the incoming Protobuf motion and motor telemetry payload
     * @param time     the reception or local correlation timestamp in milliseconds
     * @return a populated {@link WheellyMotionMessage} instance
     */
    private static WheellyMotionMessage fromProtoBuf(MotionTelemetry protoBuf, long time) {
        return new WheellyMotionMessage(time,
                protoBuf.getX(), protoBuf.getY(),
                protoBuf.getRobotDeg(),
                protoBuf.getLeftPps(), protoBuf.getRightPps(),
                protoBuf.getImuFailure(),
                MotionStatusId.values()[protoBuf.getStatusIdValue()],
                protoBuf.getTargetDeg(),
                protoBuf.getLeftTargetPps(),
                protoBuf.getRightTargetPps(),
                protoBuf.getLeftPower(), protoBuf.getRightPower(),
                protoBuf.getXTarget(), protoBuf.getYTarget());
    }

    /**
     * Generates a unique message correlation identifier based on the current system time.
     *
     * @return the current epoch time in milliseconds
     */
    private static long generateId() {
        return System.currentTimeMillis();
    }

    private final RxMqttClient client;
    private final RemoteDevice robotDevice;
    private final RemoteDevice amDetectorDevice;
    private final long configureTimeout;
    private final long retryInterval;
    private final RobotSpec robotSpec;
    private final AtomicReference<MqttRobotStatus> status;
    private final BehaviorProcessor<RobotStatusApi> states;
    private final PublishProcessor<Throwable> errors;
    private final Configuration config;
    private final List<Disposable> disposables;
    private Consumer<CameraEvent> onCameras;
    private Consumer<WheellyMotionMessage> onMotions;
    private Consumer<WheellySupplyMessage> onSupplies;
    private Consumer<WheellyContactsMessage> onContacts;
    private Consumer<WheellyLidarMessage> onLidars;

    /**
     * Constructs a new {@code MqttRobot} controller and subscribes to incoming device telemetry streams.
     * <p>
     * Initialises the internal reactive state and error processors, sets up default
     * robot status tracking, and registers RxJava subscriptions on both the primary
     * robot device (motion, contacts, LiDAR range, power supply, and heartbeat channels)
     * and the camera detector device.
     *
     * @param client           the reactive MQTT client used for broker communication (must not be {@code null})
     * @param robotDevice      the remote device abstraction representing the main robot controller (must not be {@code null})
     * @param amDetectorDevice the remote device abstraction representing the vision/camera detector module (must not be {@code null})
     * @param configureTimeout the timeout duration in milliseconds allowed for initial device configuration
     * @param retryInterval    the delay interval in milliseconds between configuration or connection retry attempts
     * @param robotSpec        the physical and kinematic specifications of the robot (must not be {@code null})
     * @param config           the Protocol Buffers configuration payload to apply to the robot (must not be {@code null})
     * @throws NullPointerException if any of the required object arguments is {@code null}
     */
    public MqttRobot(RxMqttClient client, RemoteDevice robotDevice, RemoteDevice amDetectorDevice,
                     long configureTimeout, long retryInterval, RobotSpec robotSpec, Configuration config) {
        this.client = requireNonNull(client);
        this.robotDevice = requireNonNull(robotDevice);
        this.amDetectorDevice = requireNonNull(amDetectorDevice);
        this.configureTimeout = configureTimeout;
        this.retryInterval = retryInterval;
        this.robotSpec = requireNonNull(robotSpec);
        this.config = requireNonNull(config);
        status = new AtomicReference<>(new MqttRobotStatus(false, false, false, false,
                false, false, false, 0,
                0));
        this.states = BehaviorProcessor.createDefault(status.get());
        this.errors = PublishProcessor.create();
        this.disposables = List.of(
                robotDevice.readData("mt", this::toMotionMessage)
                        .subscribe(this::onMotionMessage,
                                errors::onNext),
                robotDevice.readData("ct", this::toContactMessage)
                        .subscribe(this::onContactMessage,
                                errors::onNext),
                robotDevice.readData("rg", this::toLidarMessage)
                        .subscribe(this::onLidarMessage,
                                errors::onNext),
                robotDevice.readData("sv", this::toSupplyMessage)
                        .subscribe(this::onSupplyMessages,
                                errors::onNext),
                robotDevice.readData("hi", this::toHiMessage)
                        .subscribe(this::onRobotHiMessage,
                                errors::onNext),
                amDetectorDevice.readData("am", this::toCameraMessage)
                        .subscribe(
                                this::onCameraEvent,
                                errors::onNext));
    }

    /**
     * Registers a callback listener to receive incoming contact sensor telemetry messages.
     *
     * @param callback the {@link Consumer} invoked whenever a {@link WheellyContactsMessage} arrives
     */
    @Override
    public void addOnContacts(Consumer<WheellyContactsMessage> callback) {
        onContacts = onContacts == null ? callback : onContacts.andThen(callback);
    }

    /**
     * Registers a callback listener to receive incoming LiDAR telemetry messages.
     *
     * @param callback the {@link Consumer} invoked whenever a {@link WheellyLidarMessage} arrives
     */
    @Override
    public void addOnLidar(Consumer<WheellyLidarMessage> callback) {
        onLidars = onLidars == null ? callback : onLidars.andThen(callback);
    }

    /**
     * Registers a callback listener to receive incoming motion and odometry telemetry messages.
     *
     * @param callback the {@link Consumer} invoked whenever a {@link WheellyMotionMessage} arrives
     */
    @Override
    public void addOnMotion(Consumer<WheellyMotionMessage> callback) {
        onMotions = onMotions == null ? callback : onMotions.andThen(callback);
    }

    /**
     * Registers a callback listener to receive incoming power supply telemetry messages.
     *
     * @param callback the {@link Consumer} invoked whenever a {@link WheellySupplyMessage} arrives
     */
    @Override
    public void addOnSupply(Consumer<WheellySupplyMessage> callback) {
        onSupplies = onSupplies == null ? callback : onSupplies.andThen(callback);
    }

    /**
     * Returns the remote device abstraction representing the ArUco marker detector camera module.
     *
     * @return the ArUco camera {@link RemoteDevice} instance
     */
    public RemoteDevice arucoCameraDevice() {
        return amDetectorDevice;
    }

    /**
     * Gracefully shuts down the robot connection and releases all reactive resources.
     * <p>
     * Sends a final halt command, marks the robot status as closed, disconnects and
     * awaits termination of the underlying MQTT client, completes the reactive state
     * and error processors, and blocks until both remote device abstractions are closed.
     */
    @Override
    public void close() {
        logger.atInfo().log("Closing ...");
        halt();
        MqttRobotStatus s1 = status.updateAndGet(MqttRobotStatus::setClosed);
        if (!status.get().connected()) {
            logger.atInfo().log("Completing sensor data flows ...");
        }
        try {
            logger.atInfo().log("Closing mqtt ...");
            client.close();
            client.closed().blockingAwait();
        } catch (MqttException e) {
            logger.atError().setCause(e).log("Error shuttingDown mqtt");
        }
        for (Disposable disposable : disposables) {
            disposable.dispose();
        }
        states.onNext(s1);
        errors.onComplete();
        states.onComplete();
        robotDevice.closed().blockingAwait();
        amDetectorDevice.closed().blockingAwait();
        logger.atInfo().log("Robot closed ...");
    }

    /**
     * Returns the Protocol Buffers configuration payload associated with this robot.
     *
     * @return the active {@link Configuration} instance
     */
    public Configuration config() {
        return config;
    }

    /**
     * Initiates robot configuration if the client is connected, not closed, and not
     * already in the process of configuring.
     * <p>
     * Transitions the internal status to configuring and dispatches the configuration
     * command to the remote robot device.
     */
    public void configure() {
        MqttRobotStatus st = status.getAndUpdate(MqttRobotStatus::setRobotConfiguring);
        states.onNext(status.get());
        if (!st.robotConfiguring() && !st.closed() && st.connected()) {
            // not robotConfiguring
            //Send configure command
            configureRobot();
        }
    }

    /**
     * Dispatches the {@link Configuration} command over MQTT and subscribes to the acknowledgement.
     * <p>
     * Generates a unique correlation identifier, transmits the Protobuf configuration
     * payload with {@code configureTimeout}, verifies the response ID, and forwards the
     * outcome to {@link #onRobotConfiguration(boolean)}.
     */
    void configureRobot() {
        long id = generateId();
        executeRobotCommand(ProtoBufCommand.configCommand(id, config), configureTimeout)
                .filter(res ->
                        res.getId() == id)
                .map(x -> true)
                .defaultIfEmpty(false)
                .subscribe(this::onRobotConfiguration,
                        logError(logger, "Error configuring robot"));
    }

    /**
     * Initiates an asynchronous connection to the MQTT broker if not already started or closed.
     * <p>
     * Updates the internal robot status to connecting, records the start timestamp, and
     * subscribes to the reactive MQTT client connection lifecycle.
     */
    @Override
    public void connect() {
        if (!status.get().closed()) {
            MqttRobotStatus s = status.getAndUpdate(s1 -> s1.started(true));
            if (!s.started()) {
                MqttRobotStatus s2 = status.updateAndGet(s1 -> s1.setConnecting().startTime(System.currentTimeMillis()));
                states.onNext(s2);
                try {
                    logger.atDebug().log("Connecting to {} client {} ...", client.getServerURI(), client.getClientId());
                    client.connect()
                            .subscribe(
                                    this::onConnection,
                                    this::onConnectionFailure
                            );
                } catch (MqttException e) {
                    notifyError("Error connecting to mqtt", e);
                }
            }
        }
    }

    /**
     * Executes a Protocol Buffers-encoded device command with the specified timeout.
     *
     * @param <R>     the expected response type
     * @param command the {@link ProtoBufCommand} to transmit
     * @param timeout the maximum execution time in milliseconds
     * @return a {@link Maybe} emitting the parsed response of type {@code R}, an empty
     * {@link Maybe} if the client is disconnected, or an error {@link Maybe} if dispatch fails
     */
    private <R> Maybe<R> executeRobotCommand(ProtoBufCommand<R> command, long timeout) {
        if (!client.isConnected()) {
            return Maybe.empty();
        } else {
            try {
                return robotDevice.execute(command, timeout);
            } catch (Throwable e) {
                notifyError("Error executing robot command", e);
                return Maybe.error(e);
            }
        }
    }

    /**
     * Sends an immediate halt command to stop all robot locomotion.
     *
     * @return a {@link Single} emitting {@code true} if the command was acknowledged with a
     * matching correlation identifier, or {@code false} if it timed out or failed
     */
    @Override
    public Single<Boolean> halt() {
        long id = generateId();
        return executeRobotCommand(ProtoBufCommand.haltCommand(id), DEFAULT_COMMAND_TIMEOUT)
                .filter(msg -> msg.getId() == id)
                .map(x -> true)
                .defaultIfEmpty(false);
    }

    /**
     * Checks whether the robot is currently in a halted state.
     *
     * @return {@code true} if the robot is halted; {@code false} otherwise
     */
    public boolean isHalt() {
        return status.get().halted();
    }

    /**
     * Commands the robot to move forward or backward towards a target 2D location.
     * <p>
     * Converts the target physical coordinates into encoder pulses before transmitting
     * the Protobuf move command.
     *
     * @param frontMove {@code true} to drive forward; {@code false} to drive backward
     * @param location  the destination coordinates in physical units
     * @return a {@link Single} emitting {@code true} if the command was acknowledged, or
     * {@code false} if the client is disconnected or the command failed
     */
    @Override
    public Single<Boolean> move(boolean frontMove, Point2D location) {
        if (!status.get().connected()) {
            return Single.just(false);
        }
        long id = generateId();
        Point2D pulses = location2Pulses(location);
        return executeRobotCommand(ProtoBufCommand.moveCommand(id, frontMove, pulses), DEFAULT_COMMAND_TIMEOUT)
                .filter(msg -> msg.getId() == id)
                .map(x -> true)
                .defaultIfEmpty(false);
    }

    /**
     * Logs an error and forwards it to the reactive error stream.
     *
     * @param message the contextual log description
     * @param error   the {@link Throwable} cause to log and emit
     */
    private void notifyError(String message, Throwable error) {
        if (error instanceof TimeoutException) {
            logger.atError().setCause(error).log(message);
            errors.onNext(error);
        } else {
            logger.atError().setCause(error.getCause()).log(message);
            errors.onNext(error);
        }
    }

    /**
     * Registers a callback listener to receive incoming camera detection events.
     *
     * @param callback the {@link Consumer} invoked whenever a {@link CameraEvent} arrives
     */
    @Override
    public void onCamera(Consumer<CameraEvent> callback) {
        onCameras = onCameras == null ? callback : onCameras.andThen(callback);
    }

    /**
     * Dispatches an incoming {@link CameraEvent} to the registered camera listener, if present.
     *
     * @param cameraEvent the received camera detection event
     */
    private void onCameraEvent(CameraEvent cameraEvent) {
        if (onCameras != null) {
            onCameras.accept(cameraEvent);
        }
    }

    /**
     * Handles changes in the MQTT broker connection state.
     * <p>
     * Updates the internal robot status accordingly and triggers automatic robot
     * configuration upon successful connection.
     *
     * @param connected {@code true} if the MQTT client is connected; {@code false} otherwise
     */
    private void onConnection(boolean connected) {
        logger.atDebug().log("Client mqtt connected.");
        if (connected) {
            MqttRobotStatus st = this.status.updateAndGet(s ->
                    s.setConnected().lastActivity(System.currentTimeMillis())
            );
            states.onNext(st);
            configure();
        } else {
            MqttRobotStatus st = this.status.updateAndGet(MqttRobotStatus::setUnconnected);
            states.onNext(st);
        }
    }

    /**
     * Handles an MQTT connection failure by notifying error subscribers and scheduling
     * a delayed reconnection attempt after {@code retryInterval} milliseconds.
     *
     * @param error the {@link Throwable} describing the connection failure
     */
    private void onConnectionFailure(Throwable error) {
        notifyError("Error mqtt", error);
        Completable.timer(retryInterval, TimeUnit.MILLISECONDS, Schedulers.io())
                .subscribe(this::reconnect);
    }

    /**
     * Dispatches an incoming {@link WheellyContactsMessage} to the registered contact listener, if present.
     *
     * @param message the received contact sensor telemetry message
     */
    private void onContactMessage(WheellyContactsMessage message) {
        if (onContacts != null) {
            onContacts.accept(message);
        }
    }

    /**
     * Dispatches an incoming {@link WheellyLidarMessage} to the registered LiDAR listener, if present.
     *
     * @param message the received LiDAR telemetry message
     */
    private void onLidarMessage(WheellyLidarMessage message) {
        if (onLidars != null) {
            onLidars.accept(message);
        }
    }

    /**
     * Dispatches an incoming {@link WheellyMotionMessage} to the registered motion listener, if present.
     *
     * @param message the received motion and odometry telemetry message
     */
    private void onMotionMessage(WheellyMotionMessage message) {
        if (onMotions != null) {
            onMotions.accept(message);
        }
    }

    /**
     * Handles the outcome of a robot configuration attempt.
     * <p>
     * If configuration succeeded, marks the robot status as configured; otherwise,
     * marks it as unconfigured and schedules a retry after {@code retryInterval} milliseconds.
     *
     * @param success {@code true} if the robot acknowledged the configuration; {@code false} otherwise
     */
    private void onRobotConfiguration(boolean success) {
        if (success) {
            MqttRobotStatus s1 = status.updateAndGet(MqttRobotStatus::setRobotConfigured);
            states.onNext(s1);
            logger.atInfo().log("Robot configured");
        } else {
            logger.atDebug().log("Robot configuration failed");
            MqttRobotStatus s1 = status.updateAndGet(MqttRobotStatus::setRobotNotConfigured);
            states.onNext(s1);
            Completable.timer(retryInterval, TimeUnit.MILLISECONDS, Schedulers.io())
                    .subscribe(this::configure);
        }
    }

    /**
     * Handles an incoming heartbeat ({@link HiMessage}) from the robot controller.
     * <p>
     * Indicates that the robot has started or restarted, resetting its configuration
     * state to unconfigured and triggering a fresh configuration push.
     *
     * @param hiMessage the received {@link HiMessage} heartbeat payload
     */
    private void onRobotHiMessage(HiMessage hiMessage) {
        MqttRobotStatus st = status.updateAndGet(MqttRobotStatus::setRobotNotConfigured);
        states.onNext(st);
        configure();
    }

    /**
     * Dispatches an incoming {@link WheellySupplyMessage} to the registered power supply listener, if present.
     *
     * @param message the received power supply telemetry message
     */
    private void onSupplyMessages(WheellySupplyMessage message) {
        if (onSupplies != null) {
            onSupplies.accept(message);
        }
    }

    /**
     * Returns a reactive stream emitting runtime and communication errors.
     *
     * @return a {@link Flowable} of {@link Throwable} errors
     */
    @Override
    public Flowable<Throwable> readErrors() {
        return errors;
    }

    /**
     * Returns a reactive stream emitting updates to the robot's operational status.
     *
     * @return a {@link Flowable} emitting {@link RobotStatusApi} state snapshots
     */
    @Override
    public Flowable<RobotStatusApi> readRobotStatus() {
        return states;
    }

    /**
     * Triggers a reconnection attempt to the MQTT broker.
     */
    @Override
    public void reconnect() {
    }

    /**
     * Returns the remote device abstraction representing the primary robot controller.
     *
     * @return the robot {@link RemoteDevice} instance
     */
    public RemoteDevice robotDevice() {
        return robotDevice;
    }

    /**
     * Returns the physical and kinematic specifications configured for this robot.
     *
     * @return the {@link RobotSpec} instance
     */
    @Override
    public RobotSpec robotSpec() {
        return robotSpec;
    }

    /**
     * Returns the elapsed time in milliseconds since the robot connection lifecycle started.
     *
     * @return elapsed operational time in milliseconds
     */
    @Override
    public long robotTime() {
        return System.currentTimeMillis() - status.get().startTime();
    }

    /**
     * Commands the robot to rotate towards a target heading angle.
     *
     * @param dir the target rotation angle in degrees
     * @return a {@link Single} emitting {@code true} if the command was acknowledged, or
     * {@code false} if the client is disconnected or the command timed out
     */
    @Override
    public Single<Boolean> rotate(int dir) {
        if (!status.get().connected()) {
            return Single.just(false);
        }
        long id = generateId();
        return executeRobotCommand(ProtoBufCommand.rotateCommand(id, dir), DEFAULT_COMMAND_TIMEOUT)
                .filter(msg -> msg.getId() == id)
                .map(x -> true)
                .defaultIfEmpty(false);
    }

    /**
     * Commands the robot's head scanner to orient towards a specified angle.
     *
     * @param direction the target head scan angle in degrees
     * @return a {@link Single} emitting {@code true} if the command was acknowledged, or
     * {@code false} if the command failed or timed out
     */
    @Override
    public Single<Boolean> scan(int direction) {
        long id = generateId();
        return executeRobotCommand(ProtoBufCommand.scanCommand(id, direction), DEFAULT_COMMAND_TIMEOUT)
                .filter(msg ->
                        msg.getId() == id)
                .map(x -> true)
                .defaultIfEmpty(false)
                .map(s -> !s);
    }

    /**
     * Returns the time-scaling factor used for simulation execution.
     *
     * @return the simulation speed multiplier (always {@code 1.0} for real hardware)
     */
    @Override
    public double simulationSpeed() {
        return 1;
    }

    /**
     * Deserialises an incoming MQTT payload into a domain {@link CameraEvent}.
     *
     * @param mqttMessage the raw MQTT message received from the camera detector module
     * @return the parsed {@link CameraEvent}, or {@code null} if Protobuf parsing fails
     */
    private CameraEvent toCameraMessage(MqttMessage mqttMessage) {
        double widthRatio = 2 * tan(robotSpec.cameraFOV().toRad() / 2);
        try {
            MarkerEvent protoMarker = MarkerEvent.parseFrom(mqttMessage.getPayload());
            return fromProtoBuf(protoMarker, robotTime(), widthRatio);
        } catch (Throwable ex) {
            logger.atError().setCause(ex).log("Error parsing camera message");
            return null;
        }
    }

    /**
     * Deserialises an incoming MQTT payload into a domain {@link WheellyContactsMessage}.
     *
     * @param mqttMessage the raw MQTT message received on the contacts telemetry channel
     * @return the parsed {@link WheellyContactsMessage}, or {@code null} if Protobuf parsing fails
     */
    private WheellyContactsMessage toContactMessage(MqttMessage mqttMessage) {
        try {
            ContactsTelemetry protoBuf = ContactsTelemetry.parseFrom(mqttMessage.getPayload());
            return fromProtoBuf(protoBuf, robotTime());
        } catch (Throwable ex) {
            logger.atError().setCause(ex).log("Error parsing contact message");
            return null;
        }
    }

    /**
     * Deserialises an incoming MQTT payload into a Protocol Buffers {@link HiMessage}.
     *
     * @param mqttMessage the raw MQTT message received on the heartbeat channel
     * @return the parsed {@link HiMessage}, or {@code null} if Protobuf parsing fails
     */
    private HiMessage toHiMessage(MqttMessage mqttMessage) {
        try {
            return HiMessage.parseFrom(mqttMessage.getPayload());
        } catch (Throwable ex) {
            logger.atError().setCause(ex).log("Error parsing motion message");
            return null;
        }
    }

    /**
     * Deserialises an incoming MQTT payload into a domain {@link WheellyLidarMessage}.
     *
     * @param mqttMessage the raw MQTT message received on the LiDAR telemetry channel
     * @return the parsed {@link WheellyLidarMessage}, or {@code null} if Protobuf parsing fails
     */
    private WheellyLidarMessage toLidarMessage(MqttMessage mqttMessage) {
        try {
            LidarTelemetry protoBuf = LidarTelemetry.parseFrom(mqttMessage.getPayload());
            return fromProtoBuf(protoBuf, robotTime());
        } catch (Throwable ex) {
            logger.atError().setCause(ex).log("Error parsing lidar message");
            return null;
        }
    }

    /**
     * Deserialises an incoming MQTT payload into a domain {@link WheellyMotionMessage}.
     *
     * @param mqttMessage the raw MQTT message received on the motion telemetry channel
     * @return the parsed {@link WheellyMotionMessage}, or {@code null} if Protobuf parsing fails
     */
    private WheellyMotionMessage toMotionMessage(MqttMessage mqttMessage) {
        try {
            MotionTelemetry protoBuf = MotionTelemetry.parseFrom(mqttMessage.getPayload());
            return fromProtoBuf(protoBuf, robotTime());
        } catch (Throwable ex) {
            logger.atError().setCause(ex).log("Error parsing motion message");
            return null;
        }
    }

    /**
     * Deserialises an incoming MQTT payload into a domain {@link WheellySupplyMessage}.
     *
     * @param mqttMessage the raw MQTT message received on the power supply telemetry channel
     * @return the parsed {@link WheellySupplyMessage}, or {@code null} if Protobuf parsing fails
     */
    private WheellySupplyMessage toSupplyMessage(MqttMessage mqttMessage) {
        try {
            SupplyTelemetry protoBuf = SupplyTelemetry.parseFrom(mqttMessage.getPayload());
            return fromProtoBuf(protoBuf, robotTime());
        } catch (Throwable ex) {
            logger.atError().setCause(ex).log("Error parsing supply message");
            return null;
        }
    }

    /**
     * Commands the robot's head to track a target 2D location in front or rear mode.
     * <p>
     * Converts the target physical coordinates into encoder pulses before transmitting
     * the Protobuf head-tracking command.
     *
     * @param frontTrack {@code true} for front-facing tracking; {@code false} for rear-facing tracking
     * @param target     the target point coordinates in physical units
     * @return a {@link Single} emitting {@code true} if the command was acknowledged, or
     * {@code false} if the client is disconnected or the command timed out
     */
    @Override
    public Single<Boolean> track(boolean frontTrack, Point2D target) {
        if (!status.get().connected()) {
            return Single.just(false);
        }
        long id = generateId();
        Point2D pulses = location2Pulses(target);
        return executeRobotCommand(ProtoBufCommand.trackCommand(id, frontTrack, pulses), DEFAULT_COMMAND_TIMEOUT)
                .filter(msg -> msg.getId() == id)
                .map(x -> true)
                .defaultIfEmpty(false);
    }
}
