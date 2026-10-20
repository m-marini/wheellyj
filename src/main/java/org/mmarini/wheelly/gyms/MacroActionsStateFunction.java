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

package org.mmarini.wheelly.gyms;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.wheelly.behaviours.BehaviourState;
import org.mmarini.wheelly.behaviours.HeadActionId;
import org.mmarini.wheelly.behaviours.MacroActionsState;
import org.mmarini.wheelly.behaviours.MoveActionId;
import org.mmarini.wheelly.control.RobotStatus;
import org.mmarini.wheelly.perception.*;
import org.mmarini.wheelly.robots.*;
import org.mmarini.wheelly.utils.Angle;
import org.mmarini.wheelly.utils.WheellyJsonSchemas;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.lang.Math.clamp;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.utils.Utils.cm2m;

/**
 * Computes and structures the multi-input state feature vectors for Deep Learning
 * macro-actions within the Wheelly environment.
 * <p>
 * This state function fragments raw sensory data into specialised, independent sub-signal
 * vectors including robot kinematic data, proximity buffers, sensor head telemetry,
 * web camera visuals, concurrent finite state machine actions, and an occupancy grid map grid.
 * </p>
 */
public class MacroActionsStateFunction implements StateFunction<MacroActionsState> {

    /**
     * Identifier key for the grid map multidimensional signal.
     */
    public static final String MAP_SIGNAL_ID = "map";

    public static final int CAMERA_SIGNAL_BASE_SIZE = 4;
    /**
     * Number of base categorical cell states inside the tracking grid.
     */
    public static final int NUM_CELL_STATES = 4; // unknown, empty, eco, contact

    /**
     * Channel index identifier for unmapped or unknown sectors.
     */
    public static final int UNKNOWN_CHANNEL = 0;

    /**
     * Channel index identifier for clear or empty sectors.
     */
    public static final int EMPTY_CHANNEL = 1;

    /**
     * Channel index identifier for tactile contact bumper sectors.
     */
    public static final int CONTACT_CHANNEL = 2;

    /**
     * Channel index identifier for radar or LiDAR distance echo sectors.
     */
    public static final int ECHO_CHANNEL = 3;

    /**
     * Configuration node key identifier for tracking marker definitions.
     */
    public static final String MARKER_LABELS_ID = "markerLabels";

    /**
     * Validation URI location matching the JSON schema definition for this function.
     */
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/state-func-macro-actions-schema-0.1";

    /**
     * Identifier key for the robot chassis kinematics feature vector.
     */
    public static final String ROBOT_SIGNAL_ID = "robot";

    /**
     * Identifier key for the proximity, tactile contact, and hindrance vector.
     */
    public static final String PROXY_SIGNAL_ID = "proxy";

    /**
     * Identifier key for the autonomous scanning head telemetry vector.
     */
    public static final String HEAD_SIGNAL_ID = "head";

    /**
     * Identifier key for the web camera optical marker tracking vector.
     */
    public static final String CAMERA_SIGNAL_ID = "camera";

    /**
     * Identifier key for the concurrent high-level FSM action flags vector.
     */
    public static final String FSM_SIGNAL_ID = "fsm";
    public static final int ROBOT_SIGNAL_SIZE = 8 + MotionStatusId.values().length;
    public static final int PROXY_SIGNAL_SIZE = 4;
    public static final int HEAD_SIGNAL_SIZE = 7 + HeadStatusId.values().length;
    public static final int FSM_SIGNAL_SIZE = MoveActionId.values().length + HeadActionId.values().length;
    private static final Logger logger = LoggerFactory.getLogger(MacroActionsStateFunction.class);

    /**
     * Creates a new state function configured with the specified world bounds and
     * environmental tracking markers.
     *
     * @param worldSpec the structural world limits and specification profile
     * @param markers   the explicit list of unique optical marker labels
     * @return a newly initialised {@link MacroActionsStateFunction} instance
     */
    public static MacroActionsStateFunction create(WorldModelSpec worldSpec, List<String> markers) {
        Map<String, SignalSpec> spec = createSpec(worldSpec, markers.size());
        return new MacroActionsStateFunction(markers, spec);
    }

    /**
     * Parses and constructs a state function provider mapping from a JSON configuration block.
     *
     * @param root    the root JSON document node containing environment parameters
     * @param locator the structural locator pattern path targeting the function properties
     * @return a functional lambda mapper waiting to build the state engine from a {@link WorldModelSpec}
     * @throws IOException if schema validation rules fail or data reading is disrupted
     */
    public static Function<WorldModelSpec, StateFunction<MacroActionsState>> create(JsonNode root, Locator locator) throws IOException {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        List<String> markers = locator.path(MARKER_LABELS_ID).elements(root)
                .map(l -> l.getNode(root).asText())
                .toList();
        return spec -> MacroActionsStateFunction.create(spec, markers);
    }

    /**
     * Generates the multi-input signal specification layout dictionary defining lengths and boundaries.
     *
     * @param worldSpec  the structural world layout specification profile
     * @param numMarkers the number of recognised optical markers configured in the system
     * @return a {@link Map} pairing signal keys to their respective {@link SignalSpec} structures
     */
    private static Map<String, SignalSpec> createSpec(WorldModelSpec worldSpec, int numMarkers) {
        long radarSize = worldSpec.robotMapSize();
        int numChannels = numMarkers + NUM_CELL_STATES;
        return Map.of(
                ROBOT_SIGNAL_ID, new FloatSignalSpec(new long[]{ROBOT_SIGNAL_SIZE}, -1f, 1f),
                PROXY_SIGNAL_ID, new FloatSignalSpec(new long[]{PROXY_SIGNAL_SIZE}, 0, 1f),
                HEAD_SIGNAL_ID, new FloatSignalSpec(new long[]{HEAD_SIGNAL_SIZE}, -1f, 1f),
                CAMERA_SIGNAL_ID, new FloatSignalSpec(new long[]{CAMERA_SIGNAL_BASE_SIZE + numMarkers}, -1f, 1f),
                FSM_SIGNAL_ID, new IntSignalSpec(new long[]{FSM_SIGNAL_SIZE}, 2),
                MAP_SIGNAL_ID, new IntSignalSpec(new long[]{numChannels, radarSize, radarSize}, 2)
        );
    }

    private final List<String> markers;
    private final Map<String, SignalSpec> spec;

    /**
     * Main constructor initialising structural properties and validation dictionaries.
     *
     * @param markers the explicit list of visual target marker tags
     * @param spec    the fully detailed multi-input signal specification layout
     */
    public MacroActionsStateFunction(List<String> markers, Map<String, SignalSpec> spec) {
        this.markers = requireNonNull(markers);
        this.spec = requireNonNull(spec);
        logger.atDebug().log("Created");
    }

    /**
     * Processes and formats optical telemetry into an ND4J tensor representing the camera signal vector.
     * <p>
     * This method iterates through the batch of environment states, extracting target coordinates
     * and computing relative directional features for each active marker tag to feed the optical
     * neural layer network.
     * </p>
     *
     * @param states an array of current execution environment states
     * @return a 2D float {@link INDArray} matrix mapping state batches to optical features
     */
    private INDArray cameraSignal(BehaviourState[] states) {
        int n = states.length;
        INDArray signals = Nd4j.zeros(n, CAMERA_SIGNAL_BASE_SIZE + markers.size()).castTo(DataType.FLOAT);
        for (int i = 0; i < n; i++) {
            WorldModel world = states[i].worldModel();
            RobotStatus robot = world.robotStatus();
            CorrelatedCameraEvent correlatedCameraEvent = robot.cameraEvent();

            double sinMarker = 0;
            double cosMarker = 1;

            double correlation = correlatedCameraEvent.cameraTime() >= correlatedCameraEvent.lidarTime() ? 1 : 0;
            int qrCodeIdx = markers.indexOf(correlatedCameraEvent.markerId());

            signals.putScalar(i, 0, sinMarker);
            signals.putScalar(i, 1, cosMarker);
            signals.putScalar(i, 2, correlation);
            signals.putScalar(i, CAMERA_SIGNAL_BASE_SIZE + qrCodeIdx, 1);
        }
        return signals;
    }

    /**
     * Processes and formats the concurrent finite state machine action flags into a binary
     * encoding ND4J tensor.
     * <p>
     * This method transforms the active high-level locomotion and sensor head macro-actions
     * from a batch of macro-environment states into a multi-hot categorical vector. It maps
     * the ordinal positions of both {@link MoveActionId} and {@link HeadActionId} into
     * a continuous row block to provide discrete contextual indicators for the deep network.
     * </p>
     *
     * @param states an array of current execution environment states
     * @return a 2D float {@link INDArray} matrix mapping state batches to active FSM action flags
     * @throws IllegalArgumentException if any state element within the batch is not an instance of {@link MacroActionsState}
     */
    private INDArray fsmSignal(MacroActionsState[] states) {
        int n = states.length;
        INDArray signals = Nd4j.zeros(n, MoveActionId.values().length + HeadActionId.values().length).castTo(DataType.FLOAT);
        for (int i = 0; i < n; i++) {
            MacroActionsState state = states[i];
            int moveActionIdx = state.moveActionId().ordinal();
            int headActionIdx = state.headActionId().ordinal();

            signals.putScalar(i, moveActionIdx, 1);
            signals.putScalar(i, MoveActionId.values().length + headActionIdx, 1);
        }
        return signals;
    }

    /**
     * Processes and formats the autonomous scanning head telemetry into an ND4J tensor.
     * <p>
     * This method extracts the angular orientation of the sensor head, converting it into
     * continuous trigonometric features (sine and cosine components) to avoid phase discontinuities.
     * It structures a 7-dimensional feature space per batch entry, reserving slots for the actual
     * head direction, target objective coordinates, targeted alignment vectors, and tracking status flags.
     * </p>
     *
     * @param states an array of current execution environment states
     * @return a 2D float {@link INDArray} matrix mapping state batches to sensor head features
     */
    private INDArray headSignal(MacroActionsState[] states) {
        int n = states.length;
        INDArray signals = Nd4j.zeros(n, HEAD_SIGNAL_SIZE).castTo(DataType.FLOAT);
        for (int i = 0; i < n; i++) {
            WorldModel world = states[i].worldModel();
            RobotStatus robot = world.robotStatus();
            double maxDistance = robot.robotSpec().maxRadarDistance();
            WheellyLidarMessage message = robot.lidarMessage();

            double sinHeadDir = message.headDirection().sin();
            double front = clamp(cm2m(message.frontDistance()) / maxDistance, 0, 1);
            double rear = clamp(cm2m(message.rearDistance()) / maxDistance, 0, 1);
            Point2D egoTarget = robot.egocentric(message.target());
            double xTarget = clamp(egoTarget.getX() / maxDistance, -1f, 1f);
            double yTarget = clamp(egoTarget.getY() / maxDistance, -1f, 1f);

            Angle egoTargetDir = message.headTarget().sub(robot.direction());
            double sinHeadTarget = egoTargetDir.sin();
            double cosHeadTarget = egoTargetDir.cos();

            signals.putScalar(i, 0, sinHeadDir);
            signals.putScalar(i, 1, front);
            signals.putScalar(i, 2, rear);
            signals.putScalar(i, 3, xTarget);
            signals.putScalar(i, 4, yTarget);
            signals.putScalar(i, 5, sinHeadTarget);
            signals.putScalar(i, 6, cosHeadTarget);
            signals.putScalar(i, 7 + message.trackingState().ordinal(), 1);
        }
        return signals;
    }

    /**
     * Processes and structures the multichannel grid map data into a 4D ND4J tensor.
     * <p>
     * This method maps the discrete spatial attributes of the radar grid across a multi-layered
     * channel architecture. The base layer channels represent categorical cell states (unknown, empty,
     * contact, and echogenic echoes). Subsequent channels are dynamically appended to encode the
     * egocentric, relative spatial positions of tracking markers, transforming their world coordinates
     * relative to the robot's heading and map topology.
     * </p>
     *
     * @param states a varargs array of current execution environment states
     * @return a 4D float {@link INDArray} matrix with dimensions {@code [batchSize, channels, width, height]}
     */
    INDArray mapSignals(MacroActionsState... states) {
        int n = states.length;
        long numMarkers = markers.size();
        long numChannels = NUM_CELL_STATES + numMarkers;
        WorldModel model = states[0].worldModel();
        GridMap map = model.gridMap();
        int width = map.topology().width();
        int height = map.topology().height();
        INDArray mapSignals = Nd4j.zeros(n, numChannels, width, height).castTo(DataType.FLOAT);

        for (int k = 0; k < n; k++) {
            model = states[k].worldModel();
            RobotStatus robotStatus = model.getRobotStatus();
            Point2D robotLocation = robotStatus.location();
            map = model.gridMap();
            Angle mapDir = map.direction();

            // Create marker state signals
            Map<Integer, FiducialMarker> markerMap = model.markers();

            MapCell[] cells = map.cells();
            int idx = 0;
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    MapCell cell = cells[idx++];
                    if (cell.empty()) {
                        mapSignals.putScalar(new int[]{k, EMPTY_CHANNEL, i, j}, 1);
                    } else if (cell.hasContact()) {
                        mapSignals.putScalar(new int[]{k, CONTACT_CHANNEL, i, j}, 1);
                    } else if (cell.echogenic()) {
                        mapSignals.putScalar(new int[]{k, ECHO_CHANNEL, i, j}, 1);
                    } else {
                        mapSignals.putScalar(new int[]{k, UNKNOWN_CHANNEL, i, j}, 1);
                    }
                }
            }
            for (int i = 0; i < markers.size(); i++) {
                String label = markers.get(i);
                FiducialMarker marker = markerMap.get(label);
                if (marker != null) {
                    Point2D location = marker.location();
                    Angle cellMapDir = Angle.direction(robotLocation, location).sub(mapDir);
                    Point2D cellMapLocation = cellMapDir.at(new Point2D.Double(), location.distance(robotLocation));
                    idx = map.topology().indexOf(cellMapLocation);
                    if (idx >= 0) {
                        int x = idx % width;
                        int y = idx / width;
                        mapSignals.putScalar(new int[]{k, i + NUM_CELL_STATES, y, x}, 1);
                    }
                }
            }
        }
        return mapSignals;
    }

    /**
     * Processes and formats proximity telemetry, contact sensor data, and obstacle flags into an ND4J tensor.
     * <p>
     * This method builds a 6-dimensional feature vector per batch entry. It normalises raw front and rear
     * range-finder data based on the maximum hardware radar distance, caps the resulting values at a maximum
     * threshold of {@code 1}, and couples them with binary indicators for physical bumper events and directional
     * locomotion clearances.
     * </p>
     *
     * @param states an array of current execution environment states
     * @return a 2D float {@link INDArray} matrix mapping state batches to proximity and contact features
     */
    private INDArray proxySignal(MacroActionsState[] states) {
        int n = states.length;
        INDArray signals = Nd4j.zeros(n, PROXY_SIGNAL_SIZE).castTo(DataType.FLOAT);
        for (int i = 0; i < n; i++) {
            WorldModel world = states[i].worldModel();
            RobotStatus robot = world.robotStatus();

            double canMoveForward = robot.canMoveForward() ? 1 : 0;
            double canMoveBackward = robot.canMoveBackward() ? 1 : 0;

            double frontSensor = robot.frontSensor() ? 1 : 0;
            double rearSensor = robot.rearSensor() ? 1 : 0;
            signals.putScalar(i, 0, frontSensor);
            signals.putScalar(i, 1, rearSensor);
            signals.putScalar(i, 2, canMoveForward);
            signals.putScalar(i, 3, canMoveBackward);
        }
        return signals;
    }

    /**
     * Processes and formats the robot chassis kinematics and spatial odometry features into an ND4J tensor.
     * <p>
     * This method builds an 11-dimensional feature space per batch entry. It normalises linear and
     * rotational velocities based on differential wheel pulse telemetry (PPS) relative to the maximum
     * hardware capability, and factors in the current halt state indicators. To prevent phase boundaries
     * discontinuities, the relative mapping heading is decomposed into its continuous sine and cosine trigonometric
     * components, leaving placeholder configurations for motion goals and tracking targets.
     * </p>
     *
     * @param states a varargs array of current execution environment states
     * @return a 2D float {@link INDArray} matrix mapping state batches to chassis kinematic features
     */
    private INDArray robotSignal(MacroActionsState... states) {
        int n = states.length;
        INDArray signals = Nd4j.zeros(n, 12).castTo(DataType.FLOAT);
        for (int i = 0; i < n; i++) {
            WorldModel world = states[i].worldModel();
            GridMap map = world.gridMap();
            RobotStatus robot = world.robotStatus();
            RobotSpec spec = robot.robotSpec();
            double targetRange = spec.maxRadarDistance();
            double leftSpeed = robot.leftPps();
            double rightSpeed = robot.rightPps();
            double linSpeed = (leftSpeed + rightSpeed) / 2 / spec.maxSpeed();
            double rotSpeed = (leftSpeed - rightSpeed) / 2 / spec.maxSpeed();

            Angle mapDir = map.direction().sub(robot.direction());
            double sinMap = mapDir.sin();
            double cosMap = mapDir.cos();

            int move = robot.motionMessage().status().ordinal();

            Point2D egoTarget = robot.egocentric(robot.motionMessage().target());
            double xTarget = clamp(egoTarget.getX() / targetRange, -1, 1);
            double yTarget = clamp(egoTarget.getY() / targetRange, -1, 1);

            Angle egoTargetDir = robot.motionMessage().targetDir().sub(robot.direction());

            double sinTargetRot = egoTargetDir.sin();
            double cosTargetRot = egoTargetDir.cos();

            signals.putScalar(i, 0, linSpeed);
            signals.putScalar(i, 1, rotSpeed);
            signals.putScalar(i, 2, sinMap);
            signals.putScalar(i, 3, cosMap);
            signals.putScalar(i, CAMERA_SIGNAL_BASE_SIZE + move, 1);
            signals.putScalar(i, 8, xTarget);
            signals.putScalar(i, 9, yTarget);
            signals.putScalar(i, 10, sinTargetRot);
            signals.putScalar(i, 11, cosTargetRot);
        }
        return signals;
    }

    /**
     * Extracts and aggregates the complete multi-input feature dictionary from a batch
     * of environment states.
     * <p>
     * This method orchestrates the full state feature extraction pipeline, processing raw
     * telemetry into independent ND4J-backed {@link ArraySignal} instances. It centralises
     * chassis kinematics, proximity metrics, sensor head tracking vectors, optical marker features,
     * concurrent FSM indicators, and multichannel grid maps into an unmodifiable map layout
     * matching the environment's state specification.
     * </p>
     *
     * @param states a varargs array of current execution environment states to be processed
     * @return a {@link Map} pairing signal identifier keys to their respective encapsulated {@link Signal} vectors
     */
    @Override
    public Map<String, Signal> signals(MacroActionsState... states) {
        return Map.of(
                ROBOT_SIGNAL_ID, new ArraySignal(robotSignal(states)),
                PROXY_SIGNAL_ID, new ArraySignal(proxySignal(states)),
                HEAD_SIGNAL_ID, new ArraySignal(headSignal(states)),
                CAMERA_SIGNAL_ID, new ArraySignal(cameraSignal(states)),
                FSM_SIGNAL_ID, new ArraySignal(fsmSignal(states)),
                MAP_SIGNAL_ID, new ArraySignal(mapSignals(states)));
    }

    /**
     * Gets the full multi-input signal specification layout dictionary.
     * <p>
     * This method exposes the immutable structural definitions, shape boundaries, and data types
     * for each independent sub-signal tensor required by the state function pipeline, ensuring
     * strict compatibility with the reinforcement learning environment configuration.
     * </p>
     *
     * @return a {@link Map} pairing signal identifier keys to their respective {@link SignalSpec} structures
     */
    @Override
    public Map<String, SignalSpec> spec() {
        return spec;
    }
}
