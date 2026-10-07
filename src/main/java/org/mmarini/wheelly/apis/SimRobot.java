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
import static org.mmarini.wheelly.apis.HeadStatus.HeadStatusId.FIX_DIRECTION;
import static org.mmarini.wheelly.apis.Obstacle.DEFAULT_OBSTACLE_RADIUS;
import static org.mmarini.wheelly.apis.RobotSpec.*;
import static org.mmarini.wheelly.apis.RobotStatus.OBSTACLE_SIZE;
import static org.mmarini.wheelly.apis.Utils.expRandom;
import static org.mmarini.wheelly.apis.Utils.m2mm;


/**
 * Simulates the robot behaviour and its interactions with the surrounding physical environment.
 * This class coordinates physical translations using the JBox2D physics engine framework, managing
 * kinematics simulation, proximity sensor ranges, bumper contact boundaries, and real-time odometry telemetry.
 */
public class SimRobot implements RobotApi {

    /**
     * The target schema identifier path definition utilised for configuration object structure validations.
     */
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/sim-robot-schema-3.3";

    /**
     * The default boundary size limit of the simulated world square area layout measured in metres (m).
     */
    public static final double DEFAULT_WORLD_SIZE = 10;

    /**
     * The maximum target angular velocity limit speed component configured in pulses per second (pps).
     */
    public static final double MAX_ANGULAR_PPS = 20;

    /**
     * The maximum angular velocity translation threshold calculated in radians per second (RAD/s).
     */
    public static final double MAX_ANGULAR_VELOCITY = MAX_ANGULAR_PPS * DISTANCE_PER_PULSE / RobotSpec.ROBOT_TRACK * 2; // RAD/s

    /**
     * The safe deceleration distance threshold baseline boundary value measured in metres (m).
     */
    public static final double SAFE_DISTANCE = 0.2;

    /**
     * The targeted vertical image resolution height parameter configured for the camera sensor matrix.
     */
    public static final int CAMERA_HEIGHT = 240;

    /**
     * The targeted horizontal image resolution width parameter configured for the camera sensor matrix.
     */
    public static final int CAMERA_WIDTH = 240;

    /**
     * The static alphanumeric data string representation registered for generated simulation QR codes.
     */
    public static final String QR_CODE = "A";

    /**
     * The transformation scalar factor variable converting microsecond operations into local milliseconds.
     */
    public static final double NANOS_PER_MILLIS = 1e6;

    /**
     * The default timeout window limit interval assigned to determine a chassis stalemate operational state (ms).
     */
    public static final long DEFAULT_STALEMATE_INTERVAL = 60000;

    /**
     * The default background generation transmission frequency interval for motion status telemetry frames (ms).
     */
    public static final long DEFAULT_MOTION_INTERVAL = 500;

    /**
     * The default background generation transmission frequency interval for lidar sensor range streams (ms).
     */
    public static final long DEFAULT_LIDAR_INTERVAL = 500;

    /**
     * The default background event transmission frequency interval configured for camera update logs (ms).
     */
    public static final long DEFAULT_CAMERA_INTERVAL = 500;

    /**
     * The default textual alphanumeric identifier tag bound to a simulated visual marker object instance.
     */
    public static final String LABEL = "A";

    /**
     * The minimum clearance safety gap layout distance required between separate world grid obstacles in metres (m).
     */
    public static final double MIN_OBSTACLE_DISTANCE = 1;

    /**
     * The safety radius boundary applied to clear obstacles around the robot to prevent immediate gimbal lock (m).
     */
    public static final double DEFAULT_ANTI_GIMBAL_RADIUS = 0.3;

    /**
     * The threshold timeout interval window assigned to reset pending asynchronous status states (ms).
     */
    public static final long STATUS_TIMEOUT = 3000;

    /**
     * The scaling adjustment variable utilised to align core physical metres into JBox2D coordinate systems.
     */
    static final float JBOX_SCALE = 100;

    /**
     * The maximum linear acceleration index threshold boundary applied to mechanical force steps.
     */
    static final float MAX_ACC = 1 * JBOX_SCALE;

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

    /**
     * Parses the provided JSON configuration tree node to instantiate and structure a fully
     * initialised {@link SimRobot} simulation profile.
     *
     * @param root the root Jackson configuration node structure containing raw properties
     * @param file the configuration file object layout metadata tracking source origins
     * @return a newly configured and structured {@link SimRobot} instance channel
     */
    public static SimRobot create(JsonNode root, File file) {
        Locator locator = Locator.root();
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME, file.toString());
        long mapSeed = locator.path("mapSeed").getNode(root).asLong(0);
        long robotSeed = locator.path("robotSeed").getNode(root).asLong(0);
        Random mapRandom = mapSeed > 0L ? new Random(mapSeed) : new Random();
        Random robotRandom = robotSeed > 0L ? new Random(robotSeed) : new Random();
        SimRobotConfig config = SimRobotConfig.fromJson(root, locator);
        return new SimRobot(config, robotRandom, mapRandom);
    }

    /**
     * The encapsulation layout record matching parsed configuration profiles and thresholds.
     */
    private final SimRobotConfig config;

    /**
     * The primary pseudo-random generator handling kinematics variance and telemetry fluctuations.
     */
    private final Random random;

    /**
     * The distinct pseudo-random generator handling structural environmental map object placements.
     */
    private final Random mapRandom;

    /**
     * The reactive publish stream processor pipe handling low-level runtime engine exceptions.
     */
    private final PublishProcessor<Throwable> errors;

    /**
     * The re-emitting state processor broadcasting alterations across the registered environmental obstacle matrix collection.
     */
    private final BehaviorProcessor<Collection<Obstacle>> obstacleChanged;

    /**
     * The re-emitting state processor broadcasting serialised updates summarising current line status states.
     */
    private final BehaviorProcessor<RobotStatusApi> robotLineState;

    /**
     * The primary JBox2D physics coordinator managing active bodies, contact joints, and velocity steps.
     */
    private final World world;

    /**
     * The structural rigid physics body component representing the robot chassis platform inside the physics canvas.
     */
    private final Body robot;

    /**
     * The geometry shape and material density structural binding component mapped onto the robot body entity.
     */
    private final Fixture robotFixture;

    /**
     * The thread-safe container reference locking pending operation command packets requested by the controller.
     */
    private final AtomicReference<RobotRequests> requests;

    /**
     * The structural snapshot tracking active orientation behaviours bound to the sensor head component.
     */
    private HeadStatus headStatus;
    private Body obstacleBody;
    private boolean connected;
    private boolean closed;
    private long startSimulationTime;
    private long robotTime;
    private long lastTick;
    private long motionStatusTime;
    private long lidarTimeout;
    private long cameraTimeout;
    private long stalemateTimeout;
    private boolean stalemate;
    private long mapExpiration;
    private Collection<Obstacle> obstacleMap;
    private MapBuilder template;
    private long randomMapExpiration;
    private Complex headDirection;
    private MotionStatus motionStatus;
    private double frontDistance;
    private double rearDistance;
    private boolean frontSensor;
    private boolean rearSensor;
    private double leftPps;
    private double rightPps;
    private boolean sendLidar;
    private boolean sendMotion;
    private boolean sendContacts;
    private Consumer<WheellyContactsMessage> onContacts;
    private Consumer<WheellyLidarMessage> onLidars;
    private Consumer<WheellyMotionMessage> onMotions;
    private Consumer<CameraEvent> onCameras;
    private long headStatusTime;
    private long moveStatusTime;

    /**
     * Initialises a new {@link SimRobot} instance, building the internal JBox2D physics universe,
     * formatting chassis density components, allocating reactive processors, setting up default sensor
     * clearance states, and populating the environment matrix with an initial random layout.
     *
     * @param config    the detailed configuration record structure detailing defaults and layout options
     * @param random    the pseudo-random engine assigned for kinematics error drift calculations
     * @param mapRandom the pseudo-random engine assigned for dynamic environment object placement shifts
     */
    public SimRobot(SimRobotConfig config, Random random, Random mapRandom) {
        this.config = config;
        this.random = requireNonNull(random);
        this.mapRandom = requireNonNull(mapRandom);
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
        this.frontSensor = this.rearSensor = true;
        this.robotLineState = BehaviorProcessor.createDefault(new RobotLineState(false, false, false, false));
        this.motionStatus = MotionStatus.halt();
        this.headStatus = HeadStatus.lookStraight();
        generateRandomMap();
    }

    /**
     * Registers a new callback consumer to receive contact sensor event messages,
     * chain-linking it to any existing registered listeners.
     *
     * @param callback the {@link Consumer} functional routine to execute upon bumper contact events
     */
    @Override
    public void addOnContacts(Consumer<WheellyContactsMessage> callback) {
        onContacts = onContacts == null ? callback : callback.andThen(onContacts);
    }

    /**
     * Registers a new callback consumer to receive proximity lidar range telemetry messages,
     * chain-linking it to any existing registered listeners.
     *
     * @param callback the {@link Consumer} functional routine to execute upon lidar message arrivals
     */
    @Override
    public void addOnLidar(Consumer<WheellyLidarMessage> callback) {
        onLidars = onLidars == null ? callback : callback.andThen(onLidars);
    }

    /**
     * Registers a new callback consumer to receive odometer motion diagnostic messages,
     * chain-linking it to any existing registered listeners.
     *
     * @param callback the {@link Consumer} functional routine to execute upon motion message arrivals
     */
    @Override
    public void addOnMotion(Consumer<WheellyMotionMessage> callback) {
        onMotions = onMotions == null ? callback : callback.andThen(onMotions);
    }

    /**
     * Stubs out power supply message registration.
     *
     * @param callback the consumer routine to register for handling power telemetry updates
     */
    @Override
    public void addOnSupply(Consumer<WheellySupplyMessage> callback) {
    }

    /**
     * Computes the absolute spatial 2D coordinates representing the real-time position
     * of the robot integrated camera hardware module.
     *
     * @return the calculated {@link Point2D} detailing the absolute camera position in space
     */
    private Point2D cameraLocation() {
        return config.robotSpec().cameraLocation(location(), direction(), sensorDirection());
    }

    /**
     * Generates a structural radial geometric bounding region representing the current active
     * vision matrix field of view tracking ahead of the camera sensor hardware.
     *
     * @return the initialised {@link AreaExpression} representing the spatial cone area slice covered by the camera sensor
     */
    public AreaExpression cameraSensorArea() {
        RobotSpec robotSpec = robotSpec();
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
     * Evaluates clear path parameters to verify if the robot chassis has sufficient safe clearance
     * to execute backward translations without colliding with dynamic grid obstacles.
     *
     * @return {@code true} if a backward travel path is unblocked, {@code false} if a crash risk is predicted
     */
    public boolean canMoveBackward() {
        return rearSensor && (rearDistance == 0 || rearDistance > SAFE_DISTANCE);
    }

    /**
     * Evaluates clear path parameters to verify if the robot chassis has sufficient safe clearance
     * to execute forward translations without colliding with dynamic grid obstacles.
     *
     * @return {@code true} if a forward travel path is unblocked, {@code false} if a crash risk is predicted
     */
    public boolean canMoveForward() {
        return frontSensor && (frontDistance == 0 || frontDistance > SAFE_DISTANCE);
    }

    /**
     * Performs sequential analytical sweep verifications against lidar sensor boundaries and bumper fields,
     * isolating the nearest blocking obstacles and immediately triggering immediate status telemetry dispatches
     * if bumper switch thresholds change state.
     *
     * @param initialFrontSensor the historical baseline clear state tracking the front sensor field before this verification sweep
     * @param initialRearSensor  the historical baseline clear state tracking the rear sensor field before this verification sweep
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
                    + random.nextGaussian() * config.errSensor();
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
                    + random.nextGaussian() * config.errSensor();
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
     * Audits current travel directions against safety path calculations, executing an immediate hardware
     * brake freeze if the chassis continues translation along a newly blocked path vector.
     */
    private void checkForSpeed() {
        if ((MotionStatus.MotionStatusId.FORWARD.equals(motionStatus.status()) && !canMoveForward())
                || (MotionStatus.MotionStatusId.BACKWARD.equals(motionStatus.status()) && !canMoveBackward())) {
            haltImmediate();
            sendMotion = true;
        }
    }

    /**
     * Requests the allocation of close down routines across active async processing contexts,
     * releasing joint structures and setting termination flags.
     */
    @Override
    public void close() {
        requests.updateAndGet(r -> r.close(true));
        logger.atInfo().log("Closing robot ...");
    }

    /**
     * Derives discrete wheel motor angular setpoints configurations in pulses per second (pps),
     * combining target translation components and clamping results inside baseline safety limits.
     *
     * @param linear   the requested linear velocity target component configured in pulses per second (pps)
     * @param rotation the requested chassis rotation velocity target component configured in pulses per second (pps)
     */
    private void composeSpeed(double linear, double rotation) {
        leftPps = clamp(linear + rotation, -RobotSpec.MAX_PPS, RobotSpec.MAX_PPS);
        rightPps = clamp(linear - rotation, -RobotSpec.MAX_PPS, RobotSpec.MAX_PPS);
    }

    /**
     * Initialises interface communication connections, synchronises execution clock layers,
     * and sets off background simulation thread timers.
     */
    @Override
    public void connect() {
        if (!closed && !connected) {
            syncConnect();
            if (config.tickInterval() == 0) {
                startSyncSimulation();
            } else {
                logger.atInfo().log("Started simulation");
                tick();
            }
        }
    }

    /**
     * Computes the relative spatial angular direction vector between the robot location point
     * and a provided physical contact collision manifold interface point.
     *
     * @param contact the targeted JBox2D physics {@link Contact} manifold details to analyse
     * @return the derived {@link Complex} direction vector tracking the intersection bearing relative to the chassis
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
     * Recreates static physical environmental obstacle body structures inside the JBox2D universe,
     * clearing the historical obstacle boundary anchors and resetting safety sensor values.
     *
     * @param obstacleMap the new collection profile grouping target environmental obstacles to bind
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
     * Generates a new comprehensive structural obstacle mapping allocation array list, populating
     * both unlabelled spatial items and camera identifier tags according to active blueprint dimensions.
     *
     * @return the initialised {@link List} of structured {@link Obstacle} layout definitions
     */
    private List<Obstacle> createObstacleMap() {
        Point2D robotLocation = location();
        return template
                // add obstacles
                .rand(random, null,
                        robotLocation, MIN_OBSTACLE_DISTANCE, config.numObstacles())
                // add labels
                .rand(random, LABEL,
                        robotLocation, MIN_OBSTACLE_DISTANCE, config.numLabels())
                .build();
    }


    /**
     * Extracts the real-time absolute heading vector representing the current
     * alignment bearing of the robot physics body.
     *
     * @return the derived {@link Complex} geometry direction mapping the chassis alignment
     */
    public Complex direction() {
        return Complex.fromRad(PI / 2 - robot.getAngle());
    }

    /**
     * Returns the absolute calculated front proximity obstacle gap distance reading in metres (m).
     *
     * @return the front sensor range value
     */
    public double frontDistance() {
        return frontDistance;
    }

    /**
     * Formulates a structural geometric bounding sector tracking the absolute range
     * matrix slice mapped directly ahead of the front lidar sensor hardware component.
     *
     * @return the newly constructed front lidar {@link AreaExpression} spatial matrix
     */
    private AreaExpression frontLidarArea() {
        return AreaExpression.radialSensorArea(
                frontLidarLocation(), headAbsDirection(), config.robotSpec().lidarFOV(),
                DEFAULT_OBSTACLE_RADIUS,
                DEFAULT_OBSTACLE_RADIUS,
                config.robotSpec().maxRadarDistance() + DEFAULT_OBSTACLE_RADIUS
        );
    }

    /**
     * Computes the absolute 2D coordinate position representing the real-time location
     * of the front lidar ranging array assembly.
     *
     * @return the calculated {@link Point2D} coordinate point mapping the front lidar assembly
     */
    Point2D frontLidarLocation() {
        return config.robotSpec().frontLidarLocation(location(), direction(), sensorDirection());
    }

    /**
     * Returns the verification flag tracking whether the front bumper micro-switch grid has clear path attributes.
     *
     * @return {@code true} if no physical front contacts are active, {@code false} otherwise
     */
    public boolean frontSensor() {
        return frontSensor;
    }

    /**
     * Generates an arbitrary safe coordinate point profile within the outer boundaries of the canvas grid,
     * validating that the selected coordinate point avoids overlapping known collision structures.
     *
     * @param map the current global active obstacle field layout snapshot to avoid
     * @return a safe unblocked destination {@link Point2D} inside the physics world
     */
    private Point2D generateLocation(Collection<Obstacle> map) {
        Point2D loc1;
        double worldSize = config.worldSize();
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
     * Generates a completely new random environment layout blueprint template, creating
     * the static physical collision components and notifying downstream reactive subscribers.
     */
    private void generateRandomMap() {
        // Selects a random map builder
        List<MapBuilder> maps = config.maps();
        template = maps.size() == 1
                ? maps.getFirst()
                : maps.get(mapRandom.nextInt(maps.size()));
        // Creates the obstacle map
        Collection<Obstacle> map = createObstacleMap();
        createObstacleBody(map);
        obstacleChanged.onNext(map);
    }

    /**
     * Regenerates dynamic internal content objects over the current environmental workspace template,
     * pushing structural updates to the downstream observers.
     */
    private void generateRandomMapContent() {
        Collection<Obstacle> map = createObstacleMap();
        createObstacleBody(map);
        obstacleChanged.onNext(map);
    }

    /**
     * Dispatches an asynchronous request instructing the simulation supervisor pipeline
     * to smoothly transition the current active translation state into a halt profile.
     *
     * @return a {@link Single} emitting {@code true} upon successful request registration
     */
    @Override
    public Single<Boolean> halt() {
        requests.updateAndGet(r -> r.motionStatus(MotionStatus.halt()));
        return Single.just(true);
    }

    /**
     * Enforces an immediate emergency brake lockdown across both motor pulse values,
     * overriding calculations and freezing chassis velocities immediately.
     */
    private void haltImmediate() {
        motionStatus = MotionStatus.halt();
        leftPps = rightPps = 0;
    }

    /**
     * Processes step-by-step target location calculations for linear backward travel movements,
     * computing remaining path distance boundaries, scaling alignment velocities,
     * and stopping operations upon entering destination target range limits.
     */
    private void handleBackward() {
        RobotSpec robotSpec = robotSpec();
        // Compute the distance to target
        Point2D robotLocation = location();
        double distance = robotLocation.distance(motionStatus.target());
        // Check for target reached
        if (distance <= robotSpec.targetRange()) {
            // Target reached
            haltImmediate();
            sendMotion = true;
            return;
        }
        // Compute the rotation angle
        Complex targetDirection = Complex.direction(robotLocation, motionStatus.target());
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
     * Traverses the active rigid body physics interaction registry lists inside the universe,
     * validating contact switch flags across bumper fields by evaluating the relative vertical
     * components of intersection coordinates.
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
     * Controls the real-time velocity step adjustments for both motor tracks, enforcing auto-straighten
     * sensor head alignment cycles and structural hardware command timeouts when communication threshold windows elapse.
     */
    private void handleEngine() {
        if (robotTime >= headStatusTime + STATUS_TIMEOUT) {
            headStatus = HeadStatus.lookStraight();
            headDirection = Complex.DEG0;
            headStatusTime = robotTime;
        }
        switch (headStatus.status()) {
            case FRONT_TRACK -> trackFrontTarget();
            case REAR_TRACK -> trackRearTarget();
        }
        if (robotTime >= moveStatusTime + STATUS_TIMEOUT) {
            haltImmediate();
            moveStatusTime = robotTime;
        }
        switch (motionStatus.status()) {
            case ROTATE -> handleRotation();
            case FORWARD -> handleForward();
            case BACKWARD -> handleBackward();
        }
    }


    /**
     * Processes step-by-step target location calculations for linear forward travel movements,
     * computing remaining path distance boundaries, scaling alignment velocities,
     * and stopping operations upon entering destination target range limits.
     */
    private void handleForward() {
        // Compute the distance to target
        Point2D robotLocation = location();
        double distance = robotLocation.distance(motionStatus.target());
        // Check for target reached
        RobotSpec robotSpec = robotSpec();
        if (distance <= robotSpec.targetRange()) {
            // Target reached
            haltImmediate();
            sendMotion = true;
            return;
        }
        // Compute the rotation angle
        Complex targetDirection = Complex.direction(robotLocation, motionStatus.target());
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
     * Extracts and executes pending configuration requests stored within the asynchronous thread-safe reference holder,
     * mapping structural closedown commands, external timeline syncs, and movement objectives.
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
            if (r.motionStatus() != null) {
                MotionStatus newMotionStatus = r.motionStatus();
                motionStatus = newMotionStatus;
                moveStatusTime = robotTime;
                if (newMotionStatus.status() == MotionStatus.MotionStatusId.HALT) {
                    leftPps = rightPps = 0;
                }
                checkForSpeed();
            }
            if (r.headStatus() != null) {
                headStatus = r.headStatus();
                headStatusTime = robotTime;
                if (FIX_DIRECTION.equals(headStatus.status())) {
                    headDirection = r.headStatus().direction();
                }
            }
        }
    }

    /**
     * Processes step-by-step angular target calculations for stationary chassis rotation movements,
     * mapping optimal shortest path directions and halting motors when alignment falls within standard tolerances.
     */
    private void handleRotation() {
        // Compute the rotation angle
        double rotDeg = motionStatus.targetDir().sub(direction()).toDeg();
        double absRotDeg = abs(rotDeg);
        // Compute che rotation speed
        double rotSpeed;
        // Check for rotation completed
        RobotSpec robotSpec = robotSpec();
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
     * Monitors active sensor grids to trace structural chassis movement stalemate conditions,
     * activating structural countdown timers and trigger-relocating the platform if the obstacle deadlock persists.
     */
    private void handleStalemate() {
        if (frontSensor || rearSensor) {
            // no stalemate
            stalemate = false;
        } else if (!stalemate) {
            // First stalemate, start the timer
            stalemate = true;
            stalemateTimeout = robotTime + config.stalemateInterval();
        } else if (robotTime >= stalemateTimeout) {
            // stalemate timeout
            safeRelocateRandom();
        }
    }


    /**
     * Computes the global absolute directional vector representing the orientation bearing of the sensor head assembly.
     *
     * @return the derived {@link Complex} geometry direction vector mapping the absolute head positioning
     */
    Complex headAbsDirection() {
        return direction().add(sensorDirection());
    }

    /**
     * Extracts the relative directional angular offset representing the current
     * alignment bearing of the sensor head relative to the chassis platform.
     *
     * @return the relative {@link Complex} geometry direction vector
     */
    public Complex headDirection() {
        return headDirection;
    }

    /**
     * Verifies if the robot chassis is currently operating under a completely stationary or halt profile.
     *
     * @return {@code true} if the active motion status id corresponds to halt, {@code false} otherwise
     */
    @Override
    public boolean isHalt() {
        return MotionStatus.MotionStatusId.HALT.equals(motionStatus.status());
    }

    /**
     * Extracts the global absolute 2D spatial coordinate location representing the position
     * of the robot centre calculated from the underlying JBox2D physics canvas body coordinates.
     *
     * @return the calculated {@link Point2D} coordinate point in metres
     */
    public Point2D location() {
        Vec2 pos = robot.getPosition();
        return new Point2D.Double(pos.x / JBOX_SCALE, pos.y / JBOX_SCALE);
    }

    /**
     * Queues an asynchronous movement objective parameter targeting a designated spatial location coordinate point,
     * initialising either a forward or backward travel path structure.
     *
     * @param frontMove {@code true} to initialize a forward travel state, {@code false} to move backward
     * @param location  the global target destination coordinate point
     * @return a {@link Single} emitting {@code true} upon request registration
     * @throws NullPointerException if {@code location} is {@code null}
     */
    @Override
    public Single<Boolean> move(boolean frontMove, Point2D location) {
        requireNonNull(location);
        requests.updateAndGet(s ->
                s.motionStatus(frontMove
                        ? MotionStatus.forward(location)
                        : MotionStatus.backward(location)
                ));
        return Single.just(true);
    }

    /**
     * Binds a newly specified obstacle mapping structure collection to the simulator universe,
     * generating rigid body boundaries inside the physics context and synchronising layer deadlines.
     *
     * @param map the new global active obstacle field layout snapshot to map
     * @return this modified {@link SimRobot} instance channel profile
     */
    public SimRobot obstacleMap(Collection<Obstacle> map) {
        obstacleMap = map;
        createObstacleBody(map);
        obstacleChanged.onNext(map);
        randomMapExpiration = robotTime + config.mapPeriod();
        mapExpiration = robotTime + config.randomPeriod();
        return this;
    }

    /**
     * Returns the global active obstacle field layout collection profile currently registered in the workspace canvas.
     *
     * @return the active {@link Collection} grouping structured obstacle boundaries
     */
    public Collection<Obstacle> obstacleMap() {
        return obstacleMap;
    }

    /**
     * Registers a callback consumer to catch processed camera visual event components,
     * chain-linking it to any existing registered listeners.
     *
     * @param callback the consumer functional routine to register
     */
    @Override
    public void onCamera(Consumer<CameraEvent> callback) {
        onCameras = onCameras == null ? callback : callback.andThen(onCameras);
    }

    /**
     * Returns a continuous reactive stream monitoring simulation exceptions.
     *
     * @return a {@link Flowable} emitting exceptions thrown during background processing loops
     */
    @Override
    public Flowable<Throwable> readErrors() {
        return errors;
    }

    /**
     * Returns a continuous reactive stream monitoring obstacle layer transformations.
     *
     * @return a {@link Flowable} emitting the updated collections whenever the obstacle map changes
     */
    public Flowable<Collection<Obstacle>> readObstacleMap() {
        return obstacleChanged;
    }

    /**
     * Returns a continuous reactive stream monitoring the robot line connection status updates.
     *
     * @return a {@link Flowable} emitting continuous updates of {@link RobotStatusApi}
     */
    @Override
    public Flowable<RobotStatusApi> readRobotStatus() {
        return robotLineState;
    }

    /**
     * Returns the absolute calculated rear proximity obstacle gap distance reading in metres (m).
     *
     * @return the rear sensor range value
     */
    public double rearDistance() {
        return rearDistance;
    }

    /**
     * Formulates a structural geometric bounding sector tracking the absolute range
     * matrix slice mapped directly behind the rear lidar sensor hardware component.
     *
     * @return the newly constructed rear lidar {@link AreaExpression} spatial matrix
     */
    public AreaExpression rearLidarArea() {
        RobotSpec robotSpec = robotSpec();
        return AreaExpression.radialSensorArea(
                rearLidarLocation(), headAbsDirection().opposite(), robotSpec.lidarFOV(),
                DEFAULT_OBSTACLE_RADIUS,
                DEFAULT_OBSTACLE_RADIUS,
                robotSpec.maxRadarDistance() + DEFAULT_OBSTACLE_RADIUS
        );
    }

    /**
     * Computes the absolute 2D coordinate position representing the real-time location
     * of the rear lidar ranging array assembly.
     *
     * @return the calculated {@link Point2D} coordinate point mapping the rear lidar assembly
     */
    Point2D rearLidarLocation() {
        return config.robotSpec().rearLidarLocation(location(), direction(), sensorDirection());
    }

    /**
     * Returns the verification flag tracking whether the rear bumper micro-switch grid has clear path attributes.
     *
     * @return {@code true} if no physical rear contacts are active, {@code false} otherwise
     */
    public boolean rearSensor() {
        return rearSensor;
    }

    /**
     * Stubs out connection re-establishment handling.
     */
    @Override
    public void reconnect() {
    }

    /**
     * Overrides the current chassis absolute direction heading by applying a rotational transform
     * matrix directly to the underlying JBox2D physics body.
     *
     * @param robotDirection the targeted new orientation heading vector to force bind
     * @return this modified {@link SimRobot} instance channel profile
     */
    public SimRobot robotDir(Complex robotDirection) {
        robot.setTransform(robot.getPosition(),
                (float) Complex.DEG90.sub(robotDirection).toRad());
        return this;
    }

    /**
     * Overrides the global spatial coordinates of the robot by applying an immediate translation
     * transform step directly to the underlying JBox2D physics body.
     *
     * @param x the target position coordinate along the absolute horizontal X axis in metres
     * @param y the target position coordinate along the absolute vertical Y axis in metres
     */
    public void robotPos(double x, double y) {
        Vec2 pos = new Vec2();
        pos.x = (float) (x * JBOX_SCALE);
        pos.y = (float) (y * JBOX_SCALE);
        robot.setTransform(pos, robot.getAngle());
    }

    /**
     * Returns the baseline technical specifications configuration bound to the robot.
     *
     * @return the active {@link RobotSpec} parameters layout properties
     */
    @Override
    public RobotSpec robotSpec() {
        return config.robotSpec();
    }

    /**
     * Returns the internal accumulated logical simulation time.
     *
     * @return the robot internal timeline marker value in milliseconds
     */
    @Override
    public long robotTime() {
        return robotTime;
    }

    /**
     * Queues an absolute rotation command targeting the specified heading alignment bearing.
     *
     * @param dir the target direction angle in degrees (DEG)
     * @return a {@link Single} emitting {@code true} indicating the rotation command was successfully queued
     */
    @Override
    public Single<Boolean> rotate(int dir) {
        requests.updateAndGet(r -> r.motionStatus(MotionStatus.rotate(Complex.fromDeg(dir))));
        return Single.just(true);
    }

    /**
     * Randomly relocates the robot chassis into a validated clear safety quadrant inside the outer canvas space boundaries,
     * ensuring it avoids spawning inside pre-existing obstacles.
     */
    public void safeRelocateRandom() {
        Collection<Obstacle> map = obstacleMap;
        Point2D loc = map != null
                ? generateLocation(map)
                : new Point2D.Double();
        // Relocate robot
        robotPos(loc.getX(), loc.getY());
    }

    /**
     * Queues a sensor head configuration alignment command to scan in the specified direction,
     * safely clamping the input angle within physical hardware limits.
     *
     * @param direction the target sensor head angle profile bearing in degrees (DEG)
     * @return a {@link Single} emitting {@code true} indicating the scan command was successfully queued
     */
    @Override
    public Single<Boolean> scan(int direction) {
        int range = robotSpec().headFOV().toIntDeg() / 2;
        int dir = clamp(direction, -range, range);
        requests.updateAndGet(s ->
                s.headStatus(HeadStatus.scan(Complex.fromDeg(dir)))
        );
        return Single.just(true);
    }

    /**
     * Evaluates visual targets intersection fields tracking inside the camera sight matrix,
     * compiling and dispatching camera event snapshots to all subscribed consumers.
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
        if (onCameras != null) {
            onCameras.accept(event);
        }
        cameraTimeout = robotTime + config.cameraInterval();
    }

    /**
     * Compiles and dispatches real-time physical bumper collision micro-switch event packets
     * to all registered listeners.
     */
    private void sendContacts() {
        WheellyContactsMessage msg = new WheellyContactsMessage(
                robotTime,
                frontSensor, rearSensor,
                canMoveForward(),
                canMoveBackward()
        );
        if (onContacts != null) {
            onContacts.accept(msg);
        }
    }

    /**
     * Compiles and dispatches firmware 0.12.0 compliant lidar telemetry sweep range data frames,
     * mapping absolute spatial grid markers and relative tracking statuses.
     */
    private void sendLidar() {
        Point2D pos = this.location();
        double xPulses = distance2Pulse(pos.getX());
        double yPulses = distance2Pulse(pos.getY());
        Complex robotYaw = direction();
        Point2D pulses = location2Pulses(headStatus.target());
        WheellyLidarMessage msg = new WheellyLidarMessage(
                robotTime,
                m2mm(frontDistance), m2mm(rearDistance),
                xPulses, yPulses, robotYaw.toIntDeg(), headDirection.toIntDeg(),
                headStatus.status(), headStatus.direction().toIntDeg(),
                pulses.getX(), pulses.getY());
        lidarTimeout = robotTime + config.lidarInterval();
        if (onLidars != null) {
            onLidars.accept(msg);
        }
    }

    /**
     * Compiles and dispatches firmware 0.12.0 compliant odometer wheel-pulse motion telemetry frames,
     * formatting multi-parameter layout targets for synchronisation with the controller loops.
     */
    private void sendMotion() {
        Point2D pos = this.location();
        double xPulses = pos.getX() / DISTANCE_PER_PULSE;
        double yPulses = pos.getY() / DISTANCE_PER_PULSE;
        Complex robotDir = direction();
        Point2D pulses = location2Pulses(motionStatus.target());
        WheellyMotionMessage msg = new WheellyMotionMessage(
                robotTime,
                xPulses, yPulses, robotDir.toIntDeg(),
                leftPps, rightPps,
                0, motionStatus.status(),
                motionStatus.targetDir().toIntDeg(), (int) round(leftPps), (int) round(rightPps),
                0, 0,
                pulses.getX(), pulses.getY());
        motionStatusTime = robotTime + config.motionInterval();
        if (onMotions != null) {
            onMotions.accept(msg);
        }
    }

    /**
     * Extracts the relative directional angular offset representing the current
     * alignment bearing of the sensor head relative to the chassis platform.
     *
     * @return the relative {@link Complex} geometry direction vector
     */
    public Complex sensorDirection() {
        return headDirection;
    }

    /**
     * Configures the relative alignment bearing of the sensor head, returning
     * the modified simulator profile instance.
     *
     * @param sensorDirection the new relative {@link Complex} alignment vector to force bind
     * @return this modified {@link SimRobot} instance channel profile
     */
    SimRobot sensorDirection(Complex sensorDirection) {
        headDirection = sensorDirection;
        return this;
    }

    /**
     * Advances the simulation environment logical clock by one frame interval step, polling asynchronous
     * thread requests, sweeping expirations, updating behavioural kinematics motors, running the JBox2D physics
     * world step, verifying sensor states, and scheduling discrete telemetry message transmissions.
     */
    void simulate() {
        // Update current simulation time
        robotTime += config.interval();
        sendMotion = sendContacts = sendLidar = false;
        lastTick = System.nanoTime();
        handleRequests();

        if (closed) {
            return;
        }

        // Check for random map expiration
        long randomPeriod = config.randomPeriod();
        if (robotTime >= randomMapExpiration) {
            generateRandomMap();
            randomMapExpiration = robotTime + expRandom(random, config.mapPeriod());
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
        if (sendMotion || robotTime >= motionStatusTime) {
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
     * Simulates the low-level rigid-body physics for the configured delta time interval,
     * applying forward linear forces and rotational angular torques to the robot chassis inside the
     * JBox2D engine grid canvas while introducing pseudo-random noise to mimic mechanical slippage and sensor variance.
     */
    private void simulatePhysics() {
        double dt = config.interval() * 1e-3;

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
        localForce = localForce.mul((float) (1 + random.nextGaussian() * config.errSensor()));

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
        angularTorque *= (1 + random.nextGaussian() * config.errSigma());
        // Clip the angular torque
        angularTorque = clamp(angularTorque, -MAX_TORQUE, MAX_TORQUE);
        world.clearForces();
        robot.applyForceToCenter(force);
        robot.applyTorque((float) angularTorque);
        world.step((float) dt, VELOCITY_ITER, POSITION_ITER);
    }

    /**
     * Calculates the execution speed profile factor of the simulation loop,
     * evaluating the ratio between accumulated logical simulation time and elapsed wall-clock nanoseconds.
     *
     * @return the simulation acceleration speed multiplier factor
     */
    @Override
    public double simulationSpeed() {
        long dt = lastTick - startSimulationTime;
        return dt > 0 ? robotTime * NANOS_PER_MILLIS / dt : 1;
    }

    /**
     * Enqueues an asynchronous request setting or overriding the internal timeline clock counter.
     *
     * @param time the target simulation timeline marker coordinate value in milliseconds
     */
    public void simulationTime(long time) {
        requests.updateAndGet(r -> r.simulationTime(time));
    }

    /**
     * Launches a synchronous simulation thread process loop on an IO thread scheduler,
     * continually advancing the clock environment state frames until closed.
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
     * Dispatches initial connection sequence handshake signals, establishes nanosecond start-markers,
     * and broadcasts initial baseline telemetry packets to synchronise interface states.
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
     * Schedules periodic simulation computations on a computation thread pool,
     * recurring recursively based on the configured tick interval frequency window.
     */
    private void tick() {
        if (!closed) {
            Completable.timer(config.tickInterval(), TimeUnit.MILLISECONDS)
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
     * Queues an asynchronous tracking destination command point, configuring the sensor head
     * mechanism component to lock onto the target's spatial coordinates via front or rear face profiles.
     *
     * @param frontTrack {@code true} to engage tracking using front face mechanisms, {@code false} for rear track
     * @param target     the target destination coordinate point to track in space
     * @return a {@link Single} emitting {@code true} upon request registration
     * @throws NullPointerException if {@code target} is {@code null}
     */
    @Override
    public Single<Boolean> track(boolean frontTrack, Point2D target) {
        requireNonNull(target);
        requests.updateAndGet(r -> r.headStatus(
                frontTrack
                        ? HeadStatus.trackFrontFace(target)
                        : HeadStatus.trackRearFace(target)));
        return Single.just(true);
    }

    /**
     * Computes and updates the sensor head alignment direction tracking a front face target,
     * introducing an anti-gimbal safety check to prevent erratic angular calculations when
     * the target is too close to the head assembly.
     */
    private void trackFrontTarget() {
        RobotSpec robotSpec = robotSpec();
        Point2D target = headStatus.target();
        Point2D headLocation = robotSpec.headLocation(location(), direction());
        double distance = headLocation.distance(target);
        if (distance > config.antiGimbalRadius()) {
            int headDir = Complex.direction(headLocation, target)
                    .sub(direction())
                    .toIntDeg();
            int headRange = robotSpec.headFOV().toIntDeg() / 2;
            this.headDirection = Complex.fromDeg(clamp(headDir, -headRange, headRange));
        }
    }

    /**
     * Computes and updates the sensor head alignment direction tracking a rear face target,
     * inverting the baseline bearing vector and introducing an anti-gimbal safety threshold
     * to prevent erratic angular fluctuations.
     */
    private void trackRearTarget() {
        RobotSpec robotSpec = robotSpec();
        Point2D target = headStatus.target();
        Point2D headLocation = robotSpec.headLocation(location(), direction());
        double distance = headLocation.distance(target);
        if (distance > config.antiGimbalRadius()) {
            int headDir = Complex.direction(headLocation, target)
                    .opposite()
                    .sub(direction())
                    .toIntDeg();
            int headRange = robotSpec.headFOV().toIntDeg() / 2;
            this.headDirection = Complex.fromDeg(clamp(headDir, -headRange, headRange));
        }
    }


    /**
     * Encapsulates all configuration properties, simulation timing limits, error thresholds,
     * and environment maps required to initialise a {@link SimRobot} instance.
     *
     * @param robotSpec         the detailed structural and physical specification parameters layout of the robot
     * @param interval          the simulation frame update step interval in milliseconds (ms)
     * @param tickInterval      the low-level loop thread sleep duration between execution steps (ms)
     * @param motionInterval    the background telemetry message transmission loop rate for motion status (ms)
     * @param lidarInterval     the background telemetry message transmission loop rate for lidar distance streams (ms)
     * @param cameraInterval    the frame refresh transmission frequency rate for simulated camera views (ms)
     * @param stalemateInterval the safety countdown duration allowed before flagging a structural position deadlock (ms)
     * @param mapPeriod         the maximum duration window governing map-template blueprint alterations (ms)
     * @param randomPeriod      the dynamic countdown duration window governing periodic obstacle displacements (ms)
     * @param numObstacles      the total target unlabelled obstacle count to spawn within the world canvas matrix
     * @param numLabels         the total camera tracking visual tag markers to distribute inside the grid
     * @param worldSize         the outer structural square canvas coordinate size width limit in metres (m)
     * @param errSensor         the standard Gaussian deviation noise factor applied to emulate lidar distance inaccuracies
     * @param errSigma          the scale variance coefficient applied to simulate mechanical wheel track slippage
     * @param maps              the collection list holding environmental layout blueprint templates
     * @param antiGimbalRadius  the minimum physical proximity threshold required to prevent head gimbal angular lock issues (m)
     */
    public record SimRobotConfig(
            RobotSpec robotSpec,
            long interval,
            long tickInterval,
            long motionInterval,
            long lidarInterval,
            long cameraInterval,
            long stalemateInterval,
            long mapPeriod,
            long randomPeriod,
            int numObstacles,
            int numLabels,
            double worldSize,
            double errSensor,
            double errSigma,
            List<MapBuilder> maps,
            double antiGimbalRadius) {

        /**
         * Parses a comprehensive Jackson JSON node structure to extract configuration rules,
         * intervals, and external map template links, initialising a {@link SimRobotConfig} instance.
         *
         * @param root    the target root JSON document tree parsing unit
         * @param locator the active locator helper tracking current structural JSON paths
         * @return a fully populated and structured {@link SimRobotConfig} profile record
         */
        public static SimRobotConfig fromJson(JsonNode root, Locator locator) {
            int numObstacles = locator.path("numObstacles").getNode(root).asInt();
            int numLabels = locator.path("numLabels").getNode(root).asInt();
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
            double antiGimbalRadius = locator.path("antiGimbalRadius").getNode(root).asDouble(DEFAULT_ANTI_GIMBAL_RADIUS);
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
            return new SimRobotConfig(robotSpec, interval, tickInterval, motionInterval, lidarInterval, cameraInterval,
                    stalemateInterval, mapPeriod, randomPeriod, numObstacles, numLabels,
                    worldSize, errSensor, errSigma, maps, antiGimbalRadius);
        }

        /**
         * Compact constructor enforcing non-null parameters across specifications and map template profiles.
         */
        public SimRobotConfig {
            requireNonNull(robotSpec);
            requireNonNull(maps);
        }
    }

    /**
     * Models the individual real-time pipeline connection handshake state indicators.
     *
     * @param connecting  {@code true} if the background handshake initialisation sequence is currently active
     * @param connected   {@code true} if the pipeline link is fully open and authenticated
     * @param configuring {@code true} if the system parameters allocation step is executing
     * @param configured  {@code true} if the configuration validation constraints are fully finalised
     */
    record RobotLineState(boolean connecting, boolean connected, boolean configuring,
                          boolean configured) implements RobotStatusApi {
    }

    /**
     * Aggregates asynchronous operational request changes requested by the controller,
     * preserving thread-safe transition states through immutable record copy updates.
     *
     * @param connect        {@code true} if an asynchronous interface connection sequence is requested
     * @param close          {@code true} if a comprehensive shutdown and resource release sweep is requested
     * @param simulationTime the targeted logical simulation timeline counter synchronisation value (ms), active if {@code >= 0}
     * @param motionStatus   the newly updated translation objective payload to apply across wheel motors
     * @param headStatus     the newly updated target alignment behaviours destined for the sensor head assembly
     */
    record RobotRequests(boolean connect, boolean close, long simulationTime,
                         MotionStatus motionStatus, HeadStatus headStatus) {

        private static final RobotRequests EMPTY = new RobotRequests(false, false, -1, null, null);

        /**
         * Returns an empty request template profile instance containing zero active modifications.
         *
         * @return the static unmodifiable default {@link RobotRequests} instance
         */
        public static RobotRequests empty() {
            return EMPTY;
        }

        /**
         * Returns a copy of this request state with the specified close property,
         * generating a new instance if the flag state changes.
         *
         * @param close {@code true} to flag a teardown closedown request
         * @return the updated {@link RobotRequests} data structure copy
         */
        public RobotRequests close(boolean close) {
            return this.close == close ? this : new RobotRequests(connect, close, simulationTime, motionStatus, headStatus);
        }

        /**
         * Returns a copy of this request state with the specified sensor head status payload,
         * generating a new instance if the parameter differs from the current reference.
         *
         * @param headStatus the new tracking behaviour command profile to bind
         * @return the updated {@link RobotRequests} data structure copy
         */
        public RobotRequests headStatus(HeadStatus headStatus) {
            return Objects.equals(this.headStatus, headStatus)
                    ? this
                    : new RobotRequests(connect, close, simulationTime, motionStatus, headStatus);
        }

        /**
         * Returns a copy of this request state with the specified chassis motion status payload,
         * generating a new instance if the parameter differs from the current reference.
         *
         * @param motionStatus the new translation command profile to bind
         * @return the updated {@link RobotRequests} data structure copy
         */
        public RobotRequests motionStatus(MotionStatus motionStatus) {
            return Objects.equals(this.motionStatus, motionStatus)
                    ? this
                    : new RobotRequests(connect, close, simulationTime, motionStatus, headStatus);
        }

        /**
         * Returns a copy of this request state with an updated simulation timeline value,
         * generating a new instance if the temporal property changes.
         *
         * @param simulationTime the target logical timeline marker to set in milliseconds (ms)
         * @return the updated {@link RobotRequests} data structure copy
         */
        public RobotRequests simulationTime(long simulationTime) {
            return this.simulationTime == simulationTime
                    ? this
                    : new RobotRequests(connect, close, simulationTime, motionStatus, headStatus);
        }
    }
}
