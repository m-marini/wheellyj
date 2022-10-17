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
import org.mmarini.wheelly.model.Utils;
import org.mmarini.wheelly.model.WheellyStatus;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.model.Utils.*;
import static org.mmarini.yaml.schema.Validator.*;

/**
 * Simulated robot
 */
public class SimRobot implements RobotApi {
    public static final float GRID_SIZE = 0.2F;
    public static final float WORLD_SIZE = 10;
    public static final float X_CENTER = 0;
    public static final float Y_CENTER = 0;
    public static final double DEFAULT_ERR_SIGMA = 0.05;
    public static final double DEFAULT_ERR_SENSOR = 0.05;
    public static final float ROBOT_WIDTH = 0.18F;
    public static final float ROBOT_LENGTH = 0.26F;
    private static final float MIN_OBSTACLE_DISTANCE = 1;
    private static final float MAX_OBSTACLE_DISTANCE = 3;
    private static final Vec2 GRAVITY = new Vec2();
    private static final int VELOCITY_ITER = 10;
    private static final int POSITION_ITER = 10;
    private static final float RAD_10 = (float) toRadians(10);
    private static final float RAD_30 = (float) toRadians(30);
    private static final float ROBOT_TRACK = 0.136F;
    private static final float ROBOT_MASS = 0.78F;
    private static final float ROBOT_DENSITY = ROBOT_MASS / ROBOT_LENGTH / ROBOT_WIDTH;
    private static final float ROBOT_FRICTION = 1;
    private static final float ROBOT_RESTITUTION = 0;
    private static final float SAFE_DISTANCE = 0.2F;
    private static final float MAX_VELOCITY = 0.280F;
    private static final float MAX_ACC = 1;
    private static final float MAX_FORCE = MAX_ACC * ROBOT_MASS;
    private static final float MAX_TORQUE = 0.7F;
    private static final float MAX_DISTANCE = 3;
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
    private static final Validator ROBOT_SPEC = objectProperties(Map.of(
                    "robotSeed", positiveInteger(),
                    "mapSeed", positiveInteger(),
                    "errSigma", nonNegativeNumber(),
                    "errSensor", nonNegativeNumber(),
                    "numObstacles", nonNegativeInteger()
            )
    );

    public static SimRobot create(JsonNode root, Locator locator) {
        ROBOT_SPEC.apply(locator).accept(root);
        long mapSeed = locator.path("mapSeed").getNode(root).asLong(0);
        long robotSeed = locator.path("robotSeed").getNode(root).asLong(0);
        int numObstacles = locator.path("numObstacles").getNode(root).asInt(0);
        Random mapRandom = mapSeed > 0L ? new Random(mapSeed) : new Random();
        Random robotRandom = robotSeed > 0L ? new Random(robotSeed) : new Random();
        ObstacleMap obstacleMap = MapBuilder.create(GRID_SIZE)
                .rect(-WORLD_SIZE / 2,
                        -WORLD_SIZE / 2, WORLD_SIZE / 2, WORLD_SIZE / 2)
                .rand(numObstacles, X_CENTER, Y_CENTER, MIN_OBSTACLE_DISTANCE, MAX_OBSTACLE_DISTANCE, mapRandom)
                .build();
        float errSigma = (float) locator.path("errSigma").getNode(root).asDouble(DEFAULT_ERR_SIGMA);
        float errSensor = (float) locator.path("errSensor").getNode(root).asDouble(DEFAULT_ERR_SENSOR);
        return new SimRobot(obstacleMap,
                robotRandom,
                errSigma,
                errSensor);
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
    private float speed;
    private float left;
    private float right;
    private int direction;
    private int sensor;
    private float distance;
    private int contacts;
    private boolean canMoveForward;
    private boolean canMoveBackward;
    private long time;
    private long resetTime;

    /**
     * Creates a simulated robot
     *
     * @param obstacleMap the obstacle map
     * @param random      the random generator
     * @param errSigma    sigma of errors in physic simulation
     * @param errSensor   sensor error in meters
     */
    public SimRobot(ObstacleMap obstacleMap, Random random, float errSigma, float errSensor) {
        this.random = requireNonNull(random);
        this.errSigma = errSigma;
        this.errSensor = errSensor;
        this.obstacleMap = obstacleMap;

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
        if (((speed > 0 || left > 0 || right > 0) && !canMoveForward)
                || ((speed < 0 || left < 0 || right < 0) && !canMoveBackward)) {
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
        left = clip((linearVelocity - angularVelocity) / 2, -1, 1);
        right = clip((linearVelocity + angularVelocity) / 2, -1, 1);

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

        // Clip the local force to physic contraints
        localForce.x = clip(localForce.x, -MAX_FORCE, MAX_FORCE);
        force = robot.getWorldVector(localForce);

        // Angle rotation due to differential motor speeds
        angularVelocity = (right - left) / ROBOT_TRACK;
        // Angular impule to fix direction
        float robotAngularVelocity = robot.getAngularVelocity();
        float angularTorque = (angularVelocity - robotAngularVelocity) * robot.getInertia() / dt;
        // Add a random factor to angulare impulse
        angularTorque *= (1 + random.nextGaussian() * errSigma);
        // Clip the angular torque
        angularTorque = clip(angularTorque, -MAX_TORQUE, MAX_TORQUE);
        world.clearForces();
        robot.applyForceToCenter(force);
        robot.applyTorque(angularTorque);
        world.step(dt, VELOCITY_ITER, POSITION_ITER);
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

    @Override
    public boolean getCanMoveBackward() {
        return canMoveBackward;
    }

    @Override
    public boolean getCanMoveForward() {
        return canMoveForward;
    }

    @Override
    public int getContacts() {
        return contacts;
    }

    @Override
    public long getElapsed() {
        return time - resetTime;
    }

    @Override
    public Optional<ObstacleMap> getObstaclesMap() {
        return Optional.ofNullable(obstacleMap);
    }

    @Override
    public int getRobotDir() {
        return (int) round(toDegrees(normalizeAngle(PI / 2 - robot.getAngle())));
    }

    /**
     * Sets the robot direction
     *
     * @param direction the direction in DEG
     */
    public void setRobotDir(int direction) {
        robot.setTransform(robot.getPosition(), (float) toNormalRadians(90 - direction));
    }

    @Override
    public Point2D getRobotPos() {
        Vec2 pos = robot.getPosition();
        return new Point2D.Float(pos.x, pos.y);
    }

    @Override
    public int getSensorDir() {
        return sensor;
    }

    @Override
    public float getSensorDistance() {
        return distance;
    }

    @Override
    public WheellyStatus getStatus() {
        return WheellyStatus.create(getRobotPos(),
                getRobotDir(), sensor, getSensorDistance(), left, right, contacts, 0,
                canMoveForward, canMoveBackward, false,
                left == 0 && right == 0, direction, speed, sensor
        );
    }

    @Override
    public long getTime() {
        return time;
    }

    @Override
    public void halt() {
        direction = getRobotDir();
        speed = 0;
    }

    private void handleBeginContact(Contact contact) {
        int value = decodeContact(contact);
        contacts |= value;
    }

    private void handleEndContact(Contact contact) {
        int value = decodeContact(contact);
        contacts &= ~value;
    }

    @Override
    public void move(int dir, float speed) {
        direction = dir;
        this.speed = speed;
        checkForSpeed();
    }

    @Override
    public void reset() {
        speed = left = right = 0f;
        direction = sensor = 0;
        distance = 0f;
        contacts = 0;
        canMoveForward = canMoveBackward = true;
        resetTime = time;
        robot.setLinearVelocity(new Vec2());
        robot.setTransform(new Vec2(), (float) (PI / 2));
        robot.setAngularVelocity(0f);
    }

    @Override
    public void scan(int dir) {
        sensor = dir;
    }

    /**
     * @param x
     * @param y
     */
    public void setRobotPos(float x, float y) {
        Vec2 pos = new Vec2();
        pos.x = x;
        pos.y = y;
        robot.setTransform(pos, robot.getAngle());
    }

    @Override
    public void start() {
    }

    @Override
    public void tick(long dt) {
        controller(dt * 1e-3F);
        time += dt;

        int sensorDeg = normalizeDegAngle(90 - getRobotDir() - sensor);
        float sensorRad = (float) toRadians(sensorDeg);
        Point2D position = getRobotPos();

        float x = (float) position.getX();
        float y = (float) position.getY();
        int obsIdx = obstacleMap.indexOfNearest(x, y, sensorRad, RAD_30);
        if (obsIdx >= 0) {
            Point2D obs = obstacleMap.getPoint(obsIdx);
            float dist = (float) obs.distance(position);
            dist = clip(dist - obstacleMap.getTopology().getGridSize() / 2, 0, 3);
            distance = dist < MAX_DISTANCE
                    ? clip(dist + (float) random.nextGaussian() * errSensor, 0, MAX_DISTANCE)
                    : 0;
        } else {
            distance = 0;
        }
        canMoveForward = (distance == 0 || distance > SAFE_DISTANCE) && (contacts & 0xc) == 0;
        canMoveBackward = (contacts & 0x3) == 0;
        checkForSpeed();
    }
}
