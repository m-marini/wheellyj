/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.schedulers.Timed;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.mmarini.NotImplementedException;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.wheelly.rx.RXFunc;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static java.lang.Math.tan;
import static java.util.Objects.requireNonNull;


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
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/mqtt-robot-schema-0.1";
    public static final String HALLO_MESSAGE = "hi";
    private static final Logger logger = LoggerFactory.getLogger(MqttRobot.class);

    /**
     * Returns the robot from configuration
     *
     * @param root the configuration document
     * @param file the configuration file
     */
    public static MqttRobot create(JsonNode root, File file) {
        Locator locator = Locator.root();
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        long configureTimeout = locator.path("configureTimeout").getNode(root).asLong();
        long watchDogInterval = locator.path("watchDogInterval").getNode(root).asLong();
        long watchDogTimeout = locator.path("watchDogTimeout").getNode(root).asLong();

        double maxRadarDistance = locator.path("maxRadarDistance").getNode(root).asDouble();
        double contactRadius = locator.path("contactRadius").getNode(root).asDouble();
        int receptiveAngle = locator.path("sensorReceptiveAngle").getNode(root).asInt();
        int cameraViewAngle = locator.path("cameraViewAngle").getNode(root).asInt();
        RobotSpec robotSpec = new RobotSpec(maxRadarDistance, Complex.fromDeg(receptiveAngle), contactRadius, Complex.fromDeg(cameraViewAngle));

        Locator configCommandsLoc = locator.path("configCommands");
        String[] configCommands = !configCommandsLoc.getNode(root).isMissingNode()
                ? configCommandsLoc.elements(root).map(l -> l.getNode(root).asText()).toArray(String[]::new)
                : new String[0];

        throw new NotImplementedException();
    }

    /**
     * Return MQTT robot interface
     *
     * @param brokerUrl        the broker url
     * @param clientId         the client id
     * @param user             the mqtt client username
     * @param password         mqtt client password
     * @param sensorTopic      the sensor topic
     * @param commandTopic     the command topic
     * @param configureTimeout the configuration timeout (ms)
     * @param retryInterval    the retry configuration interval (ms)
     * @param robotSpec        the robot specification
     * @param configCommands   the configuration commands
     * @throws MqttException in case of error
     */
    public static MqttRobot create(String brokerUrl, String clientId, String user, String password, String sensorTopic, String commandTopic, long configureTimeout, long retryInterval, RobotSpec robotSpec, String... configCommands) throws MqttException {
        RxMqttClient client = RxMqttClient.create(brokerUrl, clientId, user, password);
        return new MqttRobot(client, sensorTopic, commandTopic, configureTimeout, retryInterval, robotSpec, configCommands);
    }

    private final RxMqttClient client;
    private final String sensorTopic;
    private final String commandTopic;
    private final long configureTimeout;
    private final long retryInterval;
    private final RobotSpec robotSpec;
    private final String[] configCommands;
    private final AtomicReference<MqttRobotStatus> status;
    private final BehaviorProcessor<RobotStatusApi> states;
    private final PublishProcessor<Throwable> errors;
    private final PublishProcessor<WheellyMessage> messages;
    private final PublishProcessor<CameraEvent> cameraEvents;
    private final PublishProcessor<String> readLines;
    private final PublishProcessor<String> writeLines;
    private final PublishProcessor<Timed<String>> sensorMessages;

    /**
     * Creates the mqtt robot
     *
     * @param client       the mqtt client
     * @param sensorTopic      the sensor topic
     * @param commandTopic     the command topic
     * @param configureTimeout the configuration timeout (ms)
     * @param retryInterval    the retry configuration interval (ms)
     * @param robotSpec        the robot specification
     * @param configCommands   the configuration commands
     */
    public MqttRobot(RxMqttClient client, String sensorTopic, String commandTopic, long configureTimeout, long retryInterval, RobotSpec robotSpec, String[] configCommands) {
        this.client = requireNonNull(client);
        this.sensorTopic = requireNonNull(sensorTopic);
        this.commandTopic = requireNonNull(commandTopic);
        this.configureTimeout = configureTimeout;
        this.retryInterval = retryInterval;
        this.robotSpec = robotSpec;
        this.configCommands = configCommands;
        status = new AtomicReference<>(new MqttRobotStatus(false, false, false, false, false,
                false, false, 0, 0, ClockConverter.identity(), null));
        this.states = BehaviorProcessor.createDefault(status.get());
        this.errors = PublishProcessor.create();
        this.readLines = PublishProcessor.create();
        this.writeLines = PublishProcessor.create();
        this.messages = PublishProcessor.create();
        this.cameraEvents = PublishProcessor.create();
        this.sensorMessages = PublishProcessor.create();
        client.readMessages().subscribe(this::onMessage);
    }

    @Override
    public void close() {
        logger.atInfo().log("Closing ...");
        MqttRobotStatus s1 = status.updateAndGet(MqttRobotStatus::setClosed);
        states.onNext(s1);
        halt();
        sensorMessages.onComplete();
        errors.onComplete();
        readLines.onComplete();
        writeLines.onComplete();
        messages.onComplete();
        cameraEvents.onComplete();
        states.onComplete();
    }

    /**
     * Sends the configuration command and wait for confirmation for a maximum of configured timeout
     *
     * @param command the configuration command
     */
    private Single<Boolean> configure(String command) {
        String reply = "// " + command;
        return writeCommand(command)
                .flatMap(success -> success
                        ? RXFunc.findFirst(sensorMessages,
                                l -> l.value().equals(reply),
                                configureTimeout)
                        .doOnSuccess(replied -> logger.atDebug().log("Reply to {}={}", command, replied.value()))
                        .map(x -> true)
                        .defaultIfEmpty(false)
                        .doOnSuccess(replied -> logger.atDebug().log("Configure={} command={}", replied, command))
                        : Single.just(false));
    }

    /**
     * Configures robot
     */
    private void configure() {
        MqttRobotStatus st = status.getAndUpdate(MqttRobotStatus::setConfiguring);
        if (!st.configuring() && !st.closed() && st.connected()) {
            // if not configuring
            states.onNext(st);
            // Send configure commands
            List<Single<Boolean>> list = Stream.of(configCommands)
                    .map(this::configure)
                    .toList();
            // Send all command the result is true if success
            Single<Boolean> allConfigCmd = Single.concat(list)
                    .filter(configured -> !configured)
                    .first(true);
            // Wait for synchronisation
            sync()
                    .flatMap(sync ->
                            sync
                                    ? allConfigCmd
                                    : Single.just(false))
                    .doOnSuccess(replied -> logger.atDebug().log("Configuration={}", replied))
                    .subscribe(this::onConfiguration);
        }
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
                    onMqttError(e);
                }
            }
        }
    }

    @Override
    public boolean halt() {
        return writeCommand("ha").blockingGet();
    }

    @Override
    public boolean isHalt() {
        return status.get().halted();
    }

    @Override
    public boolean move(int dir, int speed) {
        return writeCommand("mv " + dir + " " + speed).blockingGet();
    }

    private void onConfiguration(boolean success) {
        if (success) {
            logger.atDebug().log("Robot configured");
            MqttRobotStatus s1 = status.updateAndGet(MqttRobotStatus::setConfigured);
            states.onNext(s1);
        } else {
            logger.atDebug().log("Configuration failed");
            MqttRobotStatus s1 = status.updateAndGet(MqttRobotStatus::setConnected);
            states.onNext(s1);
            Completable.timer(retryInterval, TimeUnit.MILLISECONDS, Schedulers.computation())
                    .subscribe(this::configure);
        }
    }

    /**
     * Handles the broker client connection event
     *
     * @param token the mqtt token
     */
    private void onConnection(IMqttToken token) {
        logger.atDebug().log("Client mqtt connected.");
        MqttRobotStatus st = this.status.updateAndGet(s ->
                token.isComplete() ? s.setConnected().lastActivity(System.currentTimeMillis())
                        : s.setUnconnected());

        states.onNext(st);
        try {
            client.subscribe(sensorTopic, 0).subscribe();
        } catch (MqttException e) {
            errors.onNext(e);
        }
        configure();
    }

    private void onConnectionFailure(Throwable throwable) {
        logger.atError().setCause(throwable).log("Error connecting broker");
        errors.onNext(throwable);
        Completable.timer(retryInterval, TimeUnit.MILLISECONDS, Schedulers.computation())
                .subscribe(this::reconnect);
    }

    private void onMessage(Tuple2<String, MqttMessage> mqttMessage) {
        String text = new String(mqttMessage._2.getPayload());
        logger.atDebug().log("Message received {}", text);
        Timed<String> line = new Timed<>(text, System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        sensorMessages.onNext(line);
        readLines.onNext(text);
        MqttRobotStatus s = status.get();
        Optional<WheellyMessage> msgOpt = WheellyMessage.fromLine(line, s.startTime());
        msgOpt.ifPresent(msg -> {
            if (msg instanceof WheellyMotionMessage motionMsg) {
                MqttRobotStatus st = status.updateAndGet(s1 -> s1.halted(motionMsg.halt()));
                states.onNext(st);
            }
            messages.onNext(msg);
        });
        double widthRatio = 2 * tan(robotSpec.cameraViewAngle().toRad() / 2);
        CameraEvent msg = CameraEvent.create(line, widthRatio, status.get().startTime());
        if (msg != null) {
            cameraEvents.onNext(msg);
        }
        if (!s.configured() || text.startsWith(HALLO_MESSAGE)) {
            configure();
        }
    }

    private void onMqttError(MqttException e) {
        logger.atError().setCause(e).log("MQTT error");
        errors.onNext(e);
    }

    @Override
    public Flowable<CameraEvent> readCamera() {
        return cameraEvents;
    }

    @Override
    public Flowable<Throwable> readErrors() {
        return errors;
    }

    @Override
    public Flowable<WheellyMessage> readMessages() {
        return messages;
    }

    @Override
    public Flowable<String> readReadLine() {
        return readLines;
    }

    @Override
    public Flowable<RobotStatusApi> readRobotStatus() {
        return states;
    }

    @Override
    public Flowable<String> readWriteLine() {
        return writeLines;
    }

    @Override
    public void reconnect() {
        if (!status.get().closed()) {
            MqttRobotStatus s2 = status.updateAndGet(MqttRobotStatus::setConnecting);
            states.onNext(s2);
            try {
                logger.atDebug().log("Reconnecting to {} client {} ...", client.getServerURI(), client.getClientId());
                client.reconnect();
            } catch (MqttException e) {
                onMqttError(e);
            }
        }
    }

    @Override
    public RobotSpec robotSpec() {
        return robotSpec;
    }

    @Override
    public boolean scan(int direction) {
        return writeCommand("sc " + direction).blockingGet();
    }

    @Override
    public double simulationSpeed() {
        return 0;
    }

    @Override
    public long simulationTime() {
        return 0;
    }

    private Single<Boolean> sync() {
        long t0 = simulationTime();
        String command = "ck " + t0;
        String prefix = command + " ";
        return writeCommand(command).
                flatMap(success -> success
                        ?
                        RXFunc.findFirst(sensorMessages,
                                        l -> l.value().startsWith(prefix),
                                        configureTimeout)
                                .map(line -> {
                                    ClockSyncEvent clockEvent = ClockSyncEvent.from(line.value(), line.time());
                                    if (t0 == clockEvent.originateTimestamp()) {
                                        MqttRobotStatus st = status.updateAndGet(s -> s.clockConverter(clockEvent.converter()));
                                        states.onNext(st);
                                    }
                                    return true;
                                })
                                .defaultIfEmpty(false)
                        : Single.just(false));
    }

    private Single<Boolean> writeCommand(String command) {
        if (client.isConnected()) {
            return Single.just(true)
                    .flatMap(ignored -> {
                        logger.atDebug().log("Sending {}", command);
                        try {
                            return client.publish(commandTopic, new MqttMessage(command.getBytes()))
                                    .map(token -> true)
                                    .doOnSuccess(t -> writeLines.onNext(command));
                        } catch (MqttException e) {
                            logger.atError().setCause(e).log("Error sending message");
                            return Single.just(false);
                        }
                    });
        } else {
            return Single.just(false);
        }
    }
}
