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

package org.mmarini.wheelly.apis;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.jbox2d.collision.WorldManifold;
import org.jbox2d.collision.shapes.CircleShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.*;
import org.jbox2d.dynamics.contacts.Contact;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.Obstacle.DEFAULT_OBSTACLE_RADIUS;
import static org.mmarini.wheelly.apis.RobotSpec.*;
import static org.mmarini.wheelly.apis.RobotStatus.OBSTACLE_SIZE;
import static org.mmarini.wheelly.apis.Utils.clip;
import static org.mmarini.wheelly.apis.Utils.m2mm;

/**
 * Simulated robot
 */
public class SimRobot implements RobotApi {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/sim-robot-schema-3.1";
    public static final double GRID_SIZE = 0.2;
    public static final double WORLD_SIZE = 10;
    public static final double MAX_ANGULAR_PPS = 20;
    public static final double MAX_ANGULAR_VELOCITY = MAX_ANGULAR_PPS * DISTANCE_PER_PULSE / RobotSpec.ROBOT_TRACK * 2; // RAD/s
    public static final double SAFE_DISTANCE = 0.2;
    public static final int CAMERA_HEIGHT = 240;
    public static final int CAMERA_WIDTH = 240;
    public static final String QR_CODE = "A";
    public static final double NANOS_PER_MILLIS = 10e6;
    public static final long DEFAULT_STALEMATE_INTERVAL = 60000;
    public static final int DEFAULT_MAX_ANGULAR_SPEED = 5;
    public static final long DEFAULT_MOTION_INTERVAL = 500;
    public static final long DEFAULT_LIDAR_INTERVAL = 500;
    public static final long DEFAULT_CAMERA_INTERVAL = 500;
    public static final String LABEL = "A";
    private static final Logger logger = LoggerFactory.getLogger(SimRobot.class);
    private static final Vec2 GRAVITY = new Vec2();
    private static final double ROBOT_FRICTION = 1;
    private static final double ROBOT_RESTITUTION = 0;
    private static final float JBOX_SCALE = 100;
    private static final double ROBOT_DENSITY = RobotSpec.ROBOT_MASS / (RobotSpec.ROBOT_RADIUS * RobotSpec.ROBOT_RADIUS * PI * JBOX_SCALE * JBOX_SCALE);
    private static final double RAD_10 = toRadians(10);
    private static final double RAD_30 = toRadians(30);
    private static final double MAX_ACC = 1 * JBOX_SCALE;
    private static final double MAX_FORCE = MAX_ACC * RobotSpec.ROBOT_MASS;
    private static final double MAX_TORQUE = 0.7 * JBOX_SCALE * JBOX_SCALE;
    private static final int VELOCITY_ITER = 10;
    private static final int POSITION_ITER = 10;
    private static final double SAFE_DISTANCE_SQ = pow(SAFE_DISTANCE + OBSTACLE_SIZE, 2);

    /**
     * Returns the simulated robot from JSON configuration
     *
     * @param root the json document
     * @param file the configuration file
     */
    public static SimRobot create(JsonNode root, File file) {
        Locator locator = Locator.root();
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME, file.toString());
        long mapSeed = locator.path("mapSeed").getNode(root).asLong(0);
        long robotSeed = locator.path("robotSeed").getNode(root).asLong(0);
        int numObstacles = locator.path("numObstacles").getNode(root).asInt();
        int numLabels = locator.path("numLabels").getNode(root).asInt();
        Random mapRandom = mapSeed > 0L ? new Random(mapSeed) : new Random();
        Random robotRandom = robotSeed > 0L ? new Random(robotSeed) : new Random();
        double errSigma = locator.path("errSigma").getNode(root).asDouble();
        double errSensor = locator.path("errSensor").getNode(root).asDouble();
        int maxAngularSpeed = locator.path("maxAngularSpeed").getNode(root).asInt(DEFAULT_MAX_ANGULAR_SPEED);
        long motionInterval = locator.path("motionInterval").getNode(root).asLong(DEFAULT_MOTION_INTERVAL);
        long lidarInterval = locator.path("lidarInterval").getNode(root).asLong(DEFAULT_LIDAR_INTERVAL);
        long stalemateInterval = locator.path("stalemateInterval").getNode(root).asLong(DEFAULT_STALEMATE_INTERVAL);
        long cameraInterval = locator.path("cameraInterval").getNode(root).asLong(DEFAULT_CAMERA_INTERVAL);
        long interval = locator.path("interval").getNode(root).asLong();
        long tickInterval = locator.path("tickInterval").getNode(root).asLong();
        long mapPeriod = locator.path("mapPeriod").getNode(root).asLong();
        long randomPeriod = locator.path("randomPeriod").getNode(root).asLong();
        RobotSpec robotSpec = RobotSpec.fromJson(root, locator);
        List<List<Obstacle>> maps = locator.path("mapFiles").elements(root)
                .map(l -> {
                    String filename = l.getNode(root).asText();
                    try {
                        JsonNode mapYaml = org.mmarini.yaml.Utils.fromFile(filename);
                        return MapBuilder.create(mapYaml, Locator.root()).build();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
        return new SimRobot(robotSpec, robotRandom, mapRandom,
                tickInterval, interval, motionInterval, lidarInterval, cameraInterval, stalemateInterval,
                errSensor, errSigma, maxAngularSpeed,
                maps, numObstacles, numLabels, mapPeriod, randomPeriod);
    }

    private final RobotSpec robotSpec;
    private final Random random;
    private final Random mapRandom;
    private final long motionInterval;
    private final long lidarInterval;
    private final long cameraInterval;
    private final long stalemateInterval;
    private final long mapPeriod;
    private final long randomPeriod;
    private final int numObstacles;
    private final int numLabels;
    private final double errSensor;
    private final double errSigma;
    private final int maxAngularSpeed;
    private final PublishProcessor<CameraEvent> cameraEvents;
    private final PublishProcessor<Throwable> errors;
    private final PublishProcessor<WheellyContactsMessage> contactsMessages;
    private final PublishProcessor<WheellyMotionMessage> motionMessages;
    private final PublishProcessor<WheellySupplyMessage> supplyMessages;
    private final BehaviorProcessor<Collection<Obstacle>> obstacleChanged;
    private final BehaviorProcessor<RobotStatusApi> robotLineState;
    private final AtomicReference<SimRobotStatus> status;
    private final List<List<Obstacle>> maps;
    private final World world;
    private final Body robot;
    private final Fixture robotFixture;
    private final long interval;
    private final long tickInterval;
    private final PublishProcessor<WheellyLidarMessage> lidarMessages;
    private Body obstacleBody;

    /**
     * Creates the simulated robot
     *
     * @param robotSpec         the robot specification
     * @param random            the robot random generator
     * @param mapRandom         the map random generator
     * @param tickInterval      the tick interval (ms)
     * @param interval          the simulation interval (ms)
     * @param motionInterval    the motion message interval (ms)
     * @param lidarInterval     the proxy message interval (ms)
     * @param cameraInterval    the camera event interval (ms)
     * @param stalemateInterval the stalemate interval (ms)
     * @param errSensor         the relative error sensor
     * @param errSigma          the relative error on power simulation
     * @param maxAngularSpeed   the maximum angular power
     * @param maps              the list of maps
     * @param numObstacles      the number of obstacles
     * @param numLabels         the number of labels
     * @param mapPeriod         the change map period (ms)
     * @param randomPeriod      the change obstacle period (ms)
     */
    public SimRobot(RobotSpec robotSpec, Random random, Random mapRandom,
                    long tickInterval, long interval, long motionInterval, long lidarInterval, long cameraInterval, long stalemateInterval,
                    double errSensor, double errSigma, int maxAngularSpeed, List<List<Obstacle>> maps, int numObstacles, int numLabels, long mapPeriod, long randomPeriod) {
        this.robotSpec = requireNonNull(robotSpec);
        this.random = requireNonNull(random);
        this.mapRandom = requireNonNull(mapRandom);
        this.motionInterval = motionInterval;
        this.lidarInterval = lidarInterval;
        this.cameraInterval = cameraInterval;
        this.stalemateInterval = stalemateInterval;
        this.mapPeriod = mapPeriod;
        this.randomPeriod = randomPeriod;
        this.numObstacles = numObstacles;
        this.numLabels = numLabels;
        this.errSensor = errSensor;
        this.errSigma = errSigma;
        this.maxAngularSpeed = maxAngularSpeed;
        this.interval = interval;
        this.tickInterval = tickInterval;
        this.maps = requireNonNull(maps);
        this.cameraEvents = PublishProcessor.create();
        this.contactsMessages = PublishProcessor.create();
        this.motionMessages = PublishProcessor.create();
        this.supplyMessages = PublishProcessor.create();
        this.lidarMessages = PublishProcessor.create();
        this.errors = PublishProcessor.create();
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
        circleShape.setRadius(RobotSpec.ROBOT_RADIUS * JBOX_SCALE);
        FixtureDef fixDef = new FixtureDef();
        fixDef.shape = circleShape;
        fixDef.friction = (float) ROBOT_FRICTION;
        fixDef.density = (float) ROBOT_DENSITY;
        fixDef.restitution = (float) ROBOT_RESTITUTION;
        this.robotFixture = robot.createFixture(fixDef);
        SimRobotStatus initialStatus = new SimRobotStatus(0,
                false, false, false,
                Complex.DEG0, 0, 0, true, true,
                direction(), 0, 0, 0,
                0, 0, 0, 0, 0, 0, List.of(), null,
                0, 0);
        this.status = new AtomicReference<>(initialStatus);
        generateRandomMap();
        this.robotLineState = BehaviorProcessor.createDefault(status.get());
    }

    /**
     * Returns the camera location
     */
    private Point2D cameraLocation() {
        return robotSpec.cameraLocation(location(), direction(), sensorDirection());
    }

    /**
     * Returns the camera sensor area
     */
    public AreaExpression cameraSensorArea() {
        return AreaExpression.radialSensorArea(
                cameraLocation(),
                headAbsDirection(),
                robotSpec.cameraFOV(),
                DEFAULT_OBSTACLE_RADIUS,
                ROBOT_RADIUS,
                robotSpec.maxRadarDistance()
        );
    }

    /**
     * Checks for lidar and contact sensors.
     * Sends the robot status in case of contact changes
     *
     * @param initial the initial status
     */
    private void checkForSensor(SimRobotStatus initial) {
        Point2D position = location();
        SimRobotStatus status = this.status.get();

        // Finds the nearest obstacle in front lidar range
        double frontDistance;
        AreaExpression.Parser frontParser = frontLidarArea()
                .createParser();
        Obstacle nearestFrontObstacle = status.obstacleMap().stream()
                .filter(o -> frontParser.test(o.centre()))
                .min(Comparator.comparingDouble(o -> o.centre().distanceSq(position)))
                .orElse(null);
        if (nearestFrontObstacle != null) {
            // Computes the distance of obstacles
            Point2D lidarLocation = frontLidarLocation();
            frontDistance = nearestFrontObstacle.centre().distance(lidarLocation) - nearestFrontObstacle.radius()
                    + random.nextGaussian() * errSensor;
        } else {
            frontDistance = 0;
        }

        // Finds the nearest obstacle in front lidar range
        double rearDistance;
        AreaExpression.Parser rearParser = rearLidarArea().createParser();
        Obstacle nearestRearObstacle = status.obstacleMap().stream()
                .filter(o -> rearParser.test(o.centre()))
                .min(Comparator.comparingDouble(o -> o.centre().distanceSq(position)))
                .orElse(null);
        if (nearestRearObstacle != null) {
            // Computes the distance of obstacles
            Point2D lidarLocation = rearLidarLocation();
            rearDistance = nearestRearObstacle.centre().distance(lidarLocation) - nearestRearObstacle.radius()
                    + random.nextGaussian() * errSensor;
        } else {
            rearDistance = 0;
        }
        status = this.status.updateAndGet(s -> s.frontDistance(frontDistance).rearDistance(rearDistance));

        boolean frontLidarAlarm = frontDistance > 0 && frontDistance <= SAFE_DISTANCE;
        boolean rearLidarAlarm = rearDistance > 0 && rearDistance <= SAFE_DISTANCE;
        boolean prevFrontLidarAlarm = initial.frontDistance() > 0 && initial.frontDistance() <= SAFE_DISTANCE;
        boolean prevRearLidarAlarm = initial.rearDistance() > 0 && initial.rearDistance() <= SAFE_DISTANCE;
        if (frontLidarAlarm != prevFrontLidarAlarm
                || rearLidarAlarm != prevRearLidarAlarm
                || initial.rearSensor() != status.rearSensor()
                || initial.frontSensor() != status.frontSensor()) {
            // Contacts changed -> send status
            sendLidar();
            sendContacts();
            sendMotion();
        }
    }

    /**
     * Halt the robot if it is moving in forbidden direction
     */
    private void checkForSpeed() {
        SimRobotStatus s = status.get();
        if (!s.canMoveForward() && (s.speed() > 0 || ((s.leftPps() + s.rightPps()) > 0))
                || !s.canMoveBackward() && (s.speed() < 0 || ((s.leftPps() + s.rightPps()) < 0))) {
            halt();
        }
    }

    @Override
    public void close() {
        if (!status.getAndUpdate(status -> status.closed(true)).closed()) {
            robotLineState.onNext(status.get());
            robotLineState.onComplete();
            cameraEvents.onComplete();
            contactsMessages.onComplete();
            motionMessages.onComplete();
            supplyMessages.onComplete();
            lidarMessages.onComplete();
            errors.onComplete();
        }
    }

    @Override
    public void connect() {
        if (!syncConnect()) {
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

    /**
     * Creates the obstacle bodies
     */
    private void createObstacleBody(Collection<Obstacle> obstacleMap) {
        Body obstacleBody = this.obstacleBody;
        if (obstacleBody != null) {
            world.destroyBody(obstacleBody);
            status.updateAndGet(s -> s.frontSensor(true).rearSensor(true));
        }

        BodyDef obsDef = new BodyDef();
        obsDef.type = BodyType.STATIC;
        obstacleBody = world.createBody(obsDef);

        for (Obstacle cell : obstacleMap) {
            CircleShape obsShape = new CircleShape();
            Vec2 center = new Vec2((float) cell.centre().getX() * JBOX_SCALE, (float) cell.centre().getY() * JBOX_SCALE);
            obsShape.setRadius((float) (cell.radius() * JBOX_SCALE));
            obsShape.m_p.set(center);
            FixtureDef obsFixDef = new FixtureDef();
            obsFixDef.shape = obsShape;
            obstacleBody.createFixture(obsFixDef);
        }
        this.obstacleBody = obstacleBody;
    }

    /**
     * Returns the robot direction
     */
    public Complex direction() {
        return Complex.fromRad(PI / 2 - robot.getAngle());
    }

    /**
     * Returns the rear distance (m)
     */
    public double frontDistance() {
        return status.get().frontDistance();
    }

    /**
     * Returns the front lidar area
     */
    private AreaExpression frontLidarArea() {
        return AreaExpression.radialSensorArea(
                frontLidarLocation(), headAbsDirection(), robotSpec.lidarFOV(),
                DEFAULT_OBSTACLE_RADIUS,
                DEFAULT_OBSTACLE_RADIUS,
                robotSpec.maxRadarDistance() + DEFAULT_OBSTACLE_RADIUS
        );
    }

    /**
     * Returns the front lidar location
     */
    Point2D frontLidarLocation() {
        return robotSpec.frontLidarLocation(location(), direction(), sensorDirection());
    }

    /**
     * Return a random safe location for the robot
     *
     * @param map the obstacle map
     */
    private Point2D generateLocation(Collection<Obstacle> map) {
        Point2D loc1;
        for (; ; ) {
            // Generates a random location in the map
            loc1 = new Point2D.Double(
                    random.nextDouble() * WORLD_SIZE - WORLD_SIZE / 2,
                    random.nextDouble() * WORLD_SIZE - WORLD_SIZE / 2
            );
            Point2D finalLoc = loc1;
            // Check for safe distance from any obstacles
            if (map.stream()
                    .noneMatch(cell ->
                            finalLoc.distanceSq(cell.centre()) <= SAFE_DISTANCE_SQ
                    )) {
                break;
            }
        }
        return loc1;
    }

    @Override
    public Single<Boolean> halt() {
        SimRobotStatus s0 = status.getAndUpdate(s -> s.speed(0)
                .sensorDirection(Complex.DEG0)
                .leftPps(0)
                .rightPps(0)
                .direction(direction()));
        logger.atDebug().log("{}: Halt", s0.simulationTime());
        return Single.just(true);
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

    /**
     * Returns the head absolute direction
     */
    Complex headAbsDirection() {
        return direction().add(sensorDirection());
    }

    /**
     * Returns the head direction relative the robot
     */
    public Complex headDirection() {
        return status.get().sensorDirection();
    }

    @Override
    public boolean isHalt() {
        SimRobotStatus st = status.get();
        return st.speed() == 0 && abs(st.leftPps()) < 1 && abs(st.rightPps()) < 1;
    }

    /**
     * Returns the robot location
     */
    public Point2D location() {
        Vec2 pos = robot.getPosition();
        return new Point2D.Double(pos.x / JBOX_SCALE, pos.y / JBOX_SCALE);
    }

    @Override
    public Single<Boolean> move(int dir, int speed) {
        status.updateAndGet(s -> s.direction(Complex.fromDeg(dir))
                .speed(clip(speed, -RobotSpec.MAX_PPS, RobotSpec.MAX_PPS)));
        checkForSpeed();
        return Single.just(true);
    }

    /**
     * Generates a random map and returns the simulated status
     */
    private SimRobotStatus generateRandomMap() {
        List<Obstacle> template = randomTemplate();
        SimRobotStatus currentStatus = status.updateAndGet(s ->
                s.template(template).createObstacleMap(mapRandom, location(), numObstacles, numLabels, mapPeriod, randomPeriod)
        );
        obstacleChanged.onNext(currentStatus.obstacleMap());
        return currentStatus;
    }

    /**
     * Generates a random content map and returns the simulated status
     */
    private void generateRandomMapContent() {
        SimRobotStatus currentStatus = status.updateAndGet(s ->
                s.createObstacleMap(mapRandom, location(), numObstacles, numLabels, mapPeriod, randomPeriod)
        );
        obstacleChanged.onNext(currentStatus.obstacleMap());
    }

    @Override
    public Flowable<CameraEvent> readCamera() {
        return cameraEvents;
    }

    @Override
    public Flowable<WheellyContactsMessage> readContacts() {
        return contactsMessages;
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

    /**
     * Returns the obstacle map flow
     */
    public Flowable<Collection<Obstacle>> readObstacleMap() {
        return obstacleChanged;
    }

    @Override
    public Flowable<RobotStatusApi> readRobotStatus() {
        return robotLineState;
    }

    @Override
    public Flowable<WheellySupplyMessage> readSupply() {
        return supplyMessages;
    }

    /**
     * Returns the front distance (m)
     */
    public double rearDistance() {
        return status.get().rearDistance();
    }

    /**
     * Returns the rear lidar area
     */
    public AreaExpression rearLidarArea() {
        return AreaExpression.radialSensorArea(
                rearLidarLocation(), headAbsDirection().opposite(), robotSpec.lidarFOV(),
                DEFAULT_OBSTACLE_RADIUS,
                DEFAULT_OBSTACLE_RADIUS,
                robotSpec.maxRadarDistance() + DEFAULT_OBSTACLE_RADIUS
        );
    }

    /**
     * Returns the rear lidar location
     */
    Point2D rearLidarLocation() {
        return robotSpec.rearLidarLocation(location(), direction(), sensorDirection());
    }

    @Override
    public void reconnect() {
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
    public RobotSpec robotSpec() {
        return robotSpec;
    }

    /**
     * Randomly relocates the robot
     */
    public void safeRelocateRandom() {
        SimRobotStatus s = status.get();
        Collection<Obstacle> map = s.obstacleMap();
        Point2D loc = map != null
                ? generateLocation(map)
                : new Point2D.Double();
        // Relocate robot
        robotPos(loc.getX(), loc.getY());
    }

    @Override
    public Single<Boolean> scan(int direction) {
        Complex dir = Complex.fromDeg(clip(direction, -90, 90));
        status.updateAndGet(s ->
                s.sensorDirection(dir)
        );
        return Single.just(true);
    }

    /**
     * Sends the camera message
     */
    private void sendCamera() {
        SimRobotStatus s = status.get();
        Point2D cameraLocation = location();
        Complex cameraAzimuth = direction().add(s.sensorDirection());
        // Extracts the obstacles intersecting the camera fov
        Predicate<Point2D> areaParser = cameraSensorArea()
                .createParser()::test;
        Point2D markerLocation = s.obstacleMap().stream()
                .filter(o -> o.label() != null)
                .map(Obstacle::centre)
                .filter(areaParser)
                .min(Comparator.comparingDouble(cameraLocation::distanceSq))
                .orElse(null);

        Point2D[] points = new Point2D[0];
        CameraEvent event;
        if (markerLocation != null) {
            Complex markerDirection = Complex.direction(cameraLocation, markerLocation);
            Complex markerRelativeDirection = markerDirection.sub(cameraAzimuth);
            event = new CameraEvent(s.simulationTime(), QR_CODE, CAMERA_WIDTH, CAMERA_HEIGHT, points, markerRelativeDirection);
        } else {
            event = CameraEvent.unknown(s.simulationTime());
        }
        cameraEvents.onNext(event);
        status.updateAndGet(s1 -> s1.cameraTimeout(s.simulationTime() + cameraInterval));
    }

    /**
     * Sends the contact message
     */
    private void sendContacts() {
        SimRobotStatus s = status.get();
        WheellyContactsMessage msg = new WheellyContactsMessage(
                s.simulationTime(),
                s.frontSensor(), s.rearSensor(),
                s.canMoveForward(),
                s.canMoveBackward()
        );
        logger.atDebug().log("On contacts {}", msg);
        contactsMessages.onNext(msg);
    }

    /**
     * Sends the lidar message
     */
    private void sendLidar() {
        SimRobotStatus s = status.get();
        Point2D pos = this.location();
        double xPulses = distance2Pulse(pos.getX());
        double yPulses = distance2Pulse(pos.getY());
        Complex robotYaw = direction();
        WheellyLidarMessage msg = new WheellyLidarMessage(
                s.simulationTime(),
                m2mm(s.frontDistance()), m2mm(s.rearDistance()),
                xPulses, yPulses, robotYaw.toIntDeg(), s.sensorDirection().toIntDeg()
        );
        logger.atDebug().log("lidar R{} D{} D{}", msg.headDirection().toDeg(), msg.frontDistance(), msg.rearDistance());
        lidarMessages.onNext(msg);
        status.updateAndGet(s1 -> s1.lidarTimeout(s1.simulationTime() + lidarInterval));
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
                s.simulationTime(),
                xPulses, yPulses, robotDir.toIntDeg(),
                s.leftPps(), s.rightPps(),
                0, s.speed() == 0,
                (int) round(s.leftPps()), (int) round(s.rightPps()),
                0, 0);
        motionMessages.onNext(msg);
        status.updateAndGet(s1 -> s1.motionTimeout(s1.simulationTime() + motionInterval));
    }

    /**
     * Returns the sensor direction
     */
    public Complex sensorDirection() {
        return status.get().sensorDirection();
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
     * Sets the obstacle map
     *
     * @param map the map
     */
    public SimRobot obstacleMap(Collection<Obstacle> map) {
        status.updateAndGet(s -> s.obstacleMap(map));
        createObstacleBody(map);
        obstacleChanged.onNext(map);
        return this;
    }

    /**
     * Returns a random template map
     */
    private List<Obstacle> randomTemplate() {
        return maps.isEmpty()
                ? List.of()
                : maps.size() == 1
                ? maps.getFirst()
                : maps.get(mapRandom.nextInt(maps.size()));
    }

    /**
     * Simulate the time interval
     */
    void simulate() {
        SimRobotStatus initialStatus = status.get();
        SimRobotStatus currentStatus = status.updateAndGet(s ->
                s.simulationTime(s.simulationTime() + interval)
                        .lastTick(System.nanoTime()));

        // Check for map expiration
        if (currentStatus.simulationTime() >= currentStatus.mapExpiration()) {
            currentStatus = generateRandomMap();
        }
        // Check for random map expiration
        if (currentStatus.simulationTime() >= currentStatus.randomMapExpiration()) {
            generateRandomMapContent();
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
        updateLidar();
        updateCamera();
    }

    /**
     * Simulates robot physics for interval time
     */
    private void simulatePhysics() {
        double dt = interval * 1e-3;
        SimRobotStatus status = this.status.get();
        // Direction difference
        double dAngle = direction().sub(status.direction()).toRad();

        // Relative angular power to fix the direction
        double angularVelocityPps = clip(
                Utils.linear(dAngle, -RAD_10, RAD_10, -maxAngularSpeed, maxAngularSpeed),
                -maxAngularSpeed, maxAngularSpeed);

        // Relative linear power to fix the power
        double linearVelocityPps = status.speed() *
                clip(
                        Utils.linear(abs(dAngle), 0, RAD_30, 1, 0),
                        0, 1);

        // Relative left-right motor speeds
        double leftPps = clip((linearVelocityPps - angularVelocityPps), -RobotSpec.MAX_PPS, RobotSpec.MAX_PPS);
        double rightPps = clip((linearVelocityPps + angularVelocityPps), -RobotSpec.MAX_PPS, RobotSpec.MAX_PPS);

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

        // target real power
        Vec2 targetVelocity = robot.getWorldVector(Utils.vec2(forwardVelocity * JBOX_SCALE, 0));
        // Difference of power
        Vec2 dv = targetVelocity.sub(robot.getLinearVelocity());
        // Impulse to fix the power
        Vec2 dq = dv.mul(robot.getMass());
        // Force to fix the power
        Vec2 force = dq.mul((float) (1 / dt));
        // Robot relative force
        Vec2 localForce = robot.getLocalVector(force);
        // add a random factor to force
        localForce = localForce.mul((float) (1 + random.nextGaussian() * errSensor));

        // Clip the local force to physic constraints
        localForce.x = (float) clip(localForce.x, -MAX_FORCE, MAX_FORCE);
        force = robot.getWorldVector(localForce);

        // Angle rotation due to differential motor speeds
        double angularVelocity1 = (right - left) / RobotSpec.ROBOT_TRACK;
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
     * Returns the current robot status
     */
    SimRobotStatus status() {
        return status.get();
    }

    /**
     * Connects the roboto if not yet connected and returns true if already connected
     */
    boolean syncConnect() {
        boolean alreadyConnected = status.getAndUpdate(status ->
                status.connected(true)
        ).connected();
        if (!alreadyConnected) {
            SimRobotStatus st = status.updateAndGet(s -> s.startSimulationTime(System.nanoTime()));
            robotLineState.onNext(st);
            updateMotion();
            updateLidar();
            updateCamera();
        }
        return alreadyConnected;
    }

    /**
     * Schedules the computation of status on the time interval
     */
    private void tick() {
        SimRobotStatus s = status.get();
        if (s.connected() && !s.closed()) {
            (tickInterval == 0
                    ? Completable.complete()
                    : Completable.timer(tickInterval, TimeUnit.MILLISECONDS))
                    .subscribeOn(Schedulers.computation())
                    .subscribe(() -> {
                        simulate();
                        // Reschedules the simulation
                        tick();
                    });
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
     * Sends the proxy message if the interval has elapsed
     */
    private void updateLidar() {
        SimRobotStatus s = status.get();
        if (s.simulationTime() >= s.lidarTimeout()) {
            sendLidar();
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
}
