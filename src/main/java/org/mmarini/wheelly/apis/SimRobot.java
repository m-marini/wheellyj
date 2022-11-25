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
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;

import java.awt.geom.Point2D;
import java.util.*;

import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.Utils.*;
import static org.mmarini.wheelly.apis.WheellyStatus.OBSTACLE_SIZE;
import static org.mmarini.yaml.schema.Validator.*;

/**
 * Simulated robot
 */
public class SimRobot implements RobotApi {
    public static final float GRID_SIZE = 0.2F;
    public static final float WORLD_SIZE = 10;
    public static final float X_CENTER = 0;
    public static final float Y_CENTER = 0;
    public static final float ROBOT_WIDTH = 0.18F;
    public static final float ROBOT_LENGTH = 0.26F;
    public static final float MAX_OBSTACLE_DISTANCE = 3;
    public static final float MAX_DISTANCE = 3;
    public static final int FORWARD_PROXIMITY_MASK = 0xc;
    public static final int BACKWARD_PROXIMITY_MASK = 0x3;
    public static final float MAX_VELOCITY = 0.280F;
    private static final float MIN_OBSTACLE_DISTANCE = 1;
    private static final Vec2 GRAVITY = new Vec2();
    private static final int VELOCITY_ITER = 10;
    private static final int POSITION_ITER = 10;
    private static final float RAD_10 = (float) toRadians(10);
    private static final float RAD_15 = (float) toRadians(15);
    private static final float RAD_30 = (float) toRadians(30);
    private static final float ROBOT_TRACK = 0.136F;
    private static final float ROBOT_MASS = 0.78F;
    private static final float ROBOT_DENSITY = ROBOT_MASS / ROBOT_LENGTH / ROBOT_WIDTH;
    private static final float ROBOT_FRICTION = 1;
    private static final float ROBOT_RESTITUTION = 0;
    private static final float SAFE_DISTANCE = 0.2F;
    private static final float MAX_ACC = 1;
    private static final float MAX_FORCE = MAX_ACC * ROBOT_MASS;
    private static final float MAX_TORQUE = 0.7F;
    private static final float SENSOR_GAP = 0.01F;
    private static final float[][] FRONT_LEFT_VERTICES = {
            {SENSOR_GAP, ROBOT_WIDTH / 2 + SENSOR_GAP},
            {ROBOT_LENGTH / 2 + SENSOR_GAP, ROBOT_WIDTH / 2 + SENSOR_GAP},
            {ROBOT_LENGTH / 2 + SENSOR_GAP, SENSOR_GAP}
    };
    private static final float[][] FRONT_RIGHT_VERTICES = {
            {SENSOR_GAP, -ROBOT_WIDTH / 2 - SENSOR_GAP},
            {ROBOT_LENGTH / 2 + SENSOR_GAP, -ROBOT_WIDTH / 2 - SENSOR_GAP},
            {ROBOT_LENGTH / 2 + SENSOR_GAP, -SENSOR_GAP}
    };
    private static final float[][] REAR_LEFT_VERTICES = {
            {-SENSOR_GAP, ROBOT_WIDTH / 2 + SENSOR_GAP},
            {-ROBOT_LENGTH / 2 - SENSOR_GAP, ROBOT_WIDTH / 2 + SENSOR_GAP},
            {-ROBOT_LENGTH / 2 - SENSOR_GAP, SENSOR_GAP}
    };
    private static final float[][] REAR_RIGHT_VERTICES = {
            {-SENSOR_GAP, -ROBOT_WIDTH / 2 - SENSOR_GAP},
            {-ROBOT_LENGTH / 2 - SENSOR_GAP, -ROBOT_WIDTH / 2 - SENSOR_GAP},
            {-ROBOT_LENGTH / 2 - SENSOR_GAP, -SENSOR_GAP}
    };
    private static final Validator ROBOT_SPEC = objectPropertiesRequired(Map.of(
                    "robotSeed", positiveInteger(),
                    "mapSeed", positiveInteger(),
                    "errSigma", nonNegativeNumber(),
                    "errSensor", nonNegativeNumber(),
                    "numObstacles", nonNegativeInteger(),
                    "radarWidth", positiveInteger(),
                    "radarHeight", positiveInteger(),
                    "radarGrid", positiveNumber(),
                    "radarCleanInterval", positiveInteger(),
                    "radarPersistence", positiveInteger()
            ), List.of(
                    "errSigma",
                    "errSensor",
                    "numObstacles",
                    "radarWidth",
                    "radarHeight",
                    "radarGrid",
                    "radarCleanInterval",
                    "radarPersistence"
            )
    );

    public static SimRobot create(JsonNode root, Locator locator) {
        ROBOT_SPEC.apply(locator).accept(root);
        long mapSeed = locator.path("mapSeed").getNode(root).asLong(0);
        long robotSeed = locator.path("robotSeed").getNode(root).asLong(0);
        int numObstacles = locator.path("numObstacles").getNode(root).asInt();
        Random mapRandom = mapSeed > 0L ? new Random(mapSeed) : new Random();
        Random robotRandom = robotSeed > 0L ? new Random(robotSeed) : new Random();
        ObstacleMap obstacleMap = MapBuilder.create(GRID_SIZE)
                .rect(-WORLD_SIZE / 2,
                        -WORLD_SIZE / 2, WORLD_SIZE / 2, WORLD_SIZE / 2)
                .rand(numObstacles, X_CENTER, Y_CENTER, MIN_OBSTACLE_DISTANCE, MAX_OBSTACLE_DISTANCE, mapRandom)
                .build();
        float errSigma = (float) locator.path("errSigma").getNode(root).asDouble();
        float errSensor = (float) locator.path("errSensor").getNode(root).asDouble();
        int radarWidth = locator.path("radarWidth").getNode(root).asInt();
        int radarHeight = locator.path("radarHeight").getNode(root).asInt();
        float radarGrid = (float) locator.path("radarGrid").getNode(root).asDouble();
        long radarCleanInterval = locator.path("radarCleanInterval").getNode(root).asLong();
        long radarPersistence = locator.path("radarPersistence").getNode(root).asLong();
        RadarMap radarMap = RadarMap.create(radarWidth, radarHeight, new Point2D.Float(), radarGrid);
        return new SimRobot(obstacleMap,
                robotRandom,
                errSigma,
                errSensor, radarMap, radarPersistence, radarCleanInterval);
    }

    protected static void createObstacle(World world, Point2D location) {
        PolygonShape obsShape = new PolygonShape();
        obsShape.setAsBox(OBSTACLE_SIZE / 2, OBSTACLE_SIZE / 2);

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
    private static Fixture createSensor(Body parentBody, float[][] vertices) {
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
    private final float errSigma;
    private final float errSensor;
    private final Random random;
    private final ObstacleMap obstacleMap;
    private final Fixture flSensor;
    private final Fixture frSensor;
    private final Fixture rlSensor;
    private final Fixture rrSensor;
    private final long radarPersistence;
    private final long cleanInterval;
    private final WheellyStatus status;
    private float speed;
    private int direction;
    private int sensor;
    private long cleanTimeout;

    /**
     * Creates a simulated robot
     *
     * @param obstacleMap      the obstacle map
     * @param random           the random generator
     * @param errSigma         sigma of errors in physic simulation
     * @param errSensor        sensor error in meters
     * @param radarMap         the radar map
     * @param radarPersistence the radar persistence map
     * @param cleanInterval    the radar clean interval
     */
    public SimRobot(ObstacleMap obstacleMap, Random random, float errSigma, float errSensor, RadarMap radarMap, long radarPersistence, long cleanInterval) {
        this.random = requireNonNull(random);
        this.errSigma = errSigma;
        this.errSensor = errSensor;
        this.obstacleMap = obstacleMap;
        this.status = WheellyStatus.create();
        status.setRadarMap(radarMap);
        this.radarPersistence = radarPersistence;
        this.cleanInterval = cleanInterval;

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
        robotShape.setAsBox(ROBOT_WIDTH / 2, ROBOT_LENGTH / 2);
        FixtureDef fixDef = new FixtureDef();
        fixDef.shape = robotShape;
        fixDef.friction = ROBOT_FRICTION;
        fixDef.density = ROBOT_DENSITY;
        fixDef.restitution = ROBOT_RESTITUTION;
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
     *
     */
    private void checkForSpeed() {
        double left = status.getLeftSpeed();
        double right = status.getRightSpeed();
        if (((speed > 0 || left > 0 || right > 0) && !status.getCanMoveForward())
                || ((speed < 0 || left < 0 || right < 0) && !status.getCanMoveBackward())) {
            halt();
        }
    }

    @Override
    public void close() {
    }

    /**
     * @param dt the time interval
     */
    private void controller(float dt) {
        // Direction difference
        float dAngle = (float) normalizeAngle(toRadians(90 - direction) - robot.getAngle());
        // Relative angular speed to fix the direction
        float angularVelocity = clip(linear(dAngle, -RAD_10, RAD_10, -1, 1), -1, 1);
        // Relative linear speed to fix the speed
        float linearVelocity = speed * clip(linear(abs(dAngle), 0, RAD_30, 1, 0), 0, 1);

        // Relative left-right motor speeds
        float left = clip((linearVelocity - angularVelocity) / 2, -1, 1);
        float right = clip((linearVelocity + angularVelocity) / 2, -1, 1);

        // Real left-right motor speeds
        left = round(left * 10F) / 10F * MAX_VELOCITY;
        right = round(right * 10) / 10F * MAX_VELOCITY;

        // Real forward velocity
        float forwardVelocity = (left + right) / 2;

        // target real speed
        Vec2 targetVelocity = robot.getWorldVector(Utils.vec2(forwardVelocity, 0));
        // Difference of speed
        Vec2 dv = targetVelocity.sub(robot.getLinearVelocity());
        // Impulse to fix the speed
        Vec2 dq = dv.mul(robot.getMass());
        // Force to fix the speed
        Vec2 force = dq.mul(1 / dt);
        // Robot relative force
        Vec2 localForce = robot.getLocalVector(force);
        // add a random factor to force
        localForce = localForce.mul((float) (1 + random.nextGaussian() * errSensor));

        // Clip the local force to physic constraints
        localForce.x = clip(localForce.x, -MAX_FORCE, MAX_FORCE);
        force = robot.getWorldVector(localForce);

        // Angle rotation due to differential motor speeds
        angularVelocity = (right - left) / ROBOT_TRACK;
        // Angular impulse to fix direction
        float robotAngularVelocity = robot.getAngularVelocity();
        float angularTorque = (angularVelocity - robotAngularVelocity) * robot.getInertia() / dt;
        // Add a random factor to angular impulse
        angularTorque *= (1 + random.nextGaussian() * errSigma);
        // Clip the angular torque
        angularTorque = clip(angularTorque, -MAX_TORQUE, MAX_TORQUE);
        world.clearForces();
        robot.applyForceToCenter(force);
        robot.applyTorque(angularTorque);
        world.step(dt, VELOCITY_ITER, POSITION_ITER);

        // Update robot status
        Vec2 pos = robot.getPosition();
        Point2D.Float location = new Point2D.Float(pos.x, pos.y);
        status.setLocation(location);
        int direction = normalizeDegAngle((int) round(90 - toDegrees(robot.getAngle())));
        status.setDirection(direction);
        status.setLeftSpeed(left);
        status.setRightSpeed(right);
    }

    private int decodeContact(Contact contact) {
        Fixture fa = contact.m_fixtureA;
        Fixture fb = contact.m_fixtureB;
        if (fa == flSensor || fb == flSensor) {
            return 8;
        } else if (fa == frSensor || fb == frSensor) {
            return 4;
        } else if (fa == rlSensor || fb == rlSensor) {
            return 2;
        } else if (fa == rrSensor || fb == rrSensor) {
            return 1;
        } else {
            return 0;
        }
    }

    public Optional<ObstacleMap> getObstaclesMap() {
        return Optional.ofNullable(obstacleMap);
    }

    @Override
    public WheellyStatus getStatus() {
        return status;
    }

    @Override
    public void halt() {
        speed = 0;
        status.setLeftSpeed(0);
        status.setRightSpeed(0);
    }

    private void handleBeginContact(Contact contact) {
        status.setProximity(status.getProximity() | decodeContact(contact));
    }

    private void handleEndContact(Contact contact) {
        status.setProximity(status.getProximity() & ~decodeContact(contact));
    }

    @Override
    public void move(int dir, float speed) {
        this.direction = dir;
        this.speed = speed;
        checkForSpeed();
    }

    @Override
    public void reset() {
        speed = 0f;
        direction = 0;
        sensor = 0;

        status.setResetTime(status.getTime());
        status.setLocation(new Point2D.Float());
        status.setDirection(0);
        status.setSensorDirection(0);
        status.setSampleDistance(0);
        status.setProximity(0);
        status.setLeftSpeed(0);
        status.setRightSpeed(0);
        status.setCanMoveForward(true);
        status.setCanMoveBackward(true);
        status.setHalt(true);
        status.setImuFailure(false);

        robot.setLinearVelocity(new Vec2());
        robot.setTransform(new Vec2(), (float) (PI / 2));
        robot.setAngularVelocity(0f);
    }

    @Override
    public void scan(int dir) {
        this.sensor = dir;
        status.setSensorDirection(dir);
    }

    /**
     * Sets the robot direction
     *
     * @param direction the direction in DEG
     */
    public void setRobotDir(int direction) {
        this.direction = direction;
        robot.setTransform(robot.getPosition(), (float) toNormalRadians(90 - direction));
    }

    /**
     * @param x the x coordinate
     * @param y the y coordinate
     */
    public void setRobotPos(float x, float y) {
        Vec2 pos = new Vec2();
        pos.x = x;
        pos.y = y;
        robot.setTransform(pos, robot.getAngle());
        status.setLocation(new Point2D.Float(x, y));
    }

    @Override
    public void start() {
    }

    @Override
    public void tick(long dt) {
        controller(dt * 1e-3F);
        status.setTime(status.getTime() + dt);

        // Check for sensor
        Point2D position = status.getLocation();
        float x = (float) position.getX();
        float y = (float) position.getY();
        int sensorDeg = normalizeDegAngle(90 - status.getDirection() - sensor);
        float sensorRad = (float) toRadians(sensorDeg);
        float distance = 0;
        int obsIdx = obstacleMap.indexOfNearest(x, y, sensorRad, RAD_15);
        if (obsIdx >= 0) {
            Point2D obs = obstacleMap.getPoint(obsIdx);
            float dist = (float) (obs.distance(position) - obstacleMap.getTopology().getGridSize() / 2
                    + random.nextGaussian() * errSensor);
            distance = dist > 0 && dist < MAX_DISTANCE ? dist : 0;
        }
        status.setSampleDistance(distance);

        // Check for movement constraints
        boolean canMoveForward = (distance == 0 || distance > SAFE_DISTANCE) && (status.getProximity() & FORWARD_PROXIMITY_MASK) == 0;
        status.setCanMoveForward(canMoveForward);
        boolean canMoveBackward = (status.getProximity() & BACKWARD_PROXIMITY_MASK) == 0;
        status.setCanMoveBackward(canMoveBackward);
        checkForSpeed();

        // Check for radar map
        RadarMap radarMap = status.getRadarMap();
        if (radarMap != null) {
            long time = status.getTime();
            RadarMap.SensorSignal signal = new RadarMap.SensorSignal(status.getLocation(), normalizeDegAngle(status.getDirection() + status.getSensorDirection()), distance, time);
            radarMap.update(signal);
            if (time >= cleanTimeout) {
                radarMap.clean(time - radarPersistence);
                cleanTimeout = time + cleanInterval;
            }
            status.setRadarMap(radarMap);
        }
    }
}
