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
import static org.mmarini.wheelly.apis.Utils.normalizeDegAngle;

/**
 * Simulated robot
 */
public class SimRobot implements RobotApi, WithRobotStatus {
    public static final double GRID_SIZE = 0.2;
    public static final double WORLD_SIZE = 10;
    public static final double X_CENTER = 0;
    public static final double Y_CENTER = 0;
    public static final double ROBOT_WIDTH = 0.18;
    public static final double ROBOT_LENGTH = 0.26;
    public static final double MAX_OBSTACLE_DISTANCE = 3;
    public static final double MAX_DISTANCE = 3;
    public static final int FORWARD_PROXIMITY_MASK = 0xc;
    public static final int BACKWARD_PROXIMITY_MASK = 0x3;
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
    private static final long THRESHOLD_TIME = 5;
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
        double maxSimSpeed = locator.path("maxSimulationSpeed").getNode(root).asDouble();
        int maxAngularSpeed = locator.path("maxAngularSpeed").getNode(root).asInt();
        return new SimRobot(obstacleMap,
                robotRandom,
                errSigma, errSensor,
                sensorReceptiveAngle, maxAngularSpeed, maxSimSpeed);
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
    private final double maxSimSpeed;
    private final int maxAngularSpeed;
    private RobotStatus status;
    private int speed;
    private int direction;
    private int sensor;
    private Consumer<RobotStatus> onStatusReady;

    /**
     * Creates a simulated robot
     *
     * @param obstacleMap          the obstacle map
     * @param random               the random generator
     * @param errSigma             sigma of errors in physic simulation (U)
     * @param errSensor            sensor error (m)
     * @param sensorReceptiveAngle sensor receptive angle (DEG)
     * @param maxAngularSpeed      the maximum angular speed
     * @param maxSimSpeed          the maximum simulation speed
     */
    public SimRobot(ObstacleMap obstacleMap, Random random, double errSigma, double errSensor, double sensorReceptiveAngle, int maxAngularSpeed, double maxSimSpeed) {
        this.random = requireNonNull(random);
        this.errSigma = errSigma;
        this.errSensor = errSensor;
        this.obstacleMap = requireNonNull(obstacleMap);
        this.sensorReceptiveAngle = sensorReceptiveAngle;
        this.maxAngularSpeed = maxAngularSpeed;
        this.maxSimSpeed = maxSimSpeed;
        this.status = RobotStatus.create(x -> 12d);

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
     * Halt the robot if it is moving in forbidden direction
     */
    private void checkForSpeed() {
        double left = status.getLeftPps();
        double right = status.getRightPps();
        if (((speed > 0 || left > 0 || right > 0) && !status.canMoveForward())
                || ((speed < 0 || left < 0 || right < 0) && !status.canMoveBackward())) {
            halt();
        }
    }

    @Override
    public void close() {
    }

    @Override
    public void configure() {
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
        int leftPps = (int) round(Utils.clip((linearVelocityPps - angularVelocityPps), -MAX_PPS, MAX_PPS));
        int rightPps = (int) round(Utils.clip((linearVelocityPps + angularVelocityPps), -MAX_PPS, MAX_PPS));

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
        Vec2 pos = robot.getPosition();
        Point2D.Float location = new Point2D.Float(pos.x, pos.y);
        int direction = normalizeDegAngle((int) round(90 - toDegrees(robot.getAngle())));
        status = status.setLocation(location)
                .setDirection(direction)
                .setLeftPps(leftPps)
                .setRightPps(rightPps);
    }

    /**
     * Returns the decoded contact
     *
     * @param contact the jbox contact
     */
    private int decodeContact(Contact contact) {
        Fixture fa = contact.m_fixtureA;
        Fixture fb = contact.m_fixtureB;
        if (fa == flSensor || fb == flSensor) {
            return RobotStatus.FRONT_LEFT;
        } else if (fa == frSensor || fb == frSensor) {
            return RobotStatus.FRONT_RIGHT;
        } else if (fa == rlSensor || fb == rlSensor) {
            return RobotStatus.REAR_LEFT;
        } else if (fa == rrSensor || fb == rrSensor) {
            return RobotStatus.REAR_RIGHT;
        } else {
            return RobotStatus.NO_CONTACT;
        }
    }

    public Optional<ObstacleMap> getObstaclesMap() {
        return Optional.ofNullable(obstacleMap);
    }

    @Override
    public RobotStatus getRobotStatus() {
        return status;
    }

    @Override
    public void halt() {
        speed = 0;
        status = status.setLeftPps(0)
                .setRightPps(0);
    }

    private void handleBeginContact(Contact contact) {
        status = status.setContacts(status.getContacts() | decodeContact(contact));
    }

    private void handleEndContact(Contact contact) {
        status = status.setContacts(status.getContacts() & ~decodeContact(contact));
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
        sensor = 0;

        status = status.setResetTime(status.getTime())
                .setLocation(new Point2D.Float())
                .setDirection(0)
                .setSensorDirection(0)
                .setEchoDistance(0)
                .setContacts(0)
                .setLeftPps(0)
                .setRightPps(0)
                .setCanMoveForward(true)
                .setCanMoveBackward(true)
                .setHalt(true)
                .setImuFailure(0);

        robot.setLinearVelocity(new Vec2());
        robot.setTransform(new Vec2(), (float) (PI / 2));
        robot.setAngularVelocity(0f);
    }

    @Override
    public void scan(int dir) {
        this.sensor = min(max(dir, -90), 90);
        status = status.setSensorDirection(dir);
    }

    @Override
    public void setOnStatusReady(Consumer<RobotStatus> callback) {
        onStatusReady = callback;
    }

    /**
     * Sets the robot direction
     *
     * @param direction the direction in DEG
     */
    public void setRobotDir(int direction) {
        this.direction = direction;
        status = status.setDirection(direction);
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
        status = status.setLocation(new Point2D.Double(x, y));
    }

    @Override
    public void tick(long dt) {
        long start = System.currentTimeMillis();
        controller(dt * 1e-3F);
        status = status.setTime(status.getTime() + dt);

        // Check for sensor
        Point2D position = status.getLocation();
        double x = position.getX();
        double y = position.getY();
        int sensorDeg = normalizeDegAngle(90 - status.getDirection() - sensor);
        double sensorRad = toRadians(sensorDeg);
        double distance = 0;
        int obsIdx = obstacleMap.indexOfNearest(x, y, sensorRad, sensorReceptiveAngle);
        if (obsIdx >= 0) {
            Point2D obs = obstacleMap.getPoint(obsIdx);
            double dist = obs.distance(position) - obstacleMap.getTopology().getGridSize() / 2
                    + random.nextGaussian() * errSensor;
            distance = dist > 0 && dist < MAX_DISTANCE ? dist : 0;
        }
        status = status.setEchoDistance(distance);

        // Check for movement constraints
        boolean canMoveForward = (distance == 0 || distance > SAFE_DISTANCE) && (status.getContacts() & FORWARD_PROXIMITY_MASK) == 0;
        status = status.setCanMoveForward(canMoveForward);
        boolean canMoveBackward = (status.getContacts() & BACKWARD_PROXIMITY_MASK) == 0;
        status = status.setCanMoveBackward(canMoveBackward);
        checkForSpeed();
        if (onStatusReady != null) {
            onStatusReady.accept(status);
        }
        long elapse = System.currentTimeMillis() - start;
        long expected = round(dt / maxSimSpeed);
        long remainder = expected - elapse;
        if (remainder > THRESHOLD_TIME) {
            try {
                Thread.sleep(remainder);
            } catch (InterruptedException e) {
                logger.atError().setCause(e).log();
            }
        }
    }
}
