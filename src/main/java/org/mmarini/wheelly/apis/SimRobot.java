/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.apis;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.RobotStatus.*;
import static org.mmarini.wheelly.apis.Utils.clip;

/**
 * Simulated robot
 */
public class SimRobot implements RobotApi {
    public static final double GRID_SIZE = 0.2;
    public static final double WORLD_SIZE = 10;
    public static final double MAX_OBSTACLE_DISTANCE = 3;
    public static final double MAX_DISTANCE = 3;
    public static final double MAX_ANGULAR_PPS = 20;
    public static final double ROBOT_TRACK = 0.136;
    public static final double MAX_ANGULAR_VELOCITY = MAX_ANGULAR_PPS * DISTANCE_PER_PULSE / ROBOT_TRACK * 2; // RAD/s
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/sim-robot-schema-1.0";
    public static final int CAMERA_HEIGHT = 240;
    public static final int CAMERA_WIDTH = 240;
    public static final String QR_CODE = "A";
    private static final int DEFAULT_MAX_ANGULAR_SPEED = 5;
    private static final long DEFAULT_MOTION_INTERVAL = 500;
    private static final long DEFAULT_PROXY_INTERVAL = 500;
    private static final double MIN_OBSTACLE_DISTANCE = 1;
    private static final Vec2 GRAVITY = new Vec2();
    private static final int VELOCITY_ITER = 10;
    private static final int POSITION_ITER = 10;
    private static final double RAD_10 = toRadians(10);
    private static final double RAD_30 = toRadians(30);
    private static final double ROBOT_MASS = 0.785;
    private static final double ROBOT_FRICTION = 1;
    private static final double ROBOT_RESTITUTION = 0;
    private static final double SAFE_DISTANCE = 0.2;
    private static final float JBOX_SCALE = 100;
    private static final double MAX_ACC = 1 * JBOX_SCALE;
    private static final double MAX_FORCE = MAX_ACC * ROBOT_MASS;
    private static final double MAX_TORQUE = 0.7 * JBOX_SCALE * JBOX_SCALE;
    private static final Logger logger = LoggerFactory.getLogger(SimRobot.class);
    private static final float ROBOT_RADIUS = 0.15f;
    private static final double ROBOT_DENSITY = ROBOT_MASS / (ROBOT_RADIUS * ROBOT_RADIUS * PI * JBOX_SCALE * JBOX_SCALE);
    private static final int DEFAULT_SENSOR_RECEPTIVE_ANGLE = 15;
    private static final long DEFAULT_STALEMATE_INTERVAL = 60000;

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
        ObstacleMap obstacleMap = createObstacleMap(numObstacles, numLabels, mapRandom, new Point2D.Double());
        double errSigma = locator.path("errSigma").getNode(root).asDouble();
        double errSensor = locator.path("errSensor").getNode(root).asDouble();
        int maxAngularSpeed = locator.path("maxAngularSpeed").getNode(root).asInt(DEFAULT_MAX_ANGULAR_SPEED);
        long motionInterval = locator.path("motionInterval").getNode(root).asLong(DEFAULT_MOTION_INTERVAL);
        long proxyInterval = locator.path("proxyInterval").getNode(root).asLong(DEFAULT_PROXY_INTERVAL);
        long changeObstaclesPeriod = locator.path("changeObstaclesPeriod").getNode(root).asLong(0);
        long stalemateInterval = locator.path("stalemateInterval").getNode(root).asLong(DEFAULT_STALEMATE_INTERVAL);

        double maxRadarDistance = locator.path("maxRadarDistance").getNode(root).asDouble();
        double contactRadius = locator.path("contactRadius").getNode(root).asDouble();
        RobotSpec robotSpec = new RobotSpec(maxRadarDistance, sensorReceptiveAngle, contactRadius);

        return new SimRobot(robotSpec, obstacleMap,
                robotRandom, mapRandom,
                errSigma, errSensor,
                maxAngularSpeed, motionInterval, proxyInterval, numObstacles, numLabels, changeObstaclesPeriod, stalemateInterval);
    }

    /**
     * Returns the obstacle map
     *
     * @param numObstacles  the number of obstacles
     * @param numLabels     the number of labels
     * @param mapRandom     the randomizer
     * @param robotLocation the robot location
     */
    private static ObstacleMap createObstacleMap(int numObstacles, int numLabels, Random mapRandom, Point2D robotLocation) {
        return MapBuilder.create(GRID_SIZE)
                .rect(false, -WORLD_SIZE / 2,
                        -WORLD_SIZE / 2, WORLD_SIZE / 2, WORLD_SIZE / 2)
                .rand(numObstacles, numLabels, new Point2D.Double(), MAX_OBSTACLE_DISTANCE,
                        robotLocation, MIN_OBSTACLE_DISTANCE, mapRandom)
                .build();
    }

    private final long proxyInterval;
    private final long cameraInterval;
    private final Random random;
    private final Random mapRandom;
    private final Body robot;
    private final Fixture robotFixture;
    private final World world;
    private final int numObstacles;
    private final int numLabels;
    private final long changeObstaclesPeriod;
    private final double errSensor;
    private final double errSigma;
    private final int maxAngularSpeed;
    private final long stalemateInterval;
    private final long motionInterval;
    private final RobotSpec robotSpec;
    private Complex direction;
    private double echoDistance;
    private boolean frontSensor;
    private double leftPps;
    private long motionTimeout;
    private Consumer<ClockSyncEvent> onClock;
    private Consumer<WheellyContactsMessage> onContacts;
    private Consumer<WheellyMotionMessage> onMotion;
    private Consumer<WheellyProxyMessage> onProxy;
    private Consumer<CameraEvent> onCamera;
    private long proxyTimeout;
    private long cameraTimeout;
    private boolean rearSensor;
    private double rightPps;
    private Complex sensorDirection;
    private long simulationTime;
    private int speed;
    private Consumer<SimRobot> onObstacleChanged;
    private Body obstacleBody;
    private ObstacleMap obstacleMap;
    private ObstacleMap.ObstacleCell nearestCell;
    private long stalemateInstant;
    private boolean stalemate;

    /**
     * Creates a simulated robot
     *
     * @param robotSpec             the robot specification
     * @param obstacleMap           the obstacle map
     * @param random                the random generator
     * @param mapRandom             the map random generator
     * @param errSigma              sigma of errors in physic simulation (U)
     * @param errSensor             sensor error (m)
     * @param maxAngularSpeed       the maximum angular speed
     * @param motionInterval        the interval between motion messages
     * @param proxyInterval         the interval between proxy messages
     * @param numObstacles          the number of obstacles
     * @param numLabels             the number of labeled obstacles
     * @param changeObstaclesPeriod the period of change obstacles
     * @param stalemateInterval     the stalemate interval (ms)
     */
    public SimRobot(RobotSpec robotSpec, ObstacleMap obstacleMap, Random random, Random mapRandom, double errSigma, double errSensor,
                    int maxAngularSpeed, long motionInterval, long proxyInterval,
                    int numObstacles, int numLabels, long changeObstaclesPeriod, long stalemateInterval) {
        this.mapRandom = mapRandom;
        this.numObstacles = numObstacles;
        this.numLabels = numLabels;
        this.changeObstaclesPeriod = changeObstaclesPeriod;
        this.stalemateInterval = stalemateInterval;
        this.robotSpec = robotSpec;
        logger.atDebug().log("Created");
        this.random = requireNonNull(random);
        this.errSigma = errSigma;
        this.errSensor = errSensor;
        this.obstacleMap = requireNonNull(obstacleMap);
        this.sensorDirection = Complex.DEG0;
        this.direction = Complex.DEG0;
        this.maxAngularSpeed = maxAngularSpeed;
        this.motionInterval = motionInterval;
        this.proxyInterval = proxyInterval;
        this.cameraInterval = 1000; // TODO initialize cameraInterval
        this.frontSensor = this.rearSensor = true;

        // Creates the jbox2 physic world
        this.world = new World(GRAVITY);

        // Creates the jbox2 physic robot body
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyType.DYNAMIC;
        bodyDef.angle = (float) (PI / 2);
        this.robot = world.createBody(bodyDef);

        CircleShape circleShape = new CircleShape();
        circleShape.setRadius(ROBOT_RADIUS * JBOX_SCALE);
        FixtureDef fixDef = new FixtureDef();
        fixDef.shape = circleShape;
        fixDef.friction = (float) ROBOT_FRICTION;
        fixDef.density = (float) ROBOT_DENSITY;
        fixDef.restitution = (float) ROBOT_RESTITUTION;
        this.robotFixture = robot.createFixture(fixDef);

        // Create obstacle bodies
        createObstacleBody();
    }

    /**
     * Returns true if robot can move backward
     */
    boolean canMoveBackward() {
        return rearSensor;
    }

    /**
     * Returns true if robot can move forward
     */
    boolean canMoveForward() {
        return frontSensor && (echoDistance == 0 || echoDistance > SAFE_DISTANCE);
    }

    /**
     * Halt the robot if it is moving in forbidden direction
     */
    private void checkForSpeed() {
        if (((speed > 0 || leftPps > 0 || rightPps > 0) && !canMoveForward())
                || ((speed < 0 || leftPps < 0 || rightPps < 0) && !canMoveBackward())) {
            halt();
        }
    }

    @Override
    public void close() {
    }

    @Override
    public void configure() {
        if (onClock != null) {
            onClock.accept(new ClockSyncEvent(simulationTime, simulationTime, simulationTime, simulationTime));
        }
        sendMotion();
        sendProxy();
        sendContacts();
    }

    @Override
    public void connect() {
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
     * @param dt the localTime interval
     */
    private void controller(double dt) {
        // Direction difference
        double dAngle = direction().sub(direction).toRad();

        // Relative angular speed to fix the direction
        double angularVelocityPps = Utils.clip(Utils.linear(dAngle, -RAD_10, RAD_10, -maxAngularSpeed, maxAngularSpeed), -maxAngularSpeed, maxAngularSpeed);
        // Relative linear speed to fix the speed

        double linearVelocityPps = speed * Utils.clip(Utils.linear(abs(dAngle), 0, RAD_30, 1, 0), 0, 1);

        // Relative left-right motor speeds
        leftPps = Utils.clip((linearVelocityPps - angularVelocityPps), -MAX_PPS, MAX_PPS);
        rightPps = Utils.clip((linearVelocityPps + angularVelocityPps), -MAX_PPS, MAX_PPS);

        // Check for block
        if ((leftPps < 0 && !canMoveBackward())
                || (leftPps > 0 && !canMoveForward())) {
            leftPps = 0;
        }
        if ((rightPps < 0 && !canMoveBackward())
                && (rightPps > 0 && !canMoveForward())) {
            rightPps = 0;
        }

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
        localForce.x = (float) Utils.clip(localForce.x, -MAX_FORCE, MAX_FORCE);
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
        angularTorque = Utils.clip(angularTorque, -MAX_TORQUE, MAX_TORQUE);
        world.clearForces();
        robot.applyForceToCenter(force);
        robot.applyTorque((float) angularTorque);
        world.step((float) dt, VELOCITY_ITER, POSITION_ITER);
        handleContacts();

        // Update robot status
        updateMotion();
    }

    /**
     * Creates the obstacle bodies
     */
    private void createObstacleBody() {
        if (obstacleBody != null) {
            world.destroyBody(obstacleBody);
            frontSensor = true;
            rearSensor = true;
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
    }

    /**
     * Returns the robot direction (DEG)
     */
    public Complex direction() {
        return Complex.fromRad(PI / 2 - robot.getAngle());
    }

    /**
     * Returns the echo distance (m)
     */
    public double echoDistance() {
        return echoDistance;
    }

    /**
     * Returns true if the front sensor is clear
     */
    boolean frontSensor() {
        return frontSensor;
    }

    @Override
    public void halt() {
        speed = 0;
        direction = direction();
        leftPps = 0;
        rightPps = 0;
    }

    /**
     * Handles contact list
     */
    private void handleContacts() {
        Contact contact = world.getContactList();
        frontSensor = rearSensor = true;
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
                        halt();
                    }
                    if (collisionDir.y() <= 0) {
                        // rear contact
                        rearSensor = false;
                        halt();
                    }
                }
            }
            contact = contact.getNext();
        }
        handleStalemate();
    }

    /**
     * Handles the stalemate status
     * Checks for stalemate and relocate the roboto in caso of stalemate timeout
     */
    private void handleStalemate() {
        if (frontSensor() || rearSensor()) {
            // no stalemate
            this.stalemate = false;
        } else if (!stalemate) {
            // First stalemate, start the timer
            stalemateInstant = simulationTime + stalemateInterval;
            this.stalemate = true;
        } else if (simulationTime >= stalemateInstant) {
            // stalemate timeout
            safeRelocateRandom();
        }
    }

    @Override
    public boolean isHalt() {
        return speed == 0;
    }

    /**
     * Returns the robot location (m)
     */
    public Point2D location() {
        Vec2 pos = robot.getPosition();
        return new Point2D.Double(pos.x / JBOX_SCALE, pos.y / JBOX_SCALE);
    }

    @Override
    public void move(Complex dir, int speed) {
        this.direction = dir;
        this.speed = min(max(speed, -MAX_PPS), MAX_PPS);
        checkForSpeed();
    }

    /**
     * Returns the obstacles map
     */
    public Optional<ObstacleMap> obstaclesMap() {
        return Optional.ofNullable(obstacleMap);
    }

    /**
     * Returns true if rear sensor is clear
     */
    boolean rearSensor() {
        return rearSensor;
    }

    @Override
    public RobotSpec robotSpec() {
        return robotSpec;
    }

    /**
     * Randomly relocates the robot
     */
    public SimRobot safeRelocateRandom() {
        double safeDistanceSq = pow(SAFE_DISTANCE + OBSTACLE_SIZE, 2);
        Point2D loc = obstaclesMap()
                .map(map -> {
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
                                        finalLoc.distanceSq(cell.location()) <= safeDistanceSq
                                )) {
                            break;
                        }
                    }
                    return loc1;
                })
                .orElse(new Point2D.Double());
        // Relocate robot
        setRobotPos(loc.getX(), loc.getY());
        return this;
    }

    @Override
    public void scan(Complex dir) {
        this.sensorDirection = dir.y() >= 0
                ? dir
                : dir.x() >= 0
                ? Complex.DEG90 : Complex.DEG270;
    }

    /**
     * Sends the camera message
     */
    private void sendCamera() {
        if (onCamera != null) {
            Point2D[] points = new Point2D[0];
            CameraEvent event = nearestCell != null && nearestCell.labeled()
                    ? new CameraEvent(simulationTime, QR_CODE, CAMERA_WIDTH, CAMERA_HEIGHT, points)
                    : CameraEvent.unknown(simulationTime);
            onCamera.accept(event);
        }
        cameraTimeout = simulationTime + cameraInterval;
    }

    /**
     * Sends the message of the contacts
     */
    private void sendContacts() {
        if (onContacts != null) {
            WheellyContactsMessage msg = new WheellyContactsMessage(
                    System.currentTimeMillis(), simulationTime, simulationTime,
                    frontSensor, rearSensor,
                    canMoveForward(),
                    canMoveBackward()
            );
            onContacts.accept(msg);
            logger.atDebug().log("On contacts {}", msg);
        }
    }

    /**
     * Sends the motion message
     */
    private void sendMotion() {
        if (onMotion != null) {
            Point2D pos = this.location();
            double xPulses = pos.getX() / DISTANCE_PER_PULSE;
            double yPulses = pos.getY() / DISTANCE_PER_PULSE;
            Complex robotDir = direction();
            WheellyMotionMessage msg = new WheellyMotionMessage(
                    System.currentTimeMillis(), simulationTime, simulationTime,
                    xPulses, yPulses, robotDir.toIntDeg(),
                    leftPps, rightPps,
                    0, speed == 0,
                    (int) round(leftPps), (int) round(rightPps),
                    0, 0);
            onMotion.accept(msg);
        }
        motionTimeout = simulationTime + motionInterval;
    }

    /**
     * Sends the proxy message
     */
    private void sendProxy() {
        if (onProxy != null) {
            Point2D pos = this.location();
            double xPulses = pos.getX() / DISTANCE_PER_PULSE;
            double yPulses = pos.getY() / DISTANCE_PER_PULSE;
            Complex echoYaw = direction();
            long echoDelay = round(echoDistance / DISTANCE_SCALE);
            WheellyProxyMessage msg = new WheellyProxyMessage(
                    System.currentTimeMillis(), simulationTime, simulationTime,
                    sensorDirection.toIntDeg(), echoDelay, xPulses, yPulses, echoYaw.toIntDeg());
            onProxy.accept(msg);
        }
        proxyTimeout = simulationTime + proxyInterval;
    }

    /**
     * Returns the sensor direction
     */
    public Complex sensorDirection() {
        return sensorDirection;
    }

    @Override
    public void setOnCamera(Consumer<CameraEvent> callback) {
        onCamera = callback;
    }

    @Override
    public void setOnClock(Consumer<ClockSyncEvent> callback) {
        onClock = callback;
    }

    @Override
    public void setOnContacts(Consumer<WheellyContactsMessage> callback) {
        this.onContacts = callback;
    }

    @Override
    public void setOnMotion(Consumer<WheellyMotionMessage> callback) {
        this.onMotion = callback;
    }

    /**
     * Sets the callback on obstacle changed
     *
     * @param callback the call back
     */
    public void setOnObstacleChanged(Consumer<SimRobot> callback) {
        onObstacleChanged = callback;
    }

    @Override
    public void setOnProxy(Consumer<WheellyProxyMessage> callback) {
        this.onProxy = callback;
    }

    @Override
    public void setOnSupply(Consumer<WheellySupplyMessage> callback) {
    }

    /**
     * Sets the robot direction
     *
     * @param direction the direction (DEG)
     */
    public void setRobotDir(Complex direction) {
        this.direction = direction;
        robot.setTransform(robot.getPosition(),
                (float) Complex.DEG90.sub(direction).toRad());
    }

    /**
     * @param x the x coordinate
     * @param y the y coordinate
     */
    public void setRobotPos(double x, double y) {
        Vec2 pos = new Vec2();
        pos.x = (float) (x * JBOX_SCALE);
        pos.y = (float) (y * JBOX_SCALE);
        robot.setTransform(pos, robot.getAngle());
    }

    /**
     * Set the sensor direction
     *
     * @param sensorDirection the sector direction
     */
    public void setSensorDirection(Complex sensorDirection) {
        this.sensorDirection = sensorDirection;
    }

    @Override
    public long simulationTime() {
        return simulationTime;
    }

    @Override
    public void tick(long dt) {
        //long t0 = System.currentTimeMillis();
        this.simulationTime += dt;

        // Change obstacles
        if (changeObstaclesPeriod > 0) {
            double lambda = 1D / changeObstaclesPeriod;
            double p = 1 - exp(-lambda * dt);
            if (mapRandom.nextDouble() < p) {
                obstacleMap = createObstacleMap(numObstacles, numLabels, mapRandom, location());
                createObstacleBody();
                if (onObstacleChanged != null) {
                    onObstacleChanged.accept(this);
                }
            }
        }

        // Simulate robot motion
        boolean prevFront = frontSensor;
        boolean prevRear = rearSensor;
        controller(dt * 1e-3F);

        // Check for sensor
        Point2D position = location();
        double x = position.getX();
        double y = position.getY();
        Complex sensorRad = direction().add(sensorDirection);
        boolean prevEchoAlarm = echoDistance > 0 && echoDistance <= SAFE_DISTANCE;
        this.echoDistance = 0;
        // Finds the nearest obstacle in proxy sensor range
        this.nearestCell = obstacleMap.nearest(x, y, sensorRad, robotSpec.receptiveAngle());
        if (nearestCell != null) {
            // Computes the distance of obstacles
            Point2D obs = nearestCell.location();
            double dist = obs.distance(position) - obstacleMap.gridSize() / 2
                    + random.nextGaussian() * errSensor;
            echoDistance = dist > 0 && dist < MAX_DISTANCE ? dist : 0;
        }
        boolean echoAlarm = echoDistance > 0 && echoDistance <= SAFE_DISTANCE;
        if (echoAlarm != prevEchoAlarm
                || prevRear != rearSensor
                || prevFront != frontSensor) {
            sendContacts();
        }
        // Check for movement constraints
        checkForSpeed();
        updateProxy();
        updateCamera();
        //logger.atDebug().log("Tick elapsed {} ms", System.currentTimeMillis() - t0);
    }

    /**
     * Sends the proxy message if the interval has elapsed
     */
    private void updateCamera() {
        if (simulationTime >= cameraTimeout) {
            sendCamera();
        }
    }

    /**
     * Sends the motion message if the interval has elapsed
     */
    private void updateMotion() {
        if (simulationTime >= motionTimeout) {
            sendMotion();
        }
    }

    /**
     * Sends the proxy message if the interval has elapsed
     */
    private void updateProxy() {
        if (simulationTime >= proxyTimeout) {
            sendProxy();
        }
    }
}
