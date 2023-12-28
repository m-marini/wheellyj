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
import org.jbox2d.callbacks.ContactImpulse;
import org.jbox2d.callbacks.ContactListener;
import org.jbox2d.collision.Manifold;
import org.jbox2d.collision.shapes.ChainShape;
import org.jbox2d.collision.shapes.PolygonShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.*;
import org.jbox2d.dynamics.contacts.Contact;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.RobotStatus.DISTANCE_PER_PULSE;
import static org.mmarini.wheelly.apis.RobotStatus.DISTANCE_SCALE;
import static org.mmarini.wheelly.apis.Utils.normalizeAngle;
import static org.mmarini.wheelly.apis.Utils.normalizeDegAngle;

/**
 * Simulated robot
 */
public class SimRobot implements RobotApi {
    public static final double GRID_SIZE = 0.2;
    public static final double WORLD_SIZE = 10;
    public static final double X_CENTER = 0;
    public static final double Y_CENTER = 0;
    public static final double ROBOT_WIDTH = 0.18;
    public static final double ROBOT_LENGTH = 0.26;
    public static final double MAX_OBSTACLE_DISTANCE = 3;
    public static final double MAX_DISTANCE = 3;
    public static final double MAX_VELOCITY = MAX_PPS * DISTANCE_PER_PULSE;
    public static final double ROBOT_TRACK = 0.136;
    private static final double MIN_OBSTACLE_DISTANCE = 1;
    private static final Vec2 GRAVITY = new Vec2();
    private static final int VELOCITY_ITER = 10;
    private static final int POSITION_ITER = 10;
    private static final double RAD_10 = toRadians(10);
    private static final double RAD_30 = toRadians(30);
    private static final double ROBOT_MASS = 0.78;
    private static final double ROBOT_DENSITY = ROBOT_MASS / ROBOT_LENGTH / ROBOT_WIDTH;
    private static final double ROBOT_FRICTION = 1;
    private static final double ROBOT_RESTITUTION = 0;
    private static final double SAFE_DISTANCE = 0.2;
    private static final double MAX_ACC = 1;
    private static final double MAX_FORCE = MAX_ACC * ROBOT_MASS;
    private static final double MAX_TORQUE = 0.7;
    private static final double SENSOR_GAP = 0.01;
    private static final double[][] FRONT_LEFT_VERTICES = {
            {SENSOR_GAP, ROBOT_WIDTH / 2 + SENSOR_GAP},
            {ROBOT_LENGTH / 2 + SENSOR_GAP, ROBOT_WIDTH / 2 + SENSOR_GAP},
            {ROBOT_LENGTH / 2 + SENSOR_GAP, SENSOR_GAP}
    };
    private static final double[][] FRONT_RIGHT_VERTICES = {
            {SENSOR_GAP, -ROBOT_WIDTH / 2 - SENSOR_GAP},
            {ROBOT_LENGTH / 2 + SENSOR_GAP, -ROBOT_WIDTH / 2 - SENSOR_GAP},
            {ROBOT_LENGTH / 2 + SENSOR_GAP, -SENSOR_GAP}
    };
    private static final double[][] REAR_LEFT_VERTICES = {
            {-SENSOR_GAP, ROBOT_WIDTH / 2 + SENSOR_GAP},
            {-ROBOT_LENGTH / 2 - SENSOR_GAP, ROBOT_WIDTH / 2 + SENSOR_GAP},
            {-ROBOT_LENGTH / 2 - SENSOR_GAP, SENSOR_GAP}
    };
    private static final double[][] REAR_RIGHT_VERTICES = {
            {-SENSOR_GAP, -ROBOT_WIDTH / 2 - SENSOR_GAP},
            {-ROBOT_LENGTH / 2 - SENSOR_GAP, -ROBOT_WIDTH / 2 - SENSOR_GAP},
            {-ROBOT_LENGTH / 2 - SENSOR_GAP, -SENSOR_GAP}
    };
    private static final Logger logger = LoggerFactory.getLogger(SimRobot.class);

    public static SimRobot create(JsonNode root, Locator locator) {
        long mapSeed = locator.path("mapSeed").getNode(root).asLong(0);
        long robotSeed = locator.path("robotSeed").getNode(root).asLong(0);
        int numObstacles = locator.path("numObstacles").getNode(root).asInt();
        double sensorReceptiveAngle = toRadians(locator.path("sensorReceptiveAngle").getNode(root).asInt());
        Random mapRandom = mapSeed > 0L ? new Random(mapSeed) : new Random();
        Random robotRandom = robotSeed > 0L ? new Random(robotSeed) : new Random();
        ObstacleMap obstacleMap = MapBuilder.create(GRID_SIZE)
                .rect(-WORLD_SIZE / 2,
                        -WORLD_SIZE / 2, WORLD_SIZE / 2, WORLD_SIZE / 2)
                .rand(numObstacles, X_CENTER, Y_CENTER, MIN_OBSTACLE_DISTANCE, MAX_OBSTACLE_DISTANCE, mapRandom)
                .build();
        double errSigma = locator.path("errSigma").getNode(root).asDouble();
        double errSensor = locator.path("errSensor").getNode(root).asDouble();
        int maxAngularSpeed = locator.path("maxAngularSpeed").getNode(root).asInt();
        long motionInterval = locator.path("motionInterval").getNode(root).asLong(500);
        long proxyInterval = locator.path("proxyInterval").getNode(root).asLong(500);
        return new SimRobot(obstacleMap,
                robotRandom,
                errSigma, errSensor,
                sensorReceptiveAngle, maxAngularSpeed, motionInterval, proxyInterval);
    }

    /**
     * Creates obstacle in the world
     *
     * @param world    thr world
     * @param location the obstacle location
     */
    protected static void createObstacle(World world, Point2D location) {
        PolygonShape obsShape = new PolygonShape();
        obsShape.setAsBox(RobotStatus.OBSTACLE_SIZE / 2, RobotStatus.OBSTACLE_SIZE / 2);

        FixtureDef obsFixDef = new FixtureDef();
        obsFixDef.shape = obsShape;

        BodyDef obsDef = new BodyDef();
        obsDef.type = BodyType.STATIC;

        obsDef.position.x = (float) location.getX();
        obsDef.position.y = (float) location.getY();
        Body obs = world.createBody(obsDef);
        obs.createFixture(obsFixDef);
    }

    /**
     * Returns a sensor fixture
     *
     * @param parentBody parent body
     * @param vertices   the vertices
     */
    private static Fixture createSensor(Body parentBody, double[][] vertices) {
        ChainShape shape = new ChainShape();
        Vec2[] vertices1 = Arrays.stream(vertices).map(Utils::vec2).toArray(Vec2[]::new);
        shape.createChain(vertices1, vertices.length);
        FixtureDef def = new FixtureDef();
        def.shape = shape;
        def.isSensor = true;
        return parentBody.createFixture(def);
    }

    private final World world;
    private final Body robot;
    private final double errSigma;
    private final double errSensor;
    private final double sensorReceptiveAngle;
    private final Random random;
    private final ObstacleMap obstacleMap;
    private final Fixture flSensor;
    private final Fixture frSensor;
    private final Fixture rlSensor;
    private final Fixture rrSensor;
    private final int maxAngularSpeed;
    private final long motionInterval;
    private final long proxyInterval;
    private long proxyTimeout;
    private int speed;
    private int direction;
    private int sensorDirection;
    private Consumer<WheellyMotionMessage> onMotion;
    private Consumer<WheellyProxyMessage> onProxy;
    private Consumer<WheellyContactsMessage> onContacts;
    private Consumer<ClockSyncEvent> onClock;
    private double leftPps;
    private double rightPps;
    private boolean frontSensor;
    private boolean rearSensor;
    private long remoteTime;
    private double echoDistance;
    private long motionTimeout;

    /**
     * Creates a simulated robot
     *
     * @param obstacleMap          the obstacle map
     * @param random               the random generator
     * @param errSigma             sigma of errors in physic simulation (U)
     * @param errSensor            sensor error (m)
     * @param sensorReceptiveAngle sensor receptive angle (DEG)
     * @param maxAngularSpeed      the maximum angular speed
     * @param motionInterval       the interval between motion messages
     * @param proxyInterval        the interval between proxy messages
     */
    public SimRobot(ObstacleMap obstacleMap, Random random, double errSigma, double errSensor, double sensorReceptiveAngle, int maxAngularSpeed, long motionInterval, long proxyInterval) {
        logger.atDebug().log("Created");
        this.random = requireNonNull(random);
        this.errSigma = errSigma;
        this.errSensor = errSensor;
        this.obstacleMap = requireNonNull(obstacleMap);
        this.sensorReceptiveAngle = sensorReceptiveAngle;
        this.maxAngularSpeed = maxAngularSpeed;
        this.motionInterval = motionInterval;
        this.proxyInterval = proxyInterval;
        this.frontSensor = this.rearSensor = true;

        // Creates the jbox2 physic world
        this.world = new World(GRAVITY);
        world.setContactListener(new ContactListener() {
            @Override
            public void beginContact(Contact contact) {
                SimRobot.this.handleBeginContact(contact);
            }

            @Override
            public void endContact(Contact contact) {
                SimRobot.this.handleEndContact(contact);
            }

            @Override
            public void postSolve(Contact contact, ContactImpulse contactImpulse) {
            }

            @Override
            public void preSolve(Contact contact, Manifold manifold) {
            }
        });

        // Creates the jbox2 physic robot body
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyType.DYNAMIC;
        bodyDef.angle = (float) (PI / 2);
        this.robot = world.createBody(bodyDef);

        // Creates the jbox2 robot fixture
        PolygonShape robotShape = new PolygonShape();
        robotShape.setAsBox((float) (ROBOT_WIDTH / 2), (float) (ROBOT_LENGTH / 2));
        FixtureDef fixDef = new FixtureDef();
        fixDef.shape = robotShape;
        fixDef.friction = (float) ROBOT_FRICTION;
        fixDef.density = (float) ROBOT_DENSITY;
        fixDef.restitution = (float) ROBOT_RESTITUTION;
        robot.createFixture(fixDef);

        // Creates the jbox2 sensor fixtures
        this.flSensor = createSensor(robot, FRONT_LEFT_VERTICES);
        this.frSensor = createSensor(robot, FRONT_RIGHT_VERTICES);
        this.rlSensor = createSensor(robot, REAR_LEFT_VERTICES);
        this.rrSensor = createSensor(robot, REAR_RIGHT_VERTICES);

        // Create obstacle fixture
        for (Point2D point : obstacleMap.getPoints()) {
            createObstacle(world, point);
        }
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
            long now = System.currentTimeMillis();
            onClock.accept(ClockSyncEvent.create(now, remoteTime, remoteTime, now));
        }
        sendMotion();
        sendProxy();
        sendContacts();
    }

    @Override
    public void connect() {
    }

    /**
     * @param dt the time interval
     */
    private void controller(double dt) {
        // Direction difference
        double dAngle = Utils.normalizeAngle(toRadians(90 - direction) - robot.getAngle());
        // Relative angular speed to fix the direction
        double angularVelocityPps = Utils.clip(Utils.linear(dAngle, -RAD_10, RAD_10, -maxAngularSpeed, maxAngularSpeed), -maxAngularSpeed, maxAngularSpeed);
        // Relative linear speed to fix the speed

        double linearVelocityPps = (double) speed * Utils.clip(Utils.linear(abs(dAngle), 0, RAD_30, MAX_PPS, 0), 0, MAX_PPS);

        // Relative left-right motor speeds
        leftPps = Utils.clip((linearVelocityPps - angularVelocityPps), -MAX_PPS, MAX_PPS);
        rightPps = Utils.clip((linearVelocityPps + angularVelocityPps), -MAX_PPS, MAX_PPS);

        // Real left-right motor speeds
        double left = leftPps * DISTANCE_PER_PULSE;
        double right = rightPps * DISTANCE_PER_PULSE;

        // Real forward velocity
        double forwardVelocity = (left + right) / 2;

        // target real speed
        Vec2 targetVelocity = robot.getWorldVector(Utils.vec2(forwardVelocity, 0));
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
        double angularVelocity = (right - left) / ROBOT_TRACK;
        // Angular impulse to fix direction
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

        // Update robot status
        updateMotion();
    }

    /**
     * Returns the robot direction (DEG)
     */
    public int getDirection() {
        return normalizeDegAngle((int) round(90 - toDegrees(robot.getAngle())));
    }

    /**
     * Returns the echo distance (m)
     */
    public double getEchoDistance() {
        return echoDistance;
    }

    /**
     * Returns the robot location (m)
     */
    public Point2D getLocation() {
        Vec2 pos = robot.getPosition();
        return new Point2D.Double(pos.x, pos.y);
    }

    /**
     * Returns the obstacles map
     */
    public Optional<ObstacleMap> getObstaclesMap() {
        return Optional.ofNullable(obstacleMap);
    }

    /**
     * Returns the sensor direction (DEG)
     */
    public int getSensorDirection() {
        return sensorDirection;
    }

    @Override
    public void halt() {
        speed = 0;
        leftPps = 0;
        rightPps = 0;
    }

    private void handleBeginContact(Contact contact) {
        if (isFront(contact)) {
            frontSensor = false;
        }
        if (isRear(contact)) {
            rearSensor = false;
        }
        sendContacts();
    }

    private void handleEndContact(Contact contact) {
        if (isFront(contact)) {
            frontSensor = true;
        }
        if (isRear(contact)) {
            rearSensor = true;
        }
        sendContacts();
    }

    /**
     * Returns true if contact is frontal
     *
     * @param contact contact
     */
    private boolean isFront(Contact contact) {
        Fixture fa = contact.m_fixtureA;
        Fixture fb = contact.m_fixtureB;
        return fa == flSensor || fb == flSensor || fa == frSensor || fb == frSensor;
    }

    /**
     * Returns true if front sensor is clear
     */
    boolean isFrontSensor() {
        return frontSensor;
    }

    /**
     * Returns true if contact is later
     *
     * @param contact contact
     */
    private boolean isRear(Contact contact) {
        Fixture fa = contact.m_fixtureA;
        Fixture fb = contact.m_fixtureB;
        return fa == rlSensor || fb == rlSensor || fa == rrSensor || fb == rrSensor;
    }

    /**
     * Returns true if rear sensor is clear
     */
    boolean isRearSensor() {
        return rearSensor;
    }

    @Override
    public void move(int dir, int speed) {
        this.direction = normalizeDegAngle(dir);
        this.speed = min(max(speed, -MAX_PPS), MAX_PPS);
        checkForSpeed();
    }

    @Override
    public void reset() {
        speed = 0;
        direction = 0;
        sensorDirection = 0;
        remoteTime = 0;
        robot.setLinearVelocity(new Vec2());
        robot.setTransform(new Vec2(), (float) (PI / 2));
        robot.setAngularVelocity(0f);
    }

    @Override
    public void scan(int dir) {
        this.sensorDirection = min(max(dir, -90), 90);
    }

    /**
     * Sends the contacts message
     */
    private void sendContacts() {
        if (onContacts != null) {
            WheellyContactsMessage msg = new WheellyContactsMessage(
                    System.currentTimeMillis(), remoteTime,
                    frontSensor, rearSensor,
                    canMoveForward(),
                    canMoveBackward()
            );
            onContacts.accept(msg);
        }
    }

    /**
     * Sends the motion message
     */
    private void sendMotion() {
        if (onMotion != null) {
            Vec2 pos = robot.getPosition();
            double xPulses = pos.x / DISTANCE_PER_PULSE;
            double yPulses = pos.y / DISTANCE_PER_PULSE;
            int robotDir = getDirection();
            WheellyMotionMessage msg = new WheellyMotionMessage(
                    System.currentTimeMillis(), remoteTime,
                    xPulses, yPulses, robotDir,
                    leftPps, rightPps,
                    0, speed == 0,
                    (int) round(leftPps), (int) round(rightPps),
                    0, 0);
            onMotion.accept(msg);
        }
        motionTimeout = remoteTime + motionInterval;
    }

    /**
     * Sends the proxy message
     */
    private void sendProxy() {
        if (onProxy != null) {
            Vec2 pos = robot.getPosition();
            double xPulses = pos.x / DISTANCE_PER_PULSE;
            double yPulses = pos.y / DISTANCE_PER_PULSE;
            int echoYaw = getDirection();
            long echoDelay = round(echoDistance / DISTANCE_SCALE);
            WheellyProxyMessage msg = new WheellyProxyMessage(
                    System.currentTimeMillis(), remoteTime,
                    sensorDirection, echoDelay, xPulses, yPulses, echoYaw);
            onProxy.accept(msg);
        }
        proxyTimeout = remoteTime + proxyInterval;
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
     * @param direction the direction in DEG
     */
    public void setRobotDir(int direction) {
        this.direction = direction;
        robot.setTransform(robot.getPosition(), (float) Utils.toNormalRadians(90 - direction));
    }

    /**
     * @param x the x coordinate
     * @param y the y coordinate
     */
    public void setRobotPos(double x, double y) {
        Vec2 pos = new Vec2();
        pos.x = (float) x;
        pos.y = (float) y;
        robot.setTransform(pos, robot.getAngle());
    }

    @Override
    public void tick(long dt) {
        this.remoteTime += dt;

        // Simulate robot motion
        controller(dt * 1e-3F);

        // Check for sensor
        Vec2 pos = robot.getPosition();
        double x = pos.x;
        double y = pos.y;
        Point2D position = new Point2D.Double(x, y);
        double sensorRad = normalizeAngle(robot.getAngle() - toRadians(sensorDirection));
        this.echoDistance = 0;
        // Finds the nearest obstacle in proxy sensor range
        int obsIdx = obstacleMap.indexOfNearest(x, y, sensorRad, sensorReceptiveAngle);
        if (obsIdx >= 0) {
            // Computes the distance of obstacles
            Point2D obs = obstacleMap.getPoint(obsIdx);
            double dist = obs.distance(position) - obstacleMap.getTopology().getGridSize() / 2
                    + random.nextGaussian() * errSensor;
            echoDistance = dist > 0 && dist < MAX_DISTANCE ? dist : 0;
        }

        // Check for movement constraints
        checkForSpeed();
        updateProxy();
    }

    @Override
    public long getRemoteTime() {
        return remoteTime;
    }

    /**
     * Sends the motion message if interval has elapsed
     */
    private void updateMotion() {
        if (remoteTime >= motionTimeout) {
            sendMotion();
        }
    }

    /**
     * Sends the proxy message if interval has elapsed
     */
    private void updateProxy() {
        if (remoteTime >= proxyTimeout) {
            sendProxy();
        }
    }
}
