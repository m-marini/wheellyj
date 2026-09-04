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
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.Obstacle.DEFAULT_OBSTACLE_RADIUS;
import static org.mmarini.wheelly.apis.RobotSpec.*;
import static org.mmarini.wheelly.apis.RobotStatus.OBSTACLE_SIZE;
import static org.mmarini.wheelly.apis.RobotStatusId.*;
import static org.mmarini.wheelly.apis.Utils.expRandom;
import static org.mmarini.wheelly.apis.Utils.m2mm;

/**
 * Simulated robot
 */
public class SimRobot implements RobotApi {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/sim-robot-schema-3.3";
    public static final double DEFAULT_WORLD_SIZE = 10;
    public static final double MAX_ANGULAR_PPS = 20;
    public static final double MAX_ANGULAR_VELOCITY = MAX_ANGULAR_PPS * DISTANCE_PER_PULSE / RobotSpec.ROBOT_TRACK * 2; // RAD/s
    public static final double SAFE_DISTANCE = 0.2;
    public static final int CAMERA_HEIGHT = 240;
    public static final int CAMERA_WIDTH = 240;
    public static final String QR_CODE = "A";
    public static final double NANOS_PER_MILLIS = 1e6;
    public static final long DEFAULT_STALEMATE_INTERVAL = 60000;
    public static final long DEFAULT_MOTION_INTERVAL = 500;
    public static final long DEFAULT_LIDAR_INTERVAL = 500;
    public static final long DEFAULT_CAMERA_INTERVAL = 500;
    public static final String LABEL = "A";
    public static final double MIN_OBSTACLE_DISTANCE = 1;
    static final float JBOX_SCALE = 100;
    static final double MAX_ACC = 1 * JBOX_SCALE;
    private static final Logger logger = LoggerFactory.getLogger(SimRobot.class);
    private static final Vec2 GRAVITY = new Vec2();
    private static final double ROBOT_FRICTION = 1;
    private static final double ROBOT_RESTITUTION = 0;
    private static final double ROBOT_DENSITY = RobotSpec.ROBOT_MASS / (RobotSpec.ROBOT_RADIUS * RobotSpec.ROBOT_RADIUS * PI * JBOX_SCALE * JBOX_SCALE);
    private static final double MAX_FORCE = MAX_ACC * RobotSpec.ROBOT_MASS;
    private static final double MAX_TORQUE = MAX_FORCE * ROBOT_TRACK * JBOX_SCALE;
    // private static final double MAX_TORQUE = 0.7 * JBOX_SCALE * JBOX_SCALE;
    private static final int VELOCITY_ITER = 10;
    private static final int POSITION_ITER = 10;
    private static final double SAFE_DISTANCE_SQ = pow(SAFE_DISTANCE + OBSTACLE_SIZE, 2);
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
    private final double worldSize;
    private final double errSensor;
    private final double errSigma;
    private final PublishProcessor<Throwable> errors;
    private final BehaviorProcessor<Collection<Obstacle>> obstacleChanged;
    private final BehaviorProcessor<RobotStatusApi> robotLineState;
    private final List<MapBuilder> maps;
    private final World world;
    private final Body robot;
    private final Fixture robotFixture;
    private final long interval;
    private final long tickInterval;
    private final AtomicReference<RobotRequests> requests;
    private final List<Consumer<WheellyContactsMessage>> onContacts;
    private final List<Consumer<WheellyLidarMessage>> onLidars;
    private final List<Consumer<WheellyMotionMessage>> onMotions;
    private final List<Consumer<CameraEvent>> onCameras;
    private Body obstacleBody;
    private boolean connected;
    private boolean closed;
    private long startSimulationTime;
    private long robotTime;
    private long lastTick;
    private long motionTimeout;
    private long lidarTimeout;
    private long cameraTimeout;
    private long stalemateTimeout;
    private boolean stalemate;
    private long mapExpiration;
    private Collection<Obstacle> obstacleMap;
    private MapBuilder template;
    private long randomMapExpiration;
    private Complex targetDirection;
    private Point2D target;
    private Complex headDirection;
    private RobotStatusId statusId;
    private double frontDistance;
    private double rearDistance;
    private boolean frontSensor;
    private boolean rearSensor;
    private double leftPps;
    private double rightPps;
    private boolean sendLidar;
    private boolean sendMotion;
    private boolean sendContacts;

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
     * @param maps              the list of maps
     * @param numObstacles      the number of obstacles
     * @param numLabels         the number of labels
     * @param mapPeriod         the change map period (ms)
     * @param randomPeriod      the change obstacle period (ms)
     * @param worldSize         the world size (m)
     */
    public SimRobot(RobotSpec robotSpec, Random random, Random mapRandom,
                    long tickInterval, long interval, long motionInterval, long lidarInterval, long cameraInterval,
                    long stalemateInterval, double errSensor, double errSigma, List<MapBuilder> maps, int numObstacles,
                    int numLabels, long mapPeriod, long randomPeriod, double worldSize) {
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
        this.interval = interval;
        this.tickInterval = tickInterval;
        this.maps = requireNonNull(maps);
        this.worldSize = worldSize;
        this.requests = new AtomicReference<>(RobotRequests.empty());
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
        this.headDirection = Complex.DEG0;
        this.statusId = HALT;
        this.frontSensor = this.rearSensor = true;
        this.robotLineState = BehaviorProcessor.createDefault(new RobotLineState(false, false, false, false));
        this.onContacts = new ArrayList<>();
        this.onLidars = new ArrayList<>();
        this.onMotions = new ArrayList<>();
        this.onCameras = new ArrayList<>();
        generateRandomMap();
    }

    /**
     * Returns the simulated robot from JSON configuration
     *
     * @param root the JSON document
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
        long motionInterval = locator.path("motionInterval").getNode(root).asLong(DEFAULT_MOTION_INTERVAL);
        long lidarInterval = locator.path("lidarInterval").getNode(root).asLong(DEFAULT_LIDAR_INTERVAL);
        long stalemateInterval = locator.path("stalemateInterval").getNode(root).asLong(DEFAULT_STALEMATE_INTERVAL);
        long cameraInterval = locator.path("cameraInterval").getNode(root).asLong(DEFAULT_CAMERA_INTERVAL);
        long interval = locator.path("interval").getNode(root).asLong();
        long tickInterval = locator.path("tickInterval").getNode(root).asLong();
        long mapPeriod = locator.path("mapPeriod").getNode(root).asLong();
        long randomPeriod = locator.path("randomPeriod").getNode(root).asLong();
        double worldSize = locator.path("worldSize").getNode(root).asDouble(DEFAULT_WORLD_SIZE);
        RobotSpec robotSpec = RobotSpec.fromJson(root, locator);
        List<MapBuilder> maps = locator.path("mapFiles").elements(root)
                .map(l -> {
                    String filename = l.getNode(root).asText();
                    try {
                        JsonNode mapYaml = org.mmarini.yaml.Utils.fromFile(filename);
                        return MapBuilder.create(mapYaml, Locator.root());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
        return new SimRobot(robotSpec, robotRandom, mapRandom,
                tickInterval, interval, motionInterval, lidarInterval, cameraInterval, stalemateInterval,
                errSensor, errSigma,
                maps, numObstacles, numLabels, mapPeriod, randomPeriod, worldSize);
    }

    @Override
    public void addOnContacts(Consumer<WheellyContactsMessage> callback) {
        onContacts.add(callback);
    }

    @Override
    public void addOnLidar(Consumer<WheellyLidarMessage> callback) {
        onLidars.add(callback);
    }

    @Override
    public void addOnMotion(Consumer<WheellyMotionMessage> callback) {
        onMotions.add(callback);
    }

    @Override
    public void addOnSupply(Consumer<WheellySupplyMessage> callback) {
    }

    @Override
    public Single<Boolean> backward(Point2D location) {
        requireNonNull(location);
        requests.updateAndGet(s -> s.backward(location));
        return Single.just(true);
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
     * Returns true if robot can move backward
     */
    public boolean canMoveBackward() {
        return rearSensor && (rearDistance == 0 || rearDistance > SAFE_DISTANCE);
    }

    /**
     * Returns true if robot can move forward
     */
    public boolean canMoveForward() {
        return frontSensor && (frontDistance == 0 || frontDistance > SAFE_DISTANCE);
    }

    /**
     * Checks for lidar and contact sensors.
     * Sends the robot status in case of contact changes
     *
     * @param initialFrontSensor true if no contact at front sensor before sensor check
     * @param initialRearSensor  true if no contact at rear sensor before sensor check
     */
    private void checkForSensor(boolean initialFrontSensor, boolean initialRearSensor) {
        Point2D position = location();

        // Finds the nearest obstacle in front lidar range
        double currentFrontDistance;
        AreaExpression.Parser frontParser = frontLidarArea()
                .createParser();
        Obstacle nearestFrontObstacle = obstacleMap.stream()
                .filter(o -> frontParser.test(o.centre()))
                .min(Comparator.comparingDouble(o -> o.centre().distanceSq(position)))
                .orElse(null);
        if (nearestFrontObstacle != null) {
            // Computes the distance of obstacles
            Point2D lidarLocation = frontLidarLocation();
            currentFrontDistance = nearestFrontObstacle.centre().distance(lidarLocation) - nearestFrontObstacle.radius()
                    + random.nextGaussian() * errSensor;
        } else {
            currentFrontDistance = 0;
        }

        // Finds the nearest obstacle in front lidar range
        double currentRearDistance;
        AreaExpression.Parser rearParser = rearLidarArea().createParser();
        Obstacle nearestRearObstacle = obstacleMap.stream()
                .filter(o -> rearParser.test(o.centre()))
                .min(Comparator.comparingDouble(o -> o.centre().distanceSq(position)))
                .orElse(null);
        if (nearestRearObstacle != null) {
            // Computes the distance of obstacles
            Point2D lidarLocation = rearLidarLocation();
            currentRearDistance = nearestRearObstacle.centre().distance(lidarLocation) - nearestRearObstacle.radius()
                    + random.nextGaussian() * errSensor;
        } else {
            currentRearDistance = 0;
        }
        boolean prevFrontLidarAlarm = frontDistance > 0 && frontDistance <= SAFE_DISTANCE;
        boolean prevRearLidarAlarm = rearDistance > 0 && rearDistance <= SAFE_DISTANCE;
        this.frontDistance = currentFrontDistance;
        this.rearDistance = currentRearDistance;

        boolean frontLidarAlarm = currentFrontDistance > 0 && currentFrontDistance <= SAFE_DISTANCE;
        boolean rearLidarAlarm = currentRearDistance > 0 && currentRearDistance <= SAFE_DISTANCE;
        if (frontLidarAlarm != prevFrontLidarAlarm
                || rearLidarAlarm != prevRearLidarAlarm
                || initialRearSensor != rearSensor
                || initialFrontSensor != frontSensor) {
            // Contacts changed -> send status
            sendLidar = sendContacts = sendMotion = true;
        }
    }

    /**
     * Halt the robot if it is moving in forbidden direction
     */
    private void checkForSpeed() {
        if ((FORWARD.equals(statusId) && !canMoveForward())
                || (BACKWARD.equals(statusId) && !canMoveBackward())) {
            haltImmediate();
            sendMotion = true;
        }
    }

    @Override
    public void close() {
        requests.updateAndGet(r -> r.close(true));
        logger.atInfo().log("Closing robot ...");
    }

    /**
     * Set speed by composing linear and rotation speeds
     *
     * @param linear   the linear speed (pps)
     * @param rotation the rotation speed (pps)
     */
    private void composeSpeed(double linear, double rotation) {
        leftPps = clamp(linear + rotation, -RobotSpec.MAX_PPS, RobotSpec.MAX_PPS);
        rightPps = clamp(linear - rotation, -RobotSpec.MAX_PPS, RobotSpec.MAX_PPS);
    }

    @Override
    public void connect() {
        if (!closed && !connected) {
            syncConnect();
            if (tickInterval == 0) {
                startSyncSimulation();
            } else {
                logger.atInfo().log("Started simulation");
                tick();
            }
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
            frontSensor = true;
            rearSensor = true;
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
        this.obstacleMap = obstacleMap;
    }

    /**
     * Returns a new obstacle map from the current template
     */
    private List<Obstacle> createObstacleMap() {
        Point2D robotLocation = location();
        return template
                // add obstacles
                .rand(random, null,
                        robotLocation, MIN_OBSTACLE_DISTANCE, numObstacles)
                // add labels
                .rand(random, LABEL,
                        robotLocation, MIN_OBSTACLE_DISTANCE, numLabels)
                .build();
    }

    /**
     * Returns the robot direction
     */
    public Complex direction() {
        return Complex.fromRad(PI / 2 - robot.getAngle());
    }

    @Override
    public Single<Boolean> forward(Point2D location) {
        requireNonNull(location);
        requests.updateAndGet(s -> s.forward(location));
        return Single.just(true);
    }

    /**
     * Returns the rear distance (m)
     */
    public double frontDistance() {
        return frontDistance;
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
     *
     * Returns true if no contact at front sensor
     */
    public boolean frontSensor() {
        return frontSensor;
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
                    random.nextDouble() * worldSize - worldSize / 2,
                    random.nextDouble() * worldSize - worldSize / 2
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

    /**
     * Generates a random map and returns the simulated status
     */
    private void generateRandomMap() {
        // Selects a random map builder
        template = maps.size() == 1
                ? maps.getFirst()
                : maps.get(mapRandom.nextInt(maps.size()));
        // Creates the obstacle map
        Collection<Obstacle> map = createObstacleMap();
        createObstacleBody(map);
        obstacleChanged.onNext(map);
    }

    /**
     * Generates a random content map and returns the simulated status
     */
    private void generateRandomMapContent() {
        Collection<Obstacle> map = createObstacleMap();
        createObstacleBody(map);
        obstacleChanged.onNext(map);
    }

    @Override
    public Single<Boolean> halt() {
        requests.updateAndGet(RobotRequests::halt);
        return Single.just(true);
    }

    private void haltImmediate() {
        statusId = HALT;
        leftPps = rightPps = 0;
    }

    /**
     * Sets the motor speed to handle move backward
     */
    private void handleBackward() {
        // Compute the distance to target
        Point2D robotLocation = location();
        double distance = robotLocation.distance(target);
        // Check for target reached
        if (distance <= robotSpec.targetRange()) {
            // Target reached
            haltImmediate();
            sendMotion = true;
            return;
        }
        // Compute the rotation angle
        Complex targetDirection = Complex.direction(robotLocation, target);
        double rotDeg = targetDirection.sub(direction()).opposite().toDeg();
        double absRotDeg = abs(rotDeg);
        // Compute che rotation speed
        double maxRotDeg = robotSpec.maxRotRange().toDeg();
        double rotSpeed = absRotDeg > maxRotDeg
                ? rotDeg >= 0
                ? robotSpec.maxRotPps()
                : -robotSpec.maxRotPps()
                // Rotate at speed proportional the rotation angle
                : robotSpec.maxRotPps() * rotDeg / maxRotDeg;
        // Compute the linear speed
        double decDistance = robotSpec.decelerateDistance();
        double linSpeed = absRotDeg > maxRotDeg
                ? 0 // robot not in target direction
                : distance >= decDistance
                ? -robotSpec.maxSpeed() // Robot distant from target
                : -robotSpec.maxSpeed() * distance / decDistance; // Robot near the target
        composeSpeed(linSpeed, rotSpeed);
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
        this.frontSensor = frontSensor;
        this.rearSensor = rearSensor;
    }

    /**
     * Sets the motor speed based on the command status
     */
    private void handleEngine() {
        switch (statusId) {
            case ROTATE -> handleRotation();
            case FORWARD -> handleForward();
            case BACKWARD -> handleBackward();
        }
    }

    /**
     * Sets the motor speed to handle move forward
     */
    private void handleForward() {
        // Compute the distance to target
        Point2D robotLocation = location();
        double distance = robotLocation.distance(target);
        // Check for target reached
        if (distance <= robotSpec.targetRange()) {
            // Target reached
            haltImmediate();
            sendMotion = true;
            return;
        }
        // Compute the rotation angle
        Complex targetDirection = Complex.direction(robotLocation, target);
        double rotDeg = targetDirection.sub(direction()).toDeg();
        double absRotDeg = abs(rotDeg);
        // Compute che rotation speed
        double maxRotDeg = robotSpec.maxRotRange().toDeg();
        double rotSpeed = absRotDeg > maxRotDeg
                ? rotDeg >= 0
                ? robotSpec.maxRotPps()
                : -robotSpec.maxRotPps()
                // Rotate at speed proportional the rotation angle
                : robotSpec.maxRotPps() * rotDeg / maxRotDeg;
        // Compute the linear speed
        double decDistance = robotSpec.decelerateDistance();
        double linSpeed = absRotDeg > maxRotDeg
                ? 0 // robot not in target direction
                : distance >= decDistance
                ? robotSpec.maxSpeed() // Robot distant from target
                : robotSpec.maxSpeed() * distance / decDistance; // Robot near the target
        composeSpeed(linSpeed, rotSpeed);
    }

    /**
     * Handles the pending requests
     */
    private void handleRequests() {
        // Handle close request
        if (!closed) {
            RobotRequests r = requests.getAndSet(RobotRequests.empty());
            if (r.close()) {
                closed = true;
                errors.onComplete();
                robotLineState.onNext(new RobotLineState(false, false, false, false));
                robotLineState.onComplete();
                logger.atInfo().log("Sim robot closed");
                return;
            }
            long t = r.simulationTime();
            if (t >= 0) {
                logger.atDebug().log("Set simulation time");
                robotTime = t;
            }
            if (r.statusId() != null) {
                switch (r.statusId()) {
                    case ROTATE -> {
                        statusId = ROTATE;
                        targetDirection = Complex.fromDeg(r.targetDir());
                        logger.atDebug().log("Rotate robot to {} DEG", targetDirection.toIntDeg());
                    }
                    case BACKWARD -> {
                        statusId = BACKWARD;
                        target = r.target();
                        logger.atDebug().log("Move robot backward to {}", target);
                    }
                    case FORWARD -> {
                        statusId = FORWARD;
                        target = r.target();
                        logger.atDebug().log("Move robot forward to {}", target);
                    }
                    default -> {
                        statusId = HALT;
                        headDirection = Complex.DEG0;
                        leftPps = rightPps = 0;
                        logger.atDebug().log("Halt robot");
                    }
                }
                checkForSpeed();
            }
            Complex headDir = r.headDir();
            if (headDir != null) {
                this.headDirection = headDir;
                logger.atDebug().log("Rotate head to {} DEG", headDir.toIntDeg());
            }
        }
    }

    /**
     * Sets the motor speed to handle rotation
     */
    private void handleRotation() {
        // Compute the rotation angle
        double rotDeg = targetDirection.sub(direction()).toDeg();
        double absRotDeg = abs(rotDeg);
        // Compute che rotation speed
        double rotSpeed;
        // Check for rotation completed
        if (absRotDeg <= robotSpec.directionRange().toIntDeg()) {
            // Rotation completed -> halt robot
            haltImmediate();
            sendMotion = true;
            return;
        }
        double maxRotDeg = robotSpec.maxRotRange().toDeg();
        if (absRotDeg > maxRotDeg) {
            // rotate at max speed
            rotSpeed = rotDeg >= 0
                    ? robotSpec.maxRotPps()
                    : -robotSpec.maxRotPps();
        } else {
            // Rotate at speed proportional the rotation angle
            rotSpeed = robotSpec.maxRotPps() * rotDeg / maxRotDeg;
        }
        composeSpeed(0, rotSpeed);
    }

    /**
     * Handles the stalemate status
     * Checks for stalemate and relocate the robot in case of stalemate timeout
     */
    private void handleStalemate() {
        if (frontSensor || rearSensor) {
            // no stalemate
            stalemate = false;
        } else if (!stalemate) {
            // First stalemate, start the timer
            stalemate = true;
            stalemateTimeout = robotTime + stalemateInterval;
        } else if (robotTime >= stalemateTimeout) {
            // stalemate timeout
            safeRelocateRandom();
        }
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
        return headDirection;
    }

    @Override
    public boolean isHalt() {
        return HALT.equals(statusId);
    }

    /**
     * Returns the robot location
     */
    public Point2D location() {
        Vec2 pos = robot.getPosition();
        return new Point2D.Double(pos.x / JBOX_SCALE, pos.y / JBOX_SCALE);
    }

    /**
     * Sets the obstacle map
     *
     * @param map the map
     */
    public SimRobot obstacleMap(Collection<Obstacle> map) {
        obstacleMap = map;
        createObstacleBody(map);
        obstacleChanged.onNext(map);
        randomMapExpiration = robotTime + mapPeriod;
        mapExpiration = robotTime + randomPeriod;
        return this;
    }

    public Collection<Obstacle> obstacleMap() {
        return obstacleMap;
    }

    @Override
    public void onCamera(Consumer<CameraEvent> callback) {
        onCameras.add(callback);
    }

    @Override
    public Flowable<Throwable> readErrors() {
        return errors;
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

    /**
     * Returns the front distance (m)
     */
    public double rearDistance() {
        return rearDistance;
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

    /**
     *
     * Returns true if no contact at rear sensor
     */
    public boolean rearSensor() {
        return rearSensor;
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

    @Override
    public long robotTime() {
        return robotTime;
    }

    @Override
    public Single<Boolean> rotate(int dir) {
        requests.updateAndGet(r -> r.rotate(dir));
        return Single.just(true);
    }

    /**
     * Randomly relocates the robot
     */
    public void safeRelocateRandom() {
        Collection<Obstacle> map = obstacleMap;
        Point2D loc = map != null
                ? generateLocation(map)
                : new Point2D.Double();
        // Relocate robot
        robotPos(loc.getX(), loc.getY());
    }

    @Override
    public Single<Boolean> scan(int direction) {
        Complex dir = Complex.fromDeg(clamp(direction, -90, 90));
        requests.updateAndGet(s ->
                s.headDir(dir)
        );
        return Single.just(true);
    }

    /**
     * Sends the camera message
     */
    private void sendCamera() {
        Point2D cameraLocation = location();
        Complex cameraAzimuth = direction().add(headDirection);
        // Extracts the obstacles intersecting the camera fov
        Predicate<Point2D> areaParser = cameraSensorArea()
                .createParser()::test;
        Point2D markerLocation = obstacleMap.stream()
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
            event = new CameraEvent(robotTime, QR_CODE, CAMERA_WIDTH, CAMERA_HEIGHT, points, markerRelativeDirection);
        } else {
            event = CameraEvent.unknown(robotTime);
        }
        for (Consumer<CameraEvent> callback : onCameras) {
            callback.accept(event);
        }
        cameraTimeout = robotTime + cameraInterval;
    }

    /**
     * Sends the contact message
     */
    private void sendContacts() {
        WheellyContactsMessage msg = new WheellyContactsMessage(
                robotTime,
                frontSensor, rearSensor,
                canMoveForward(),
                canMoveBackward()
        );
        for (Consumer<WheellyContactsMessage> callback : onContacts) {
            callback.accept(msg);
        }
    }

    /**
     * Sends the lidar message
     */
    private void sendLidar() {
        Point2D pos = this.location();
        double xPulses = distance2Pulse(pos.getX());
        double yPulses = distance2Pulse(pos.getY());
        Complex robotYaw = direction();
        WheellyLidarMessage msg = new WheellyLidarMessage(
                robotTime,
                m2mm(frontDistance), m2mm(rearDistance),
                xPulses, yPulses, robotYaw.toIntDeg(), headDirection.toIntDeg()
        );
        lidarTimeout = robotTime + lidarInterval;
        for (Consumer<WheellyLidarMessage> callback : onLidars) {
            callback.accept(msg);
        }
    }

    /**
     * Sends the motion message
     */
    private void sendMotion() {
        Point2D pos = this.location();
        double xPulses = pos.getX() / DISTANCE_PER_PULSE;
        double yPulses = pos.getY() / DISTANCE_PER_PULSE;
        Complex robotDir = direction();
        WheellyMotionMessage msg = new WheellyMotionMessage(
                robotTime,
                xPulses, yPulses, robotDir.toIntDeg(),
                leftPps, rightPps,
                0, isHalt(),
                (int) round(leftPps), (int) round(rightPps),
                0, 0);
        motionTimeout = robotTime + motionInterval;
        for (Consumer<WheellyMotionMessage> callback : onMotions) {
            callback.accept(msg);
        }
    }

    /**
     * Returns the sensor direction
     */
    public Complex sensorDirection() {
        return headDirection;
    }

    /**
     * Sets the sensor direction
     *
     * @param sensorDirection the sensor direction
     */
    SimRobot sensorDirection(Complex sensorDirection) {
        headDirection = sensorDirection;
        return this;
    }

    /**
     * Simulate the time interval
     */
    void simulate() {
        // Update current simulation time
        robotTime += interval;
        sendMotion = sendContacts = sendLidar = false;
        lastTick = System.nanoTime();
        handleRequests();

        if (closed) {
            return;
        }

        // Check for random map expiration
        if (robotTime >= randomMapExpiration) {
            generateRandomMap();
            randomMapExpiration = robotTime + expRandom(random, mapPeriod);
            mapExpiration = robotTime + expRandom(random, randomPeriod);
        }

        // Check for map expiration
        if (robotTime >= mapExpiration) {
            generateRandomMapContent();
            mapExpiration = robotTime + expRandom(random, randomPeriod);
        }

        // Behaviour controller
        handleEngine();

        // Simulate robot motion
        simulatePhysics();

        boolean frontSensor0 = frontSensor;
        boolean rearSensor0 = rearSensor;
        // Handle contacts
        handleContacts();
        // Check for sensor
        checkForSensor(frontSensor0, rearSensor0);
        // Check for movement constraints
        checkForSpeed();
        // Handles stalemate
        handleStalemate();
        // Update robot status
        if (sendMotion || robotTime >= motionTimeout) {
            sendMotion();
        }
        if (sendLidar || robotTime >= lidarTimeout) {
            sendLidar();
        }
        if (sendContacts) {
            sendContacts();
        }
        if (robotTime >= cameraTimeout) {
            sendCamera();
        }
    }

    /**
     * Simulates robot physics for interval time
     */
    private void simulatePhysics() {
        double dt = interval * 1e-3;

        // Relative left-right motor speeds
        double expectedLeftPps = leftPps;
        double expectedRightPps = rightPps;

        // Check for block
        if ((expectedLeftPps < 0 && !canMoveBackward())
                || (expectedLeftPps > 0 && !canMoveForward())) {
            expectedLeftPps = 0;
        }
        if ((expectedRightPps < 0 && !canMoveBackward())
                || (expectedRightPps > 0 && !canMoveForward())) {
            expectedRightPps = 0;
        }

        // Update the status with the speed
        leftPps = expectedLeftPps;
        rightPps = expectedRightPps;

        // left-right motor speeds (m/s)
        double left = expectedLeftPps * DISTANCE_PER_PULSE;
        double right = expectedRightPps * DISTANCE_PER_PULSE;

        // Real forward velocity (m/s)
        double forwardVelocity = (left + right) / 2;

        // target real power (jbox2d/s)
        Vec2 targetVelocity = robot.getWorldVector(Utils.vec2(forwardVelocity * JBOX_SCALE, 0));
        // Difference of power
        Vec2 dv = targetVelocity.sub(robot.getLinearVelocity());
        // Impulse to fix the power
        float mass = robot.getMass();
        Vec2 dq = dv.mul(mass);
        // Force to fix the power
        Vec2 force = dq.mul((float) (1 / dt));
        // Robot relative force
        Vec2 localForce = robot.getLocalVector(force);
        // add a random factor to force
        localForce = localForce.mul((float) (1 + random.nextGaussian() * errSensor));

        // Clip the local force to physic constraints
        localForce.x = clamp(localForce.x, (float) -MAX_FORCE, (float) MAX_FORCE);
        force = robot.getWorldVector(localForce);

        // Angle rotation due to differential motor speeds (rad/s)
        double angularVelocity1 = (right - left) / RobotSpec.ROBOT_TRACK;
        // Limits rotation to max allowed rotation (rad/s)
        double angularVelocity = clamp(angularVelocity1, -MAX_ANGULAR_VELOCITY, MAX_ANGULAR_VELOCITY);
        // Angular impulse to fix the direction
        double robotAngularVelocity = robot.getAngularVelocity();
        float inertia = robot.getInertia();
        double angularTorque = (angularVelocity - robotAngularVelocity) * inertia / dt;
        // Add a random factor to angular impulse
        angularTorque *= (1 + random.nextGaussian() * errSigma);
        // Clip the angular torque
        angularTorque = clamp(angularTorque, -MAX_TORQUE, MAX_TORQUE);
        world.clearForces();
        robot.applyForceToCenter(force);
        robot.applyTorque((float) angularTorque);
        world.step((float) dt, VELOCITY_ITER, POSITION_ITER);
    }

    @Override
    public double simulationSpeed() {
        long dt = lastTick - startSimulationTime;
        return dt > 0 ? robotTime * NANOS_PER_MILLIS / dt : 1;
    }

    /**
     * Sets the simulation time
     *
     * @param time the simulation time
     */
    public void simulationTime(long time) {
        requests.updateAndGet(r -> r.simulationTime(time));
    }

    /**
     * Starts synchronous simulation
     */
    private void startSyncSimulation() {
        Completable.fromRunnable(() -> {
                    logger.atInfo().log("Started simulation");
                    while (!closed) {
                        simulate();
                    }
                    logger.atInfo().log("Simulation completed");
                }).subscribeOn(Schedulers.io())
                .subscribe();
    }

    /**
     * Connect without starting simulation polling
     */
    void syncConnect() {
        if (!closed && !connected) {
            // Send the connection sequence
            robotLineState.onNext(new RobotLineState(true, false, false, false));
            this.startSimulationTime = System.nanoTime();
            connected = true;
            robotLineState.onNext(new RobotLineState(true, true, false, true));
            robotLineState.onNext(new RobotLineState(true, true, true, false));
            robotLineState.onNext(new RobotLineState(true, true, false, true));
            sendMotion();
            sendContacts();
            sendLidar();
            sendCamera();
        }
    }

    /**
     * Schedules the computation of status on the time interval
     */
    private void tick() {
        if (!closed) {
            Completable.timer(tickInterval, TimeUnit.MILLISECONDS)
                    .observeOn(Schedulers.computation())
                    .subscribe(() -> {
                        simulate();
                        // Reschedules the simulation
                        tick();
                    });
        } else {
            logger.atInfo().log("Simulation completed");
        }
    }

    /**
     * Contains the robot line status flags
     *
     * @param connecting  true if connecting
     * @param connected   true if connected
     * @param configuring true if configuring
     * @param configured  true if configured
     */
    record RobotLineState(boolean connecting, boolean connected, boolean configuring,
                          boolean configured) implements RobotStatusApi {
    }

    /**
     * The robot queued requests
     *
     * @param connect        true if connect request
     * @param simulationTime the simulation time (ms) valid request if >= 0
     * @param statusId       the requested status
     * @param targetDir      the target direction (DEG)
     * @param target         the target location
     * @param headDir        the head direction request
     */
    record RobotRequests(boolean connect, boolean close, long simulationTime, RobotStatusId statusId, int targetDir,
                         Point2D target, Complex headDir) {

        private static final RobotRequests EMPTY = new RobotRequests(false, false, -1, null, 0, null, null);

        /**
         *
         * Returns the empty requests
         */
        public static RobotRequests empty() {
            return EMPTY;
        }

        /**
         * Sets the rotation request
         *
         * @param target the target location
         */
        public RobotRequests backward(Point2D target) {
            return BACKWARD.equals(statusId) && Objects.equals(this.target, target)
                    ? this
                    : new RobotRequests(connect, close, simulationTime, BACKWARD, targetDir, target, headDir);
        }

        /**
         * Sets the close request
         *
         * @param close true if close request
         */
        public RobotRequests close(boolean close) {
            return this.close == close ? this : new RobotRequests(connect, close, simulationTime, statusId, targetDir, target, headDir);
        }

        /**
         * Sets the rotation request
         *
         * @param target the target location
         */
        public RobotRequests forward(Point2D target) {
            return FORWARD.equals(statusId) && Objects.equals(this.target, target)
                    ? this
                    : new RobotRequests(connect, close, simulationTime, FORWARD, targetDir, target, headDir);
        }

        public RobotRequests halt() {
            return HALT.equals(statusId)
                    ? this
                    : new RobotRequests(connect, close, simulationTime, HALT, targetDir, target, headDir);
        }

        /**
         * Sets the head direction
         *
         * @param headDir the head direction
         */
        public RobotRequests headDir(Complex headDir) {
            return Objects.equals(this.headDir, headDir)
                    ? this
                    : new RobotRequests(connect, close, simulationTime, statusId, targetDir, target, headDir);
        }

        /**
         * Sets the rotation request
         *
         * @param dir the direction (DEG)
         */
        public RobotRequests rotate(int dir) {
            return ROTATE.equals(statusId) && this.targetDir == dir
                    ? this
                    : new RobotRequests(connect, close, simulationTime, ROTATE, dir, target, headDir);
        }

        /**
         * Sets the simulation time
         *
         * @param simulationTime the simulation time (ms)
         */
        public RobotRequests simulationTime(long simulationTime) {
            return this.simulationTime == simulationTime ? this : new RobotRequests(connect, close, simulationTime, statusId, targetDir, target, headDir);
        }
    }
}
