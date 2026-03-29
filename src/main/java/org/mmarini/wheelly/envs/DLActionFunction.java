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
import org.mmarini.Tuple2;
import org.mmarini.Utils;
import org.mmarini.rl.envs.ArraySignal;
import org.mmarini.rl.envs.IntSignalSpec;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.wheelly.apis.*;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;

/**
 * Converts action signals to robot commands and vice versa
 *
 * @param spec             the signal specification
 * @param numRotations     the number of rotations
 * @param numHeadRotations the number of head rotations
 * @param indicesMap       the map of action indices to move action target index (coordinates)
 */
public record DLActionFunction(Map<String, SignalSpec> spec, int numRotations, int numHeadRotations,
                               List<Point2D> indicesMap) implements ActionFunction {
    public static final String MOVE_ACTION_ID = "move";
    public static final String HEAD_ACTION_ID = "head";
    public static final String NUM_ROTATIONS_ID = "numRotations";
    public static final String NUM_HEAD_ROTATIONS_ID = "numHeadRotations";
    public static final String GRID_SIZE_ID = "gridSize";
    public static final String GRID_STEP_ID = "gridStep";
    public static final String HIDE_RADIUS_ID = "hideRadius";
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/action-func-dl-schema-1.0";
    public static final int DEFAULT_NUM_ROTATIONS = 36;
    public static final int DEFAULT_NUM_HEAD_ROTATIONS = 13;
    public static final int DEFAULT_GRID_SIZE = 31;
    public static final double DEFAULT_GRID_STEP = 0.2;
    public static final double DEFAULT_HIDE_RADIUS = 0.2;
    private static final Logger logger = LoggerFactory.getLogger(DLActionFunction.class);

    /**
     * Returns the rl actin function from a JSON doc
     *
     * @param root    the root json doc
     * @param locator the locator
     */
    public static DLActionFunction create(JsonNode root, Locator locator) throws IOException {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        int numRotations = locator.path(NUM_ROTATIONS_ID).getNode(root).asInt(DEFAULT_NUM_ROTATIONS);
        int numHeadRotation = locator.path(NUM_HEAD_ROTATIONS_ID).getNode(root).asInt(DEFAULT_NUM_HEAD_ROTATIONS);
        int gridSize = locator.path(GRID_SIZE_ID).getNode(root).asInt(DEFAULT_GRID_SIZE);
        double gridStep = locator.path(GRID_STEP_ID).getNode(root).asDouble(DEFAULT_GRID_STEP);
        double hideRadius = locator.path(HIDE_RADIUS_ID).getNode(root).asDouble(DEFAULT_HIDE_RADIUS);
        return create(numRotations, numHeadRotation, gridSize, gridStep, hideRadius);
    }

    /**
     * Returns the deep learning action function
     *
     * @param numRotations     the number of robot rotations
     * @param numHeadRotations the number of head rotations
     * @param gridSize         the grid size (number of points along dimensions)
     * @param gridStep         the grid step (m)
     * @param hideRadius       the hide radius (m)
     */
    public static DLActionFunction create(int numRotations, int numHeadRotations, int gridSize, double gridStep, double hideRadius) {
        List<Point2D> indicesMap = createIndicesMap(gridSize, gridStep, hideRadius);
        int numMoveActions = indicesMap.size() * 2 + numRotations + 1;
        Map<String, SignalSpec> spec = Map.of(
                MOVE_ACTION_ID, new IntSignalSpec(new long[]{1}, numMoveActions),
                HEAD_ACTION_ID, new IntSignalSpec(new long[]{1}, numHeadRotations)
        );
        return new DLActionFunction(spec, numRotations, numHeadRotations, indicesMap);
    }

    /**
     * Returns the map of action indices to move action target index (coordinates)
     * It maps the action indices to the move action target indices hiding the area
     * of hidden radius around the centre
     *
     * @param gridSize   the grid size (number of points along dimensions)
     * @param gridStep   the grid step (m)
     * @param hideRadius the hidden radius (m)
     */
    static List<Point2D> createIndicesMap(int gridSize, double gridStep, double hideRadius) {
        List<Point2D> map = new ArrayList<>();
        int o = (gridSize - 1) / 2;
        double hideRadius2 = hideRadius * hideRadius;
        for (int i = 0; i < gridSize; i++) {
            double y = (i - o) * gridStep;
            for (int j = 0; j < gridSize; j++) {
                double x = (j - o) * gridStep;
                if (x * x + y * y > hideRadius2) {
                    map.add(new Point2D.Double(x, y));
                }
            }
        }
        return map;
    }

    /**
     * Creates the DLActionFunction
     *
     */
    public DLActionFunction(Map<String, SignalSpec> spec, int numRotations, int numHeadRotations, List<Point2D> indicesMap) {
        this.spec = requireNonNull(spec);
        this.numRotations = numRotations;
        this.numHeadRotations = numHeadRotations;
        this.indicesMap = requireNonNull(indicesMap);
        logger.atDebug().log("Created");
    }

    /**
     * Returns the action mask
     *
     * @param states   the states
     * @param commands the
     */
    public Map<String, INDArray> actionMasks(List<WorldModel> states, List<RobotCommands> commands) {
        int n = min(states.size(), commands.size());
        long numMoves = ((IntSignalSpec) spec.get(MOVE_ACTION_ID)).numValues();
        long numHeads = ((IntSignalSpec) spec.get(HEAD_ACTION_ID)).numValues();
        INDArray moveAction = Nd4j.zeros(DataType.FLOAT, n, numMoves);
        INDArray headAction = Nd4j.zeros(DataType.FLOAT, n, numHeads);
        for (int i = 0; i < n; i++) {
            RobotCommands cmd = commands.get(i);
            WorldModel model = states.get(i);
            int moveIdx = moveIndex(cmd, model);
            moveAction.putScalar(i, moveIdx, 1);
            int headIdx = headIndex(cmd, model);
            headAction.putScalar(i, headIdx, 1);
        }

        return Map.of(
                MOVE_ACTION_ID, moveAction,
                HEAD_ACTION_ID, headAction
        );
    }

    /**
     * Returns the action signals relative to robot command
     *
     * @param commands the command
     * @param model    the world model (state of environment)
     */
    public Map<String, Signal> actions(RobotCommands commands, WorldModel model) {
        INDArray moveAction = Nd4j.zeros(DataType.FLOAT, 1, 1);
        INDArray headAction = Nd4j.zeros(DataType.FLOAT, 1, 1);
        int moveIdx = moveIndex(commands, model);
        moveAction.putScalar(0, 0, moveIdx);

        int headIdx = headIndex(commands, model);
        headAction.putScalar(0, 0, headIdx);
        return Map.of(
                MOVE_ACTION_ID, new ArraySignal(moveAction),
                HEAD_ACTION_ID, new ArraySignal(headAction)
        );
    }

    @Override
    public List<RobotCommands> commands(Map<String, Signal> actions, WorldModel... states) {
        List<RobotCommands> result = new ArrayList<>();
        INDArray heads = requireNonNull(actions.get(HEAD_ACTION_ID)).toINDArray();
        INDArray moves = requireNonNull(actions.get(MOVE_ACTION_ID)).toINDArray();
        int n = (int) min(states.length, min(moves.size(0), heads.size(0)));
        for (int i = 0; i < n; i++) {
            int moveIdx = moves.getInt(i, 0);
            int headIdx = heads.getInt(i, 0);
            WorldModel model = states[i];
            RobotCommands cmd = decodeCommand(headIdx, moveIdx, model);
            result.add(cmd);
        }
        return result;
    }

    /**
     * Returns the commands for the given head and move index
     *
     * @param headIdx the head rotation command index
     * @param moveIdx the move command index
     * @param model   the world model
     */
    RobotCommands decodeCommand(int headIdx, int moveIdx, WorldModel model) {
        int headDeg = headAngle(headIdx, model);
        if (isHalt(moveIdx)) {
            return RobotCommands.halt(headDeg);
        } else if (isRotate(moveIdx)) {
            Complex mapDir = model.gridMap().direction();
            int rotDeg = rotation(moveIdx).add(mapDir).toIntDeg();
            return RobotCommands.rotate(headDeg, rotDeg);
        } else if (isForward(moveIdx)) {
            Point2D target = target(moveIdx, model.gridMap());
            return RobotCommands.forward(headDeg, target);
        } else {
            Point2D target = target(moveIdx, model.gridMap());
            return RobotCommands.backward(headDeg, target);
        }
    }

    /**
     * Returns the robot relative head direction for the given command
     *
     * @param headIndex the head rotation command index
     * @param model     the world model
     */
    int headAngle(int headIndex, WorldModel model) {
        Complex headRelAngle = headAngle(headIndex);
        Complex absoluteDirection = headRelAngle.add(model.gridMap().direction());
        Complex sensDir = absoluteDirection.sub(model.robotStatus().direction());
        int headMaxDeg = model.robotStatus().robotSpec().headFOV().toIntDeg() / 2;
        return clamp(sensDir.toIntDeg(), -headMaxDeg, headMaxDeg);
    }

    /**
     * Returns the head angle relative the grid map, of the head index
     *
     * @param headIndex the head action index
     */
    Complex headAngle(int headIndex) {
        int i = headIndex - (numHeadRotations - 1) / 2;
        double deg = i * 180.0 / (numHeadRotations - 1);
        return Complex.fromDeg(deg);
    }

    /**
     * Returns the head command index from robot commands
     *
     * @param commands the commands
     * @param model    the world model
     */
    int headIndex(RobotCommands commands, WorldModel model) {
        // hr = hd - md + rd
        Complex headRelAngle = Complex.fromDeg(commands.scanDirection())
                .sub(model.gridMap().direction())
                .add(model.robotStatus().direction());
        return headIndex(headRelAngle);
    }

    /**
     * Returns the head command index
     *
     * @param angle the head angle
     */
    int headIndex(Complex angle) {
        double idx1 = (angle.toDeg() + 90) * (numHeadRotations - 1) / 180;
        int idx = (int) round(idx1);
        return clamp(idx, 0, numHeadRotations - 1);
    }

    /**
     * Returns true if the command index is froward command
     *
     * @param commandIndex the command index
     */
    boolean isForward(int commandIndex) {
        return commandIndex >= numRotations + 1 &&
                commandIndex < numRotations + 1 + indicesMap.size();
    }

    /**
     * Returns true if the command index is halt command
     *
     * @param commandIndex the command index
     */
    boolean isHalt(int commandIndex) {
        return commandIndex == 0;
    }

    /**
     * Returns true if command is rotate command
     *
     * @param commandIndex the command index
     */
    boolean isRotate(int commandIndex) {
        return commandIndex >= 1 && commandIndex <= numRotations;
    }

    /**
     * Returns the head command index from robot commands
     *
     * @param commands the commands
     * @param model    the world model
     */
    int moveIndex(RobotCommands commands, WorldModel model) {
        return switch (commands.status()) {
            case ROTATE -> rotationIndex(Complex.fromDeg(commands.rotationDirection())
                    .sub(model.gridMap().direction())) + 1;
            case FORWARD -> targetIndex(commands.target(), model) + numRotations + 1;
            case BACKWARD -> targetIndex(commands.target(), model) + numRotations + 1 + indicesMap.size();
            default -> 0;
        };
    }

    /**
     * Returns the rotation direction for the given movement action index
     *
     * @param commandIndex the movement action index
     */
    Complex rotation(int commandIndex) {
        int dirIdx = commandIndex - 1;
        double rad = dirIdx * PI * 2 / numRotations;
        return Complex.fromRad(rad);
    }

    /**
     * Returns the rotation command index
     *
     * @param direction the direction angle
     */
    int rotationIndex(Complex direction) {
        double idx1 = (direction.toDeg() + 360) * numRotations / 360;
        return (int) round(idx1) % numRotations;
    }

    @Override
    public Map<String, SignalSpec> spec() {
        return spec;
    }

    /**
     * Returns the absolute target position of the command index
     *
     * @param moveIdx the command index
     * @param gridMap the grid map
     */
    Point2D target(int moveIdx, GridMap gridMap) {
        Point2D relativeTarget = target(moveIdx);
        Point2D mapCentre = gridMap.center();
        AffineTransform tr = AffineTransform.getTranslateInstance(mapCentre.getX(), mapCentre.getY());
        Complex mapDir = gridMap.direction();
        tr.rotate(-mapDir.toRad());
        return tr.transform(relativeTarget, null);
    }

    /**
     * Returns the target relative location of command index
     *
     * @param commandIndex the command index
     */
    Point2D target(int commandIndex) {
        int targetIdx = (commandIndex - 1 - numRotations) % indicesMap.size();
        return indicesMap.get(targetIdx);
    }

    /**
     * Returns the target index for the give absolute target
     *
     * @param target the absolute target
     * @param model  the model
     */
    int targetIndex(Point2D target, WorldModel model) {
        GridMap gridMap = model.gridMap();
        AffineTransform tr = AffineTransform.getRotateInstance(gridMap.direction().toRad());
        Point2D gridCentre = gridMap.center();
        tr.translate(-gridCentre.getX(), -gridCentre.getY());
        Point2D mapTarget = tr.transform(target, null);
        return targetIndex(mapTarget);
    }

    /**
     * Returns the index of the closest point on the grid to the target
     *
     * @param target the head angle
     */
    int targetIndex(Point2D target) {
        return Utils.zipWithIndex(indicesMap)
                .min(Comparator.comparingDouble(a -> a._2.distance(target)))
                .map(Tuple2::getV1)
                .orElse(-1);
    }
}
