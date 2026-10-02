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

package org.mmarini.wheelly.envs;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.rl.envs.*;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.fsm.MoveActionId;
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

import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 * Computes and structures the multi-input state feature vectors for Deep Learning
 * macro-actions within the Wheelly environment.
 * <p>
 * This state function fragments raw sensory data into specialised, independent sub-signal
 * vectors including robot kinematic data, proximity buffers, sensor head telemetry,
 * web camera visuals, concurrent finite state machine actions, and an occupancy grid map grid.
 * </p>
 */
public class DLMacroActionStateFunction implements StateFunction {

    /**
     * Identifier key for the grid map multidimensional signal.
     */
    public static final String MAP_SIGNAL_ID = "map";

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
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/state-func-dl-macro-action-schema-0.1";

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

    private static final Logger logger = LoggerFactory.getLogger(DLMacroActionStateFunction.class);

    /**
     * Creates a new state function configured with the specified world bounds and
     * environmental tracking markers.
     *
     * @param worldSpec the structural world limits and specification profile
     * @param markers   the explicit list of unique optical marker labels
     * @return a newly initialised {@link DLMacroActionStateFunction} instance
     */
    public static DLMacroActionStateFunction create(WorldModelSpec worldSpec, List<String> markers) {
        Map<String, SignalSpec> spec = createSpec(worldSpec, markers.size());
        return new DLMacroActionStateFunction(markers, spec);
    }

    /**
     * Parses and constructs a state function provider mapping from a JSON configuration block.
     *
     * @param root    the root JSON document node containing environment parameters
     * @param locator the structural locator pattern path targeting the function properties
     * @return a functional lambda mapper waiting to build the state engine from a {@link WorldModelSpec}
     * @throws IOException if schema validation rules fail or data reading is disrupted
     */
    public static Function<WorldModelSpec, StateFunction> create(JsonNode root, Locator locator) throws IOException {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        List<String> markers = locator.path(MARKER_LABELS_ID).elements(root)
                .map(l -> l.getNode(root).asText())
                .toList();
        return spec -> DLMacroActionStateFunction.create(spec, markers);
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
                ROBOT_SIGNAL_ID, new FloatSignalSpec(new long[]{11}, -1f, 1f),
                PROXY_SIGNAL_ID, new FloatSignalSpec(new long[]{6}, 0, 1f),
                HEAD_SIGNAL_ID, new FloatSignalSpec(new long[]{7}, -1f, 1f),
                CAMERA_SIGNAL_ID, new FloatSignalSpec(new long[]{4 + numMarkers}, -1f, 1f),
                FSM_SIGNAL_ID, new IntSignalSpec(new long[]{22}, 2),
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
    public DLMacroActionStateFunction(List<String> markers, Map<String, SignalSpec> spec) {
        this.markers = requireNonNull(markers);
        this.spec = requireNonNull(spec);
        logger.atDebug().log("Created");
    }

    /**
     * Processes and formats optical telemetry into an ND4J tensor representing the camera signal vector.
     *
     * @param states an array of current execution environment states
     * @return a 2D float {@link INDArray} matrix mapping state batches to optical features
     */
    private INDArray cameraSignal(EnvState[] states) {
        int n = states.length;
        INDArray signals = Nd4j.zeros(n, 4 + markers.size()).castTo(DataType.FLOAT);
        for (int i = 0; i < n; i++) {
            WorldModel world = states[i].worldModel();
            RobotStatus robot = world.robotStatus();
            CorrelatedCameraEvent correlatedCameraEvent = robot.cameraEvent();

            double sinMarker = 0;
            double cosMarker = 1;

            double correlation = correlatedCameraEvent.cameraTime() >= correlatedCameraEvent.lidarTime() ? 1 : 0;
            int qrCodeIdx = markers.indexOf(correlatedCameraEvent.qrCode());

            signals.putScalar(i, 0, sinMarker);
            signals.putScalar(i, 1, cosMarker);
            signals.putScalar(i, 2, correlation);
            signals.putScalar(i, 4 + qrCodeIdx, 1);
        }
        return signals;
    }

    /**
     * Assembles the multi-region concurrent FSM active action contexts into a tensor signal.
     *
     * @param states an array of current execution environment states
     * @return a 2D float {@link INDArray} matrix encoding active locomotion and perception primitives
     */
    private INDArray fsmSignal(EnvState[] states) {
        int n = states.length;
        INDArray signals = Nd4j.zeros(n, 22).castTo(DataType.FLOAT);
        for (int i = 0; i < n; i++) {
            if (states[i] instanceof MacroEnvState state) {

                int moveActionIdx = state.moveActionId().ordinal();
                int headActionIdx = state.headActionId().ordinal();

                signals.putScalar(i, moveActionIdx, 1);
                signals.putScalar(i, MoveActionId.values().length + headActionIdx, 1);
            } else {
                throw new IllegalArgumentException("state must be a MacroEnvState");
            }
        }
        return signals;
    }


    /**
     * Extracts and normalises sensor head metrics across environment state batches
     * to compile the independent head features tensor.
     * <p>
     * This method builds a 2D float tensor mapping each batch slice to 7 continuous
     * egocentric features: the head's current angular direction (Sine/Cosine), the relative
     * Cartesian coordinates of the target tracked point (X/Y), the target relative rotation
     * of the head (Sine/Cosine), and the binary active tracking operational status flag.
     * </p>
     *
     * @param states an array of sequential {@link EnvState} objects containing world models
     * @return a 2D float {@link INDArray} matrix of shape {@code [batch_size, 7]}
     * matching the {@code head} multi-input signal specification
     */
    private INDArray headSignal(EnvState[] states) {
        int n = states.length;
        INDArray signals = Nd4j.zeros(n, 7).castTo(DataType.FLOAT);
        for (int i = 0; i < n; i++) {
            WorldModel world = states[i].worldModel();
            RobotStatus robot = world.robotStatus();

            double sinHeadDir = robot.headDirection().sin();
            double cosHeadDir = robot.headDirection().cos();

            // TODO implement head target location
            double xTarget = 0;
            double yTarget = 0;

            // TODO implement head rotation
            double sinHeadTarget = 0;
            double cosHeadTarget = 1;

            // TODO implement head tracking flag
            double headTracking = 0;

            signals.putScalar(i, 0, sinHeadDir);
            signals.putScalar(i, 1, cosHeadDir);
            signals.putScalar(i, 2, xTarget);
            signals.putScalar(i, 3, yTarget);
            signals.putScalar(i, 4, sinHeadTarget);
            signals.putScalar(i, 5, cosHeadTarget);
            signals.putScalar(i, 6, headTracking);
        }
        return signals;
    }

    /**
     * Compiles grid occupancy frames, tactile obstructions, radar echoes, and relative
     * marker coordinates into a multi-channel multidimensional grid tensor.
     * <p>
     * For each environmental frame in the batch, this method iterates through the map sectors
     * and assigns binary activations across structural channels based on cell properties
     * (empty, contact, echogenic, or unknown). Additionally, it computes the egocentric
     * transformed locations of recognized markers relative to the robot's pose, mapping
     * their spatial coordinates directly into dedicated target matrix channels.
     * </p>
     *
     * @param states a varargs sequence of current environment frames to process
     * @return a 4D float {@link INDArray} matching the convolutional input layout
     * with shape {@code [batch_size, num_channels, width, height]}
     */
    INDArray mapSignals(EnvState... states) {
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
            Complex mapDir = map.direction();

            // Create marker state signals
            Map<String, LabelMarker> markerMap = model.markers();

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
                LabelMarker marker = markerMap.get(label);
                if (marker != null) {
                    Point2D location = marker.location();
                    Complex cellMapDir = Complex.direction(robotLocation, location).sub(mapDir);
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
     * Extracts and normalises environmental proximity metrics, tactile feedback,
     * and movement hindrances across state batches to build the safety features tensor.
     * <p>
     * This method compiles a 2D float tensor matching the {@code proxy} specification,
     * mapping each batch slice to 6 dedicated signals: scaled distance to front and rear
     * obstructions, binary front and rear tactile bumper contacts, and active forward
     * and backward movement hindrance indicators.
     * </p>
     *
     * @param states an array of sequential {@link EnvState} objects containing world models
     * @return a 2D float {@link INDArray} matrix of shape {@code [batch_size, 6]}
     */
    private INDArray proxySignal(EnvState[] states) {
        int n = states.length;
        INDArray signals = Nd4j.zeros(n, 6).castTo(DataType.FLOAT);
        for (int i = 0; i < n; i++) {
            WorldModel world = states[i].worldModel();
            RobotStatus robot = world.robotStatus();
            RobotSpec spec = robot.robotSpec();

            double frontDistance = min(robot.frontDistance() / spec.maxRadarDistance(), 1);
            double rearDistance = min(robot.rearDistance() / spec.maxRadarDistance(), 1);
            double frontSensor = robot.frontSensor() ? 1 : 0;
            double rearSensor = robot.rearSensor() ? 1 : 0;
            double canMoveForward = robot.canMoveForward() ? 1 : 0;
            double canMoveBackward = robot.canMoveBackward() ? 1 : 0;

            signals.putScalar(i, 0, frontDistance);
            signals.putScalar(i, 1, rearDistance);
            signals.putScalar(i, 2, frontSensor);
            signals.putScalar(i, 3, rearSensor);
            signals.putScalar(i, 4, canMoveForward);
            signals.putScalar(i, 5, canMoveBackward);
        }
        return signals;
    }

    /**
     * Extracts and normalises robot kinematics, local map grid alignments, and navigational
     * movement targets across batches to build the primary robot base feature tensor.
     * <p>
     * This method assembles a 2D float tensor matching the {@code robot} specification.
     * It maps each batch slice to 11 dedicated signals: scaled linear and angular speeds, egocentric
     * map grid orientation (Sine/Cosine), binary halt status, active translation and rotation intent
     * flags, relative Cartesian target coordinates (X/Y), and relative target steering error (Sine/Cosine).
     * </p>
     *
     * @param states a varargs sequence of current environment frames to process
     * @return a 2D float {@link INDArray} matrix of shape {@code [batch_size, 11]}
     */
    private INDArray robotSignal(EnvState... states) {
        int n = states.length;
        INDArray signals = Nd4j.zeros(n, 11).castTo(DataType.FLOAT);
        for (int i = 0; i < n; i++) {
            WorldModel world = states[i].worldModel();
            GridMap map = world.gridMap();
            RobotStatus robot = world.robotStatus();
            RobotSpec spec = robot.robotSpec();
            double leftSpeed = robot.leftPps();
            double rightSpeed = robot.rightPps();
            double linSpeed = (leftSpeed + rightSpeed) / 2 / spec.maxSpeed();
            double rotSpeed = (leftSpeed - rightSpeed) / 2 / spec.maxSpeed();

            Complex mapDir = map.direction().sub(robot.direction());
            double sinMap = mapDir.sin();
            double cosMap = mapDir.cos();

            double halt = robot.halt() ? 1 : 0;

            // TODO implement the move status flag
            double move = 0;

            // TODO implement the rot status flag
            double rot = 0;

            // TODO implement the move target signals
            double xTarget = 0;
            double yTarget = 0;

            double sinTargetRot = 0;
            double cosTargetRot = 1;

            signals.putScalar(i, 0, linSpeed);
            signals.putScalar(i, 1, rotSpeed);
            signals.putScalar(i, 2, sinMap);
            signals.putScalar(i, 3, cosMap);
            signals.putScalar(i, 4, halt);
            signals.putScalar(i, 5, move);
            signals.putScalar(i, 6, rot);
            signals.putScalar(i, 7, xTarget);
            signals.putScalar(i, 8, yTarget);
            signals.putScalar(i, 9, sinTargetRot);
            signals.putScalar(i, 10, cosTargetRot);
        }
        return signals;
    }

    /**
     * Compiles and bundles all processed multi-input sub-signals into the final structured
     * state dictionary matching the environment specification.
     * <p>
     * This method evaluates the environment frames using specialized extraction routines
     * and wraps each resulting tensor matrix into an {@link ArraySignal}. It isolates features
     * into dedicated dictionary streams: {@code robot}, {@code proxy}, {@code head},
     * {@code camera}, {@code fsm}, and {@code map}, which are then returned as a single
     * cohesive input map ready for network inference.
     * </p>
     *
     * @param states a varargs sequence of current environment frames to evaluate
     * @return a {@link Map} pairing signal key identifiers to their compiled {@link Signal} representations
     */
    @Override
    public Map<String, Signal> signals(EnvState... states) {
        return Map.of(
                ROBOT_SIGNAL_ID, new ArraySignal(robotSignal(states)),
                PROXY_SIGNAL_ID, new ArraySignal(proxySignal(states)),
                HEAD_SIGNAL_ID, new ArraySignal(headSignal(states)),
                CAMERA_SIGNAL_ID, new ArraySignal(cameraSignal(states)),
                FSM_SIGNAL_ID, new ArraySignal(fsmSignal(states)),
                MAP_SIGNAL_ID, new ArraySignal(mapSignals(states)));
    }

    /**
     * Returns the comprehensive multi-input signal specification layout dictionary
     * defining the expected shapes and data types for the state features.
     *
     * @return a {@link Map} pairing signal key identifiers to their corresponding
     * {@link SignalSpec} configurations
     */
    @Override
    public Map<String, SignalSpec> spec() {
        return spec;
    }
}
