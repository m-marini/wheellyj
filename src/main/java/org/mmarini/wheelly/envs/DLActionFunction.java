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

import org.mmarini.Tuple2;
import org.mmarini.Utils;
import org.mmarini.rl.envs.ArraySignal;
import org.mmarini.rl.envs.IntSignalSpec;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.wheelly.apis.*;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;

/**
 * Converts action signals to robot commands and vice versa.
 * <p>
 * This record facilitates the translation between reinforcement learning deep learning
 * action spaces and concrete physical instructions for the robot, helping to optimise
 * and standardise movement and sensor behaviours.
 * </p>
 *
 * @param spec             the signal specification defining the structure of action spaces
 * @param numRotations     the total number of discrete robot rotations available
 * @param numHeadRotations the total number of discrete sensor head rotations available
 * @param indicesMap       the ordered list mapping action indices to spatial target coordinates
 */
public record DLActionFunction(Map<String, SignalSpec> spec, int numRotations, int numHeadRotations,
                               List<Point2D> indicesMap) implements ActionFunction {
    public static final String MOVE_ACTION_ID = "move";
    public static final String HEAD_ACTION_ID = "head";
    private static final Logger logger = LoggerFactory.getLogger(DLActionFunction.class);

    /**
     * Creates and initialises a deep learning action function instance.
     * <p>
     * This method initialises the action specifications based on the total number of
     * available combinations for moving and positioning the sensor head.
     * </p>
     *
     * @param numRotations     the number of discrete robot rotations
     * @param numHeadRotations the number of discrete sensor head rotations
     * @param map              the list of target points used to characterisationalise movement options
     * @return a fully initialised {@code DLActionFunction} instance
     */
    public static DLActionFunction create(int numRotations, int numHeadRotations,
                                          List<Point2D> map) {
        // each target point can be reached moving forward or backward
        int numMoveActions = map.size() * 2 + numRotations + 1;
        Map<String, SignalSpec> spec = Map.of(
                MOVE_ACTION_ID, new IntSignalSpec(new long[]{1}, numMoveActions),
                HEAD_ACTION_ID, new IntSignalSpec(new long[]{1}, numHeadRotations)
        );
        return new DLActionFunction(spec, numRotations, numHeadRotations, map);
    }

    /**
     * Validates and constructs the {@code DLActionFunction} record.
     * <p>
     * This constructor ensures that all structural parameters are properly synchronised
     * and that non-null constraints are rigorously enforced upon initialisation.
     * </p>
     *
     * @throws NullPointerException if {@code spec} or {@code indicesMap} is null
     */
    public DLActionFunction {
        requireNonNull(spec);
        requireNonNull(indicesMap);
        logger.atDebug().log("Created");
    }

    /**
     * Generates the action masks based on the current environment states and target commands.
     * <p>
     * This method reviews the historical states and intended commands to return an
     * initialised matrix representation of selected actions.
     * </p>
     *
     * @param states   the historical list of environment world models
     * @param commands the matching list of target robot commands
     * @return a map containing the NDArray action masks for movement and head rotation
     */
    public Map<String, INDArray> actionMasks(List<WorldModel> states, List<RobotCommand> commands) {
        int n = min(states.size(), commands.size());
        long numMoves = ((IntSignalSpec) spec.get(MOVE_ACTION_ID)).numValues();
        long numHeads = ((IntSignalSpec) spec.get(HEAD_ACTION_ID)).numValues();
        INDArray moveAction = Nd4j.zeros(DataType.FLOAT, n, numMoves);
        INDArray headAction = Nd4j.zeros(DataType.FLOAT, n, numHeads);
        for (int i = 0; i < n; i++) {
            RobotCommand cmd = commands.get(i);
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
     * Converts a specific robot command and world state into action signals.
     *
     * @param commands the current target robot command to encode
     * @param model    the current status and model of the world environment
     * @return a map containing the corresponding move and head signals
     */
    public Map<String, Signal> actions(RobotCommand commands, WorldModel model) {
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

    /**
     * Decodes multi-channel action signals into a sequential list of concrete robot commands.
     *
     * @param actions the map containing movement and head orientation signals
     * @param states  the current environment world models matching the action sequences
     * @return a reconstructed list of executable {@code RobotCommand} objects
     */
    @Override
    public List<RobotCommand> commands(Map<String, Signal> actions, WorldModel... states) {
        List<RobotCommand> result = new ArrayList<>();
        INDArray heads = requireNonNull(actions.get(HEAD_ACTION_ID)).toINDArray();
        INDArray moves = requireNonNull(actions.get(MOVE_ACTION_ID)).toINDArray();
        int n = (int) min(states.length, min(moves.size(0), heads.size(0)));
        for (int i = 0; i < n; i++) {
            int moveIdx = moves.getInt(i, 0);
            int headIdx = heads.getInt(i, 0);
            WorldModel model = states[i];
            RobotCommand cmd = decodeCommand(headIdx, moveIdx, model);
            result.add(cmd);
        }
        return result;
    }

    /**
     * Decodes the specific action indices into a single unified robot command.
     * <p>
     * This method evaluates behavioural logic paths to select whether to halt,
     * rotate, or advance the chassis forward or backward.
     * </p>
     *
     * @param headIdx the discrete sensor head rotation index
     * @param moveIdx the discrete locomotion command index
     * @param model   the current world model for spatial awareness context
     * @return the resolved {@code RobotCommand} instruction
     */
    RobotCommand decodeCommand(int headIdx, int moveIdx, WorldModel model) {
        Complex headDir = headAngle(headIdx, model);
        if (isHalt(moveIdx)) {
            return RobotCommand.halt(headDir);
        } else if (isRotate(moveIdx)) {
            Complex mapDir = model.gridMap().direction();
            Complex rotDir = rotation(moveIdx).add(mapDir);
            return RobotCommand.rotate(rotDir, headDir);
        } else if (isForward(moveIdx)) {
            Point2D target = target(moveIdx, model.gridMap());
            return RobotCommand.forward(target, headDir);
        } else {
            Point2D target = target(moveIdx, model.gridMap());
            return RobotCommand.backward(target, headDir);
        }
    }

    /**
     * Calculates the absolute head direction relative to the robot's hardware limits.
     * <p>
     * This method handles the angular conversions and ensures the target direction
     * is clamped within the sensor's physical field of view.
     * </p>
     *
     * @param headIndex the target head action index
     * @param model     the current world state containing robot positioning and specifications
     * @return a {@code Complex} angular representation of the safe target head direction
     */
    Complex headAngle(int headIndex, WorldModel model) {
        Complex headRelAngle = headAngle(headIndex);
        Complex absoluteDirection = headRelAngle.add(model.gridMap().direction());
        Complex sensDir = absoluteDirection.sub(model.robotStatus().direction());
        int headMaxDeg = model.robotStatus().robotSpec().headFOV().toIntDeg() / 2;
        return Complex.fromDeg(clamp(sensDir.toIntDeg(), -headMaxDeg, headMaxDeg));
    }
    /**
     * Extrapolates the nominal sensor head angle from its mapped action index.
     *
     * @param headIndex the discrete head action index
     * @return a {@code Complex} angular offset relative to the base map direction
     */
    Complex headAngle(int headIndex) {
        int i = headIndex - (numHeadRotations - 1) / 2;
        double deg = i * 180.0 / (numHeadRotations - 1);
        return Complex.fromDeg(deg);
    }

    /**
     * Identifies the appropriate head command index extracted from the robot instructions.
     * <p>
     * This method evaluates the current posture and orientation of the hardware to
     * synchronise the sensor payload with the grid framework.
     * </p>
     *
     * @param commands the current target robot command to process
     * @param model    the current status and model of the world environment
     * @return the resolved index representing the target head rotation
     */
    int headIndex(RobotCommand commands, WorldModel model) {
        // hr = hd - md + rd
        HeadStatus headStatus = commands.headStatus();
        Complex headRelAngle = headStatus.direction()
                .sub(model.gridMap().direction())
                .add(model.robotStatus().direction());
        return headIndex(headRelAngle);
    }

    /**
     * Resolves the matching head command index for a specified complex angle.
     * <p>
     * The continuous angular input is discretised and clamped to guarantee that the
     * returned index is constrained within valid array boundaries.
     * </p>
     *
     * @param angle the specific target head angle to transform
     * @return the bounded integer index for the head action
     */
    int headIndex(Complex angle) {
        double idx1 = (angle.toDeg() + 90) * (numHeadRotations - 1) / 180;
        int idx = (int) round(idx1);
        return clamp(idx, 0, numHeadRotations - 1);
    }

    /**
     * Determines whether the specified command index represents a forward movement action.
     *
     * @param commandIndex the internal action index to evaluate
     * @return {@code true} if the index is categorised as a forward travel command;
     *         {@code false} otherwise
     */
    boolean isForward(int commandIndex) {
        return commandIndex >= numRotations + 1 &&
                commandIndex < numRotations + 1 + indicesMap.size();
    }

    /**
     * Determines whether the specified command index represents a halt action.
     *
     * @param commandIndex the internal action index to evaluate
     * @return {@code true} if the index matches the halt instruction identifier;
     *         {@code false} otherwise
     */
    boolean isHalt(int commandIndex) {
        return commandIndex == 0;
    }

    /**
     * Determines whether the specified command index represents a rotational movement action.
     *
     * @param commandIndex the internal action index to evaluate
     * @return {@code true} if the index falls into the designated rotation range;
     *         {@code false} otherwise
     */
    boolean isRotate(int commandIndex) {
        return commandIndex >= 1 && commandIndex <= numRotations;
    }

    /**
     * Extracts and computes the locomotion command index from the specified robot instruction.
     * <p>
     * This evaluates the execution paths of the underlying motion subsystem status to
     * determine whether the vehicle is stationary, rotating, or advancing.
     * </p>
     *
     * @param command the target robot command to decode
     * @param model   the current world model providing environmental spatial context
     * @return the computed integer identifier corresponding to the target action index
     */
    int moveIndex(RobotCommand command, WorldModel model) {
        MotionStatus motionStatus = command.motionStatus();
        return switch (motionStatus.status()) {
            case ROTATE -> rotationIndex(motionStatus.targetDir()
                    .sub(model.gridMap().direction())) + 1;
            case FORWARD -> targetIndex(motionStatus.target(), model) + numRotations + 1;
            case BACKWARD -> targetIndex(motionStatus.target(), model) + numRotations + 1 + indicesMap.size();
            default -> 0;
        };
    }

    /**
     * Computes the rotational direction corresponding to a given action index.
     *
     * @param commandIndex the discrete rotational action index to evaluate
     * @return a {@code Complex} angular representation of the rotation path in radians
     */
    Complex rotation(int commandIndex) {
        int dirIdx = commandIndex - 1;
        double rad = dirIdx * PI * 2 / numRotations;
        return Complex.fromRad(rad);
    }

    /**
     * Resolves the discrete rotation command index for a specified direction angle.
     *
     * @param direction the target angular displacement to evaluate
     * @return the matching normalised index for the rotation array
     */
    int rotationIndex(Complex direction) {
        double idx1 = (direction.toDeg() + 360) * numRotations / 360;
        return (int) round(idx1) % numRotations;
    }

    /**
     * Retrieves the structural signal specification map defined for this action processor.
     *
     * @return the map containing named key entries coupled with their {@code SignalSpec}
     */
    @Override
    public Map<String, SignalSpec> spec() {
        return spec;
    }


    /**
     * Computes the absolute coordinates of a target point given a local command index.
     * <p>
     * This method applies affine transformations based on the coordinate framework
     * and the spatial orientation of the map centre.
     * </p>
     *
     * @param moveIdx the discrete movement command index to transform
     * @param gridMap the map layout specifying reference coordinates and headings
     * @return the transformed {@code Point2D} in absolute world coordinates
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
     * Resolves the local relative target coordinate matching the specified command index.
     *
     * @param commandIndex the locomotion command index to process
     * @return the relative {@code Point2D} coordinate offset from the index map
     */
    Point2D target(int commandIndex) {
        int targetIdx = (commandIndex - 1 - numRotations) % indicesMap.size();
        return indicesMap.get(targetIdx);
    }

    /**
     * Calculates the internal target index matching an absolute world coordinate point.
     * <p>
     * It maps the raw spatial location back onto the internal grid coordinate systems
     * by applying inverse rotational transformations.
     * </p>
     *
     * @param target the absolute world target location to map
     * @param model  the current world state configuration context
     * @return the calculated index matching the spatial target point
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
     * Locates the index of the closest registered coordinate point relative to the target tracking path.
     *
     * @param target the target reference coordinate point
     * @return the index corresponding to the nearest point found, or {@code -1} if none exists
     */
    int targetIndex(Point2D target) {
        return Utils.zipWithIndex(indicesMap)
                .min(Comparator.comparingDouble(a -> a._2.distance(target)))
                .map(Tuple2::getV1)
                .orElse(-1);
    }
}
