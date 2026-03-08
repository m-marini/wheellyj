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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.mmarini.wheelly.apis.*;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static java.lang.Math.round;
import static java.lang.Math.tan;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.RobotSpec.location2Pulses;
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
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/mqtt-robot-schema-1.2";
    public static final String DEFAULT_BROKER_URL = "tcp://localhost:1883";
    public static final long DEFAULT_CONFIGURE_INTERVAL = 1000;
    public static final long DEFAULT_RETRY_INTERVAL = 500;
    public static final long DEFAULT_COMMAND_TIMEOUT = 3000;
    private static final Logger logger = LoggerFactory.getLogger(MqttRobot.class);

    /**
     * Returns the robot from configuration
     *
     * @param root the configuration document
     * @param file the configuration file
     */
    public static MqttRobot create(JsonNode root, File file) {
        Locator locator = Locator.root();
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        String brokerUrl = locator.path("brokerUrl").getNode(root).asText(DEFAULT_BROKER_URL);
        String clientId = locator.path("clientId").getNode(root).asText(MqttAsyncClient.generateClientId());
        String user = locator.path("user").getNode(root).asText();
        String password = locator.path("password").getNode(root).asText();
        long configureTimeout = locator.path("configureTimeout").getNode(root).asLong(DEFAULT_CONFIGURE_INTERVAL);
        long retryInterval = locator.path("retryInterval").getNode(root).asLong(DEFAULT_RETRY_INTERVAL);
        String robotId = locator.path("robotId").getNode(root).asText();
        String qrDeviceId = locator.path("qrDeviceId").getNode(root).asText();

        RobotSpec robotSpec = RobotSpec.fromJson(root, locator);
        ObjectMapper objectMapper = new ObjectMapper(new JsonFactory());
        Locator cameraConfigCommandsLoc = locator.path("config");
        String[] cfg = !cameraConfigCommandsLoc.getNode(root).isMissingNode()
                ? cameraConfigCommandsLoc.elements(root).map(l -> {
                    JsonNode json = l.getNode(root);
                    try {
                        return objectMapper.writeValueAsString(json);
                    } catch (JsonProcessingException e) {
                        logger.atError().setCause(e).log("Error converting configuration to json");
                        return "";
                    }
                })
                .filter(Predicate.not(String::isEmpty))
                .toArray(String[]::new)
                : new String[0];

        try {
            return create(brokerUrl, clientId, user, password,
                    robotId,
                    qrDeviceId, configureTimeout, retryInterval,
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
     * @param user             the mqtt client username
     * @param password         mqtt client password
     * @param robotId          robot device id
     * @param qrId             QRCODE device id
     * @param configureTimeout the configuration timeout (ms)
     * @param retryInterval    the retry configuration interval (ms)
     * @param robotSpec        the robot specification
     * @param robotConfig      the robot configurations
     * @throws MqttException in case of error
     */
    public static MqttRobot create(String brokerUrl, String clientId, String user, String password,
                                   String robotId, String qrId,
                                   long configureTimeout, long retryInterval,
                                   RobotSpec robotSpec, String[] robotConfig) throws MqttException {
        RxMqttClient client = RxMqttClient.create(brokerUrl, clientId, user, password);
        RemoteDevice robotDevice = new RemoteDevice(robotId, client);
        RemoteDevice qrDevice = new RemoteDevice(qrId, client);
        return new MqttRobot(client, robotDevice, qrDevice, configureTimeout, retryInterval, robotSpec,
                robotConfig);
    }

    private final RxMqttClient client;
    private final RemoteDevice robotDevice;
    private final RemoteDevice qrDetectorDevice;
    private final long configureTimeout;
    private final long retryInterval;
    private final RobotSpec robotSpec;
    private final AtomicReference<MqttRobotStatus> status;
    private final BehaviorProcessor<RobotStatusApi> states;
    private final PublishProcessor<Throwable> errors;
    private final PublishProcessor<CameraEvent> cameraEvents;
    private final PublishProcessor<WheellyMotionMessage> motionMessages;
    private final PublishProcessor<WheellyLidarMessage> lidarMessages;
    private final PublishProcessor<WheellyContactsMessage> contactMessages;
    private final PublishProcessor<WheellySupplyMessage> supplyMessages;
    private final String[] config;

    /**
     * Creates the mqtt robot
     *
     * @param client           the mqtt client
     * @param robotDevice      the robot device
     * @param qrDetectorDevice the qr detector device
     * @param configureTimeout the configuration timeout (ms)
     * @param retryInterval    the retry configuration interval (ms)
     * @param robotSpec        the robot specification
     * @param config           the configuratios
     */
    public MqttRobot(RxMqttClient client, RemoteDevice robotDevice, RemoteDevice qrDetectorDevice, long configureTimeout, long retryInterval, RobotSpec robotSpec, String[] config) {
        this.client = requireNonNull(client);
        this.robotDevice = requireNonNull(robotDevice);
        this.qrDetectorDevice = requireNonNull(qrDetectorDevice);
        this.configureTimeout = configureTimeout;
        this.retryInterval = retryInterval;
        this.robotSpec = requireNonNull(robotSpec);
        this.config = requireNonNull(config);
        status = new AtomicReference<>(new MqttRobotStatus(false, false, false, false,
                false, false, false, 0, 0,
                0, null));
        this.states = BehaviorProcessor.createDefault(status.get());
        this.errors = PublishProcessor.create();
        this.cameraEvents = PublishProcessor.create();
        this.lidarMessages = PublishProcessor.create();
        this.motionMessages = PublishProcessor.create();
        this.contactMessages = PublishProcessor.create();
        this.supplyMessages = PublishProcessor.create();
        robotDevice.readData("mt", this::toMotionMessage)
                .subscribe(motionMessages::onNext,
                        motionMessages::onError,
                        motionMessages::onComplete
                );
        robotDevice.readData("ct", this::toContactMessage)
                .subscribe(contactMessages::onNext,
                        contactMessages::onError,
                        contactMessages::onComplete);
        robotDevice.readData("rg", this::toLidarMessage)
                .subscribe(lidarMessages::onNext,
                        lidarMessages::onError,
                        lidarMessages::onComplete);
        robotDevice.readData("sv", this::toSupplyMessage)
                .subscribe(supplyMessages::onNext,
                        supplyMessages::onError,
                        supplyMessages::onComplete);
        robotDevice.readData("hi", m -> new String(m.getPayload()))
                .subscribe(this::onRobotHiMessage,
                        supplyMessages::onError,
                        supplyMessages::onComplete);
        qrDetectorDevice.readData("qr", this::toCameraMessage)
                .subscribe(
                        cameraEvents::onNext,
                        cameraEvents::onError,
                        cameraEvents::onComplete);
    }

    @Override
    public Single<Boolean> backward(Point2D location) {
        if (!status.get().connected()) {
            return Single.just(false);
        }
        Point2D pulses = location2Pulses(location);
        int x = (int) round(pulses.getX());
        int y = (int) round(pulses.getY());
        return executeRobotCommand("bw", x + "," + y, DEFAULT_COMMAND_TIMEOUT)
                .isEmpty()
                .map(s -> !s);
    }

    /**
     * Returns the configuration command
     */
    public String[] config() {
        return config;
    }

    @Override
    public void close() {
        logger.atInfo().log("Closing ...");
        halt();
        MqttRobotStatus s1 = status.updateAndGet(MqttRobotStatus::setClosed);
        if (!status.get().connected()) {
            logger.atInfo().log("Completing sensor data flows ...");
            cameraEvents.onComplete();
            contactMessages.onComplete();
            motionMessages.onComplete();
            supplyMessages.onComplete();
        }
        try {
            logger.atInfo().log("Closing mqtt ...");
            client.close();
            client.closed().blockingAwait();
        } catch (MqttException e) {
            logger.atError().setCause(e).log("Error closing mqtt");
        }
        states.onNext(s1);
        errors.onComplete();
        states.onComplete();
        robotDevice.closed().blockingAwait();
        qrDetectorDevice.closed().blockingAwait();
        logger.atInfo().log("Robot closed ...");
    }

    /**
     * Forces robot configuration
     */
    public void configure() {
        MqttRobotStatus st = status.getAndUpdate(s ->
                s.setRobotConfiguring().configIndex(0));
        states.onNext(status.get());
        if (!st.robotConfiguring() && !st.closed() && st.connected()) {
            // if not robotConfiguring
            // Send configure command
            configureRobot();
        }
    }

    void configureRobot() {
        MqttRobotStatus st = status.get();
        if (st.configIndex() < config.length) {
            // Send configuration file
            configureRobot(config[st.configIndex()])
                    .doOnSuccess(success ->
                            logger.atDebug().log("Configuration {}/{} {}",
                                    status.get().configIndex() + 1, config.length, success ? "OK" : "KO"))
                    .subscribe(this::onRobotConfiguration,
                            logError(logger, "Error configuring robot"));
        } else {
            // Robot configured
            MqttRobotStatus s1 = status.updateAndGet(MqttRobotStatus::setRobotConfigured);
            states.onNext(s1);
            logger.atInfo().log("Robot configured");
        }
    }

    /**
     * Execute single configure command
     *
     * @param command the command
     */
    private Single<Boolean> configureRobot(String command) {
        return executeRobotCommand("cf", command, configureTimeout)
                .map(res ->
                        res.equals(command))
                .defaultIfEmpty(false);
    }

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
     * Executes the command returning the command response
     *
     * @param command the command
     * @param arg     the command argument
     * @param timeout the execution timeout
     */
    private Maybe<String> executeRobotCommand(String command, String arg, long timeout) {
        if (!client.isConnected()) {
            return Maybe.empty();
        } else {
            try {
                return robotDevice.execute(StringCommand.create(command, arg), timeout);
            } catch (Throwable e) {
                notifyError("Error executing robot command", e);
                return Maybe.error(e);
            }
        }
    }

    @Override
    public Single<Boolean> forward(Point2D location) {
        if (!status.get().connected()) {
            return Single.just(false);
        }
        Point2D pulses = location2Pulses(location);
        int x = (int) round(pulses.getX());
        int y = (int) round(pulses.getY());
        return executeRobotCommand("fw", x + "," + y, DEFAULT_COMMAND_TIMEOUT)
                .isEmpty()
                .map(s -> !s);
    }

    @Override
    public Single<Boolean> halt() {
        return executeRobotCommand("ha", "ha", DEFAULT_COMMAND_TIMEOUT)
                .isEmpty()
                .map(s -> !s);
    }

    @Override
    public boolean isHalt() {
        return status.get().halted();
    }

    @Override
    public Single<Boolean> move(int dir, int speed) {
        if (!status.get().connected()) {
            return Single.just(false);
        }
        return executeRobotCommand("mv", dir + "," + speed, DEFAULT_COMMAND_TIMEOUT)
                .isEmpty()
                .map(s -> !s);
    }

    /**
     * Notifies error
     *
     * @param message the message
     * @param error   the error
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
     * Handles the broker client connection event
     *
     * @param connected true if the client is connected
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
     * Handles the mqtt connection failure
     *
     * @param error the error
     */
    private void onConnectionFailure(Throwable error) {
        notifyError("Error mqtt", error);
        Completable.timer(retryInterval, TimeUnit.MILLISECONDS, Schedulers.computation())
                .subscribe(this::reconnect);
    }

    /**
     * Handles the configuration success
     *
     * @param success true if configuration success
     */
    private void onRobotConfiguration(boolean success) {
        if (success) {
            MqttRobotStatus s1 = status.updateAndGet(MqttRobotStatus::nextConfigIndex);
            states.onNext(s1);
            configureRobot();
        } else {
            logger.atDebug().log("Robot configuration failed");
            MqttRobotStatus s1 = status.updateAndGet(MqttRobotStatus::setRobotNotConfigured);
            states.onNext(s1);
            Completable.timer(retryInterval, TimeUnit.MILLISECONDS, Schedulers.computation())
                    .subscribe(this::configure);
        }
    }

    /**
     * Handles the hello robot message
     *
     * @param arg the argument
     */
    private void onRobotHiMessage(String arg) {
        MqttRobotStatus st = status.updateAndGet(MqttRobotStatus::setRobotNotConfigured);
        states.onNext(st);
        configure();
    }

    /**
     * Returns the robot device
     */
    public RemoteDevice qrCameraDevice() {
        return qrDetectorDevice;
    }

    @Override
    public Flowable<CameraEvent> readCamera() {
        return cameraEvents;
    }

    @Override
    public Flowable<WheellyContactsMessage> readContacts() {
        return contactMessages;
    }

    @Override
    public Flowable<Throwable> readErrors() {
        return errors;
    }

    @Override
    public Flowable<WheellyLidarMessage> readLidar() {
        return lidarMessages;
    }

    @Override
    public Flowable<WheellyMotionMessage> readMotion() {
        return motionMessages;
    }

    @Override
    public Flowable<RobotStatusApi> readRobotStatus() {
        return states;
    }

    @Override
    public Flowable<WheellySupplyMessage> readSupply() {
        return supplyMessages;
    }

    @Override
    public void reconnect() {
    }

    /**
     * Returns the robot device
     */
    public RemoteDevice robotDevice() {
        return robotDevice;
    }

    @Override
    public RobotSpec robotSpec() {
        return robotSpec;
    }

    @Override
    public Single<Boolean> rotate(int dir) {
        if (!status.get().connected()) {
            return Single.just(false);
        }
        return executeRobotCommand("ro", String.valueOf(dir), DEFAULT_COMMAND_TIMEOUT)
                .isEmpty()
                .map(s -> !s);
    }

    @Override
    public Single<Boolean> scan(int direction) {
        if (!status.get().connected()) {
            return Single.just(false);
        }
        return executeRobotCommand("sc", String.valueOf(direction), DEFAULT_COMMAND_TIMEOUT)
                .isEmpty()
                .map(s -> !s);
    }

    @Override
    public double simulationSpeed() {
        return 1;
    }

    @Override
    public long simulationTime() {
        return System.currentTimeMillis() - status.get().startTime();
    }

    /**
     * Returns the camera event from the mqtt message
     *
     * @param mqttMessage the message
     */
    private CameraEvent toCameraMessage(MqttMessage mqttMessage) {
        double widthRatio = 2 * tan(robotSpec.cameraFOV().toRad() / 2);
        try {
            return CameraEvent.parse(simulationTime(), new String(mqttMessage.getPayload()), widthRatio);
        } catch (Throwable ex) {
            logger.atError().setCause(ex).log("Error parsing camera message");
            return null;
        }
    }

    /**
     * Return contact message from the mqtt message
     *
     * @param mqttMessage the message
     */
    private WheellyContactsMessage toContactMessage(MqttMessage mqttMessage) {
        try {
            return WheellyContactsMessage.parse(simulationTime(), new String(mqttMessage.getPayload()));
        } catch (Throwable ex) {
            logger.atError().setCause(ex).log("Error parsing contact message");
            return null;
        }
    }

    private WheellyLidarMessage toLidarMessage(MqttMessage mqttMessage) {
        try {
            return WheellyLidarMessage.parse(simulationTime(), new String(mqttMessage.getPayload()));
        } catch (Throwable ex) {
            logger.atError().setCause(ex).log("Error parsing lidar message");
            return null;
        }
    }

    /**
     * Returns the motion message from mqtt message
     *
     * @param mqttMessage the message
     */
    private WheellyMotionMessage toMotionMessage(MqttMessage mqttMessage) {
        try {
            return WheellyMotionMessage.parse(simulationTime(), new String(mqttMessage.getPayload()));
        } catch (Throwable ex) {
            logger.atError().setCause(ex).log("Error parsing motion message");
            return null;
        }
    }

    /**
     * Returns the supply message from mqtt message
     *
     * @param mqttMessage the message
     */
    private WheellySupplyMessage toSupplyMessage(MqttMessage mqttMessage) {
        try {
            return WheellySupplyMessage.parse(simulationTime(), new String(mqttMessage.getPayload()));
        } catch (Throwable ex) {
            logger.atError().setCause(ex).log("Error parsing supply message");
            return null;
        }
    }
}
