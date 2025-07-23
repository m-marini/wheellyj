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

package org.mmarini.wheelly.apis;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.jbox2d.collision.WorldManifold;
import org.jbox2d.collision.shapes.CircleShape;
import org.jbox2d.collision.shapes.PolygonShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.*;
import org.jbox2d.dynamics.contacts.Contact;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.io.File;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.RobotStatus.DISTANCE_PER_PULSE;
import static org.mmarini.wheelly.apis.RobotStatus.OBSTACLE_SIZE;
import static org.mmarini.wheelly.apis.Utils.clip;
import static org.mmarini.wheelly.apis.WheellyProxyMessage.DISTANCE_SCALE;

/**
 * Simulated robot
 */
public class SimRobot implements RobotApi {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/sim-robot-schema-2.0";
    public static final double GRID_SIZE = 0.2;
    public static final double WORLD_SIZE = 10;
    public static final double MAX_OBSTACLE_DISTANCE = 3;
    public static final double MAX_DISTANCE = 3;
    public static final double MAX_ANGULAR_PPS = 20;
    public static final double ROBOT_TRACK = 0.136;
    public static final double MAX_ANGULAR_VELOCITY = MAX_ANGULAR_PPS * DISTANCE_PER_PULSE / ROBOT_TRACK * 2; // RAD/s
    public static final double SAFE_DISTANCE = 0.2;
    public static final int CAMERA_HEIGHT = 240;
    public static final int CAMERA_WIDTH = 240;
    public static final String QR_CODE = "A";
    private static final double MIN_OBSTACLE_DISTANCE = 1;
    private static final Logger logger = LoggerFactory.getLogger(SimRobot.class);
    private static final Vec2 GRAVITY = new Vec2();
    public static final float ROBOT_RADIUS = 0.15f;
    private static final double ROBOT_MASS = 0.785;
    private static final double ROBOT_FRICTION = 1;
    private static final double ROBOT_RESTITUTION = 0;
    private static final float JBOX_SCALE = 100;
    private static final double ROBOT_DENSITY = ROBOT_MASS / (ROBOT_RADIUS * ROBOT_RADIUS * PI * JBOX_SCALE * JBOX_SCALE);
    private static final double RAD_10 = toRadians(10);
    private static final double RAD_30 = toRadians(30);
    private static final double MAX_ACC = 1 * JBOX_SCALE;
    private static final double MAX_FORCE = MAX_ACC * ROBOT_MASS;
    private static final double MAX_TORQUE = 0.7 * JBOX_SCALE * JBOX_SCALE;
    private static final int VELOCITY_ITER = 10;
    private static final int POSITION_ITER = 10;
    private static final double SAFE_DISTANCE_SQ = pow(SAFE_DISTANCE + OBSTACLE_SIZE, 2);
    private static final int DEFAULT_SENSOR_RECEPTIVE_ANGLE = 15;
    private static final long DEFAULT_STALEMATE_INTERVAL = 60000;
    private static final int DEFAULT_MAX_ANGULAR_SPEED = 5;
    private static final long DEFAULT_INTERVAL = 100;
    private static final long DEFAULT_MOTION_INTERVAL = 500;
    private static final long DEFAULT_PROXY_INTERVAL = 500;
    private static final long DEFAULT_CAMERA_INTERVAL = 500;
    public static final double NANOS_PER_MILLIS = 10e6;

    /**
     * Returns the simulated robot from JSON configuration
     *
     * @param root the json document
     * @param file the configuration file
     */
    public static SimRobot create(JsonNode root, File file) {
        Locator locator = Locator.root();
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        long mapSeed = locator.path("mapSeed").getNode(root).asLong(0);
        long robotSeed = locator.path("robotSeed").getNode(root).asLong(0);
        int numObstacles = locator.path("numObstacles").getNode(root).asInt();
        int numLabels = locator.path("numLabels").getNode(root).asInt();
        Complex sensorReceptiveAngle = Complex.fromDeg(locator.path("sensorReceptiveAngle").getNode(root).asInt(DEFAULT_SENSOR_RECEPTIVE_ANGLE));
        Random mapRandom = mapSeed > 0L ? new Random(mapSeed) : new Random();
        Random robotRandom = robotSeed > 0L ? new Random(robotSeed) : new Random();
        double errSigma = locator.path("errSigma").getNode(root).asDouble();
        double errSensor = locator.path("errSensor").getNode(root).asDouble();
        int maxAngularSpeed = locator.path("maxAngularSpeed").getNode(root).asInt(DEFAULT_MAX_ANGULAR_SPEED);
        long motionInterval = locator.path("motionInterval").getNode(root).asLong(DEFAULT_MOTION_INTERVAL);
        long proxyInterval = locator.path("proxyInterval").getNode(root).asLong(DEFAULT_PROXY_INTERVAL);
        long changeObstaclesPeriod = locator.path("changeObstaclesPeriod").getNode(root).asLong(0);
        long stalemateInterval = locator.path("stalemateInterval").getNode(root).asLong(DEFAULT_STALEMATE_INTERVAL);
        long interval = locator.path("interval").getNode(root).asLong(DEFAULT_INTERVAL);
        long cameraInterval = locator.path("interval").getNode(root).asLong(DEFAULT_CAMERA_INTERVAL);
        double maxRadarDistance = locator.path("maxRadarDistance").getNode(root).asDouble();
        double contactRadius = locator.path("contactRadius").getNode(root).asDouble();
        RobotSpec robotSpec = new RobotSpec(maxRadarDistance, sensorReceptiveAngle, contactRadius);
        return new SimRobot(robotSpec, robotRandom, mapRandom,
                interval, motionInterval, proxyInterval, cameraInterval, stalemateInterval, changeObstaclesPeriod,
                errSensor, errSigma, maxAngularSpeed, numObstacles, numLabels);
    }

    private final RobotSpec robotSpec;
    private final Random random;
    private final Random mapRandom;
    private final long interval;
    private final long motionInterval;
    private final long proxyInterval;
    private final long cameraInterval;
    private final long stalemateInterval;
    private final int numObstacles;
    private final int numLabels;
    private final long changeObstaclesPeriod;
    private final double errSensor;
    private final double errSigma;
    private final int maxAngularSpeed;
    private final PublishProcessor<CameraEvent> cameraEvents;
    private final PublishProcessor<WheellyMessage> messages;
    private final PublishProcessor<Throwable> errors;
    private final PublishProcessor<String> readLines;
    private final PublishProcessor<String> writeLines;
    private final BehaviorProcessor<ObstacleMap> obstacleChanged;
    private final BehaviorProcessor<RobotStatusApi> robotLineState;
    private final AtomicReference<SimRobotStatus> status;
    private final World world;
    private final Body robot;
    private final Fixture robotFixture;
    private Body obstacleBody;

    /**
     * Creates the simulated robot
     *
     * @param robotSpec             the robot specification
     * @param random                the robot random generator
     * @param mapRandom             the map random generator
     * @param interval              the simulation time interval (ms)
     * @param motionInterval        the motion message interval (ms)
     * @param proxyInterval         the proxy message interval (ms)
     * @param cameraInterval        the camera event interval (ms)
     * @param stalemateInterval     the stalemate interval (ms)
     * @param changeObstaclesPeriod the change obstacle period (ms)
     * @param errSensor             the relative error sensor
     * @param errSigma              the relative error on speed simulation
     * @param maxAngularSpeed       the maximum angular speed
     * @param numObstacles          the number of obstacles
     * @param numLabels             the number of labels
     */
    public SimRobot(RobotSpec robotSpec, Random random, Random mapRandom,
                    long interval, long motionInterval, long proxyInterval, long cameraInterval, long stalemateInterval,
                    long changeObstaclesPeriod,
                    double errSensor, double errSigma, int maxAngularSpeed,
                    int numObstacles, int numLabels) {
        this.robotSpec = requireNonNull(robotSpec);
        this.random = requireNonNull(random);
        this.mapRandom = requireNonNull(mapRandom);
        this.interval = interval;
        this.motionInterval = motionInterval;
        this.proxyInterval = proxyInterval;
        this.cameraInterval = cameraInterval;
        this.stalemateInterval = stalemateInterval;
        this.numObstacles = numObstacles;
        this.numLabels = numLabels;
        this.changeObstaclesPeriod = changeObstaclesPeriod;
        this.errSensor = errSensor;
        this.errSigma = errSigma;
        this.maxAngularSpeed = maxAngularSpeed;
        this.cameraEvents = PublishProcessor.create();
        this.messages = PublishProcessor.create();
        this.errors = PublishProcessor.create();
        this.readLines = PublishProcessor.create();
        this.writeLines = PublishProcessor.create();
        this.obstacleChanged = BehaviorProcessor.create();
        // Creates the jbox2 physic world
        this.world = new World(GRAVITY);
        // Creates the jbox2 physic robot body
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyType.DYNAMIC;
        bodyDef.angle = (float) (PI / 2);
        this.robot = world.createBody(bodyDef);
        // Creates the robot fixture
        CircleShape circleShape = new CircleShape();
        circleShape.setRadius(ROBOT_RADIUS * JBOX_SCALE);
        FixtureDef fixDef = new FixtureDef();
        fixDef.shape = circleShape;
        fixDef.friction = (float) ROBOT_FRICTION;
        fixDef.density = (float) ROBOT_DENSITY;
        fixDef.restitution = (float) ROBOT_RESTITUTION;
        this.robotFixture = robot.createFixture(fixDef);
        this.status = new AtomicReference<>(new SimRobotStatus(0, false, false, false,
                Complex.DEG0, 0, true, true,
                direction(), 0, 0, 0,
                null, 0, 0, 0, 0, null, 0, 0));
        this.robotLineState = BehaviorProcessor.createDefault(status.get());
        createObstacleMap();
    }

    /**
     * Checks for proximity and contact sensors.
     * Sends the robot status in case of contact changes
     *
     * @param initial the initial status
     */
    private void checkForSensor(SimRobotStatus initial) {
        Point2D position = location();
        double x = position.getX();
        double y = position.getY();
        SimRobotStatus status = this.status.get();
        Complex sensorRad = direction().add(status.sensorDirection());
        double echoDistance;

        // Finds the nearest obstacle in proxy sensor range
        ObstacleMap.ObstacleCell nearestCell = status.obstacleMap().nearest(x, y, sensorRad, robotSpec.receptiveAngle());
        if (nearestCell != null) {
            // Computes the distance of obstacles
            Point2D obs = nearestCell.location();
            double dist = obs.distance(position) - status.obstacleMap().gridSize() / 2
                    + random.nextGaussian() * errSensor;
            echoDistance = dist > 0 && dist < MAX_DISTANCE ? dist : 0;
        } else {
            echoDistance = 0;
        }
        status = this.status.updateAndGet(s -> s.echoDistance(echoDistance).nearestCell(nearestCell));

        boolean echoAlarm = echoDistance > 0 && echoDistance <= SAFE_DISTANCE;
        boolean prevEchoAlarm = initial.echoDistance() > 0 && initial.echoDistance() <= SAFE_DISTANCE;
        if (echoAlarm != prevEchoAlarm
                || initial.rearSensor() != status.rearSensor()
                || initial.frontSensor() != status.frontSensor()) {
            // Contacts changed -> send status
            sendContacts();
            sendMotion();
            sendProxy();
        }
    }

    /**
     * Halt the robot if it is moving in forbidden direction
     */
    private void checkForSpeed() {
        SimRobotStatus s = status.get();
        if ((s.speed() > 0 || ((s.leftPps() + s.rightPps()) > 0)) && !s.canMoveForward()
                || (s.speed() < 0 || ((s.leftPps() + s.rightPps()) < 0)) && !s.canMoveBackward()) {
            halt();
        }
    }

    @Override
    public void close() {
        if (!status.getAndUpdate(status -> status.closed(true)).closed()) {
            robotLineState.onNext(status.get());
            robotLineState.onComplete();
            readLines.onComplete();
            writeLines.onComplete();
            cameraEvents.onComplete();
            messages.onComplete();
            errors.onComplete();
        }
    }

    @Override
    public void connect() {
        if (!status.getAndUpdate(status ->
                status.connected(true)
        ).connected()) {
            SimRobotStatus st = status.updateAndGet(s -> s.startSimulationTime(System.nanoTime()));
            robotLineState.onNext(st);
            updateMotion();
            updateProxy();
            updateCamera();
            tick();
        }
    }

    /**
     * Returns the contact direction relative to the robot (RAD)
     *
     * @param contact the contact
     */
    private Complex contactRelativeDirection(Contact contact) {
        WorldManifold worldManifold = new WorldManifold();
        contact.getWorldManifold(worldManifold);
        int n = contact.getManifold().pointCount;
        float x = 0;
        float y = 0;
        for (int i = 0; i < n; i++) {
            x += worldManifold.points[i].x;
            y += worldManifold.points[i].y;
        }
        Point2D collisionLocation = new Point2D.Double(
                x / JBOX_SCALE / n,
                y / JBOX_SCALE / n);
        Complex collisionDirection = Complex.direction(location(), collisionLocation);
        // Compute the collision direction relative to the robot direction
        return collisionDirection.sub(direction());
    }

    @Override
    public boolean halt() {
        SimRobotStatus s0 = status.getAndUpdate(s -> s.speed(0)
                .sensorDirection(Complex.DEG0)
                .leftPps(0)
                .rightPps(0)
                .direction(direction()));
        logger.atDebug().log("{}: Halt", s0.simulationTime());
        return true;
    }

    /**
     * Creates the obstacle bodies
     */
    private void createObstacleBody(ObstacleMap obstacleMap) {
        Body obstacleBody = this.obstacleBody;
        if (obstacleBody != null) {
            world.destroyBody(obstacleBody);
            status.updateAndGet(s -> s.frontSensor(true).rearSensor(true));
        }

        BodyDef obsDef = new BodyDef();
        obsDef.type = BodyType.STATIC;
        obstacleBody = world.createBody(obsDef);

        for (ObstacleMap.ObstacleCell cell : obstacleMap.cells()) {
            PolygonShape obsShape = new PolygonShape();
            Vec2 center = new Vec2((float) cell.location().getX() * JBOX_SCALE, (float) cell.location().getY() * JBOX_SCALE);
            obsShape.setAsBox(RobotStatus.OBSTACLE_SIZE / 2 * JBOX_SCALE, RobotStatus.OBSTACLE_SIZE / 2 * JBOX_SCALE, center, 0);

            FixtureDef obsFixDef = new FixtureDef();
            obsFixDef.shape = obsShape;
            obstacleBody.createFixture(obsFixDef);
        }
        this.obstacleBody = obstacleBody;
    }

    /**
     * Returns the obstacle map
     */
    private void createObstacleMap() {
        ObstacleMap map = MapBuilder.create(GRID_SIZE)
                .rect(false, -WORLD_SIZE / 2,
                        -WORLD_SIZE / 2, WORLD_SIZE / 2, WORLD_SIZE / 2)
                .rand(numObstacles, numLabels, new Point2D.Double(), MAX_OBSTACLE_DISTANCE,
                        location(), MIN_OBSTACLE_DISTANCE, mapRandom)
                .build();
        obstacleMap(map);
    }

    /**
     * Returns the robot direction
     */
    public Complex direction() {
        return Complex.fromRad(PI / 2 - robot.getAngle());
    }

    /**
     * Returns the echo distance
     */
    public double echoDistance() {
        return status.get().echoDistance();
    }

    /**
     * Return a random safe location for the robot
     *
     * @param map the obstacle map
     */
    private Point2D generateLocation(ObstacleMap map) {
        Point2D loc1;
        for (; ; ) {
            // Generates a random location in the map
            loc1 = new Point2D.Double(
                    random.nextDouble() * WORLD_SIZE - WORLD_SIZE / 2,
                    random.nextDouble() * WORLD_SIZE - WORLD_SIZE / 2
            );
            Point2D finalLoc = loc1;
            // Check for safe distance from any obstacles
            if (map.cells().stream()
                    .noneMatch(cell ->
                            finalLoc.distanceSq(cell.location()) <= SAFE_DISTANCE_SQ
                    )) {
                break;
            }
        }
        return loc1;
    }

    /**
     * Sends the motion message
     */
    private void sendMotion() {
        SimRobotStatus s = status.get();
        Point2D pos = this.location();
        double xPulses = pos.getX() / DISTANCE_PER_PULSE;
        double yPulses = pos.getY() / DISTANCE_PER_PULSE;
        Complex robotDir = direction();
        logger.atDebug().log("{}: send R{}", s.simulationTime(), robotDir.toIntDeg());
        WheellyMotionMessage msg = new WheellyMotionMessage(
                System.currentTimeMillis(), s.simulationTime(), s.simulationTime(),
                xPulses, yPulses, robotDir.toIntDeg(),
                s.leftPps(), s.rightPps(),
                0, s.speed() == 0,
                (int) round(s.leftPps()), (int) round(s.rightPps()),
                0, 0);
        messages.onNext(msg);
        status.updateAndGet(s1 -> s1.motionTimeout(s1.simulationTime() + motionInterval));
    }

    /**
     * Handles contact list
     */
    private void handleContacts() {
        Contact contact = world.getContactList();
        boolean frontSensor = true;
        boolean rearSensor = true;
        while (contact != null) {
            if (contact.isTouching()) {
                Fixture fixture = contact.getFixtureA().equals(robotFixture)
                        ? contact.getFixtureB()
                        : contact.getFixtureB().equals(robotFixture)
                        ? contact.getFixtureA()
                        : null;
                if (fixture != null) {
                    Complex collisionDir = contactRelativeDirection(contact);
                    if (collisionDir.y() >= 0) {
                        // front contact
                        frontSensor = false;
                    }
                    if (collisionDir.y() <= 0) {
                        // rear contact
                        rearSensor = false;
                    }
                }
            }
            contact = contact.getNext();
        }
        boolean finalFrontSensor = frontSensor;
        boolean finalRearSensor = rearSensor;
        status.updateAndGet(s1 -> s1.frontSensor(finalFrontSensor).rearSensor(finalRearSensor));
    }

    /**
     * Handles the stalemate status
     * Checks for stalemate and relocate the roboto in case of stalemate timeout
     */
    private void handleStalemate() {
        status.updateAndGet(s -> {
            if (s.frontSensor() || s.rearSensor()) {
                // no stalemate
                s = s.stalemate(false);
            } else if (!s.stalemate()) {
                // First stalemate, start the timer
                s = s.stalemateTimeout(s.simulationTime() + stalemateInterval)
                        .stalemate(true);
            } else if (s.simulationTime() >= s.stalemateTimeout()) {
                // stalemate timeout
                safeRelocateRandom();
            }
            return s;
        });
    }

    @Override
    public boolean isHalt() {
        SimRobotStatus st = status.get();
        return st.speed() == 0 && abs(st.leftPps()) < 1 && abs(st.rightPps()) < 1;
    }

    /**
     * Returns the robot location (m)
     */
    public Point2D location() {
        Vec2 pos = robot.getPosition();
        return new Point2D.Double(pos.x / JBOX_SCALE, pos.y / JBOX_SCALE);
    }

    @Override
    public boolean move(int dir, int speed) {
        status.updateAndGet(s -> s.direction(Complex.fromDeg(dir))
                .speed(clip(speed, -MAX_PPS, MAX_PPS)));
        checkForSpeed();
        return true;
    }

    /**
     * Sets the obstacle map
     *
     * @param map the map
     */
    public SimRobot obstacleMap(ObstacleMap map) {
        status.updateAndGet(s -> s.setObstacleMap(map));
        createObstacleBody(map);
        obstacleChanged.onNext(map);
        return this;
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

    /**
     * Returns the obstacle map flow
     */
    public Flowable<ObstacleMap> readObstacleMap() {
        return obstacleChanged;
    }

    @Override
    public Flowable<String> readReadLine() {
        return readLines;
    }

    @Override
    public Flowable<RobotStatusApi> readRobotStatus() {
        return robotLineState;
    }

    @Override
    public void reconnect() {
    }

    @Override
    public Flowable<String> readWriteLine() {
        return writeLines;
    }

    /**
     * Sets the robot direction
     *
     * @param robotDirection the robot direction
     */
    public SimRobot robotDir(Complex robotDirection) {
        status.updateAndGet(s -> s.direction(robotDirection));
        robot.setTransform(robot.getPosition(),
                (float) Complex.DEG90.sub(robotDirection).toRad());
        return this;
    }

    @Override
    public RobotSpec robotSpec() {
        return robotSpec;
    }

    /**
     * Sets the robot location
     *
     * @param x the x coordinate
     * @param y the y coordinate
     */
    public void robotPos(double x, double y) {
        Vec2 pos = new Vec2();
        pos.x = (float) (x * JBOX_SCALE);
        pos.y = (float) (y * JBOX_SCALE);
        robot.setTransform(pos, robot.getAngle());
    }

    @Override
    public boolean scan(int direction) {
        Complex dir = Complex.fromDeg(clip(direction, -90, 90));
        status.updateAndGet(s ->
                s.sensorDirection(dir)
        );
        return true;
    }

    /**
     * Sends the camera message
     */
    private void sendCamera() {
        SimRobotStatus s = status.get();
        Point2D[] points = new Point2D[0];
        CameraEvent event = s.nearestCell() != null && s.nearestCell().labeled()
                ? new CameraEvent(s.simulationTime(), QR_CODE, CAMERA_WIDTH, CAMERA_HEIGHT, points)
                : CameraEvent.unknown(s.simulationTime());
        cameraEvents.onNext(event);
        status.updateAndGet(s1 -> s1.cameraTimeout(s.simulationTime() + cameraInterval));
    }

    /**
     * Sends the contact message
     */
    private void sendContacts() {
        SimRobotStatus s = status.get();
        WheellyContactsMessage msg = new WheellyContactsMessage(
                System.currentTimeMillis(), s.simulationTime(), s.simulationTime(),
                s.frontSensor(), s.rearSensor(),
                s.canMoveForward(),
                s.canMoveBackward()
        );
        logger.atDebug().log("On contacts {}", msg);
        messages.onNext(msg);
    }

    /**
     * Simulate the time interval
     */
    private void simulate() {
        SimRobotStatus initialStatus = status.get();
        status.updateAndGet(s ->
                s.simulationTime(s.simulationTime() + interval)
                        .lastTick(System.nanoTime()));

        // Change obstacles
        if (changeObstaclesPeriod > 0) {
            double lambda = 1D / changeObstaclesPeriod;
            double p = 1 - exp(-lambda * interval);
            if (mapRandom.nextDouble() < p) {
                createObstacleMap();
            }
        }
        // Simulate robot motion
        simulatePhysics();
        // Handle contacts
        handleContacts();
        // Check for sensor
        checkForSensor(initialStatus);
        // Check for movement constraints
        checkForSpeed();
        // Handles stalemate
        handleStalemate();
        // Update robot status
        updateMotion();
        updateProxy();
        updateCamera();

        // Reschedules the simulation
        tick();
    }

    /**
     * Sends the proxy message
     */
    private void sendProxy() {
        SimRobotStatus s = status.get();
        Point2D pos = this.location();
        double xPulses = pos.getX() / DISTANCE_PER_PULSE;
        double yPulses = pos.getY() / DISTANCE_PER_PULSE;
        Complex echoYaw = direction();
        long echoDelay = round(s.echoDistance() / DISTANCE_SCALE);
        WheellyProxyMessage msg = new WheellyProxyMessage(
                System.currentTimeMillis(), s.simulationTime(), s.simulationTime(),
                s.sensorDirection().toIntDeg(), echoDelay, xPulses, yPulses, echoYaw.toIntDeg());
        logger.atDebug().log("proxy R{} D{}", msg.sensorDirection().toDeg(), msg.echoDistance());
        messages.onNext(msg);
        status.updateAndGet(s1 -> s1.proxyTimeout(s1.simulationTime() + proxyInterval));
    }

    /**
     * Returns the sensor direction
     */
    public Complex sensorDirection() {
        return status.get().sensorDirection();
    }

    /**
     * Randomly relocates the robot
     */
    public void safeRelocateRandom() {
        SimRobotStatus s = status.get();
        ObstacleMap map = s.obstacleMap();
        Point2D loc = map != null
                ? generateLocation(map)
                : new Point2D.Double();
        // Relocate robot
        robotPos(loc.getX(), loc.getY());
    }

    /**
     * Sets the sensor direction
     *
     * @param sensorDirection the sensor direction
     */
    public SimRobot sensorDirection(Complex sensorDirection) {
        status.updateAndGet(s -> s.sensorDirection(sensorDirection));
        return this;
    }

    /**
     * Simulates robot physics for interval time
     */
    private void simulatePhysics() {
        double dt = interval * 1e-3;
        SimRobotStatus status = this.status.get();
        // Direction difference
        double dAngle = direction().sub(status.direction()).toRad();

        // Relative angular speed to fix the direction
        double angularVelocityPps = clip(
                Utils.linear(dAngle, -RAD_10, RAD_10, -maxAngularSpeed, maxAngularSpeed),
                -maxAngularSpeed, maxAngularSpeed);

        // Relative linear speed to fix the speed
        double linearVelocityPps = status.speed() *
                clip(
                        Utils.linear(abs(dAngle), 0, RAD_30, 1, 0),
                        0, 1);

        // Relative left-right motor speeds
        double leftPps = clip((linearVelocityPps - angularVelocityPps), -MAX_PPS, MAX_PPS);
        double rightPps = clip((linearVelocityPps + angularVelocityPps), -MAX_PPS, MAX_PPS);

        // Check for block
        if ((leftPps < 0 && !status.canMoveBackward())
                || (leftPps > 0 && !status.canMoveForward())) {
            leftPps = 0;
        }
        if ((rightPps < 0 && !status.canMoveBackward())
                || (rightPps > 0 && !status.canMoveForward())) {
            rightPps = 0;
        }

        double finalLeftPps = leftPps;
        double finalRightPps = rightPps;
        this.status.updateAndGet(s -> s.leftPps(finalLeftPps).rightPps(finalRightPps));

        // Real left-right motor speeds
        double left = leftPps * DISTANCE_PER_PULSE;
        double right = rightPps * DISTANCE_PER_PULSE;

        // Real forward velocity
        double forwardVelocity = (left + right) / 2;

        // target real speed
        Vec2 targetVelocity = robot.getWorldVector(Utils.vec2(forwardVelocity * JBOX_SCALE, 0));
        // Difference of speed
        Vec2 dv = targetVelocity.sub(robot.getLinearVelocity());
        // Impulse to fix the speed
        Vec2 dq = dv.mul(robot.getMass());
        // Force to fix the speed
        Vec2 force = dq.mul((float) (1 / dt));
        // Robot relative force
        Vec2 localForce = robot.getLocalVector(force);
        // add a random factor to force
        localForce = localForce.mul((float) (1 + random.nextGaussian() * errSensor));

        // Clip the local force to physic constraints
        localForce.x = (float) clip(localForce.x, -MAX_FORCE, MAX_FORCE);
        force = robot.getWorldVector(localForce);

        // Angle rotation due to differential motor speeds
        double angularVelocity1 = (right - left) / ROBOT_TRACK;
        // Limits rotation to max allowed rotation
        double angularVelocity = clip(angularVelocity1, -MAX_ANGULAR_VELOCITY, MAX_ANGULAR_VELOCITY);
        // Angular impulse to fix the direction
        double robotAngularVelocity = robot.getAngularVelocity();
        double angularTorque = (angularVelocity - robotAngularVelocity) * robot.getInertia() / dt;
        // Add a random factor to angular impulse
        angularTorque *= (1 + random.nextGaussian() * errSigma);
        // Clip the angular torque
        angularTorque = clip(angularTorque, -MAX_TORQUE, MAX_TORQUE);
        world.clearForces();
        robot.applyForceToCenter(force);
        robot.applyTorque((float) angularTorque);
        world.step((float) dt, VELOCITY_ITER, POSITION_ITER);
    }

    @Override
    public double simulationSpeed() {
        SimRobotStatus s = status.get();
        long dt = s.lastTick() - s.startSimulationTime();
        return dt > 0 ? s.simulationTime() * NANOS_PER_MILLIS / dt : 1;
    }

    @Override
    public long simulationTime() {
        return status.get().simulationTime();
    }

    /**
     * Schedules the computation of status on the time interval
     */
    private void tick() {
        SimRobotStatus s = status.get();
        if (s.connected() && !s.closed()) {
            Completable.fromAction(this::simulate)
                    .subscribeOn(Schedulers.computation())
                    .subscribe();
        }
    }

    /**
     * Sends the proxy message if the interval has elapsed
     */
    private void updateCamera() {
        SimRobotStatus s = status.get();
        if (s.simulationTime() >= s.cameraTimeout()) {
            sendCamera();
        }
    }

    /**
     * Sends the motion message if the interval has elapsed
     */
    private void updateMotion() {
        SimRobotStatus s = status.get();
        if (s.simulationTime() >= s.motionTimeout()) {
            sendMotion();
        }
    }

    /**
     * Sends the proxy message if the interval has elapsed
     */
    private void updateProxy() {
        SimRobotStatus s = status.get();
        if (s.simulationTime() >= s.proxyTimeout()) {
            sendProxy();
        }
    }
}
