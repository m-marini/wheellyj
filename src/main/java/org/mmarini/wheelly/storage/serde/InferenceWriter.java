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

package org.mmarini.wheelly.storage.serde;

import org.mmarini.wheelly.behaviours.BehaviourAction;
import org.mmarini.wheelly.behaviours.BehaviourState;
import org.mmarini.wheelly.behaviours.HeadActionId;
import org.mmarini.wheelly.behaviours.MoveActionId;
import org.mmarini.wheelly.control.RobotStatus;
import org.mmarini.wheelly.perception.*;
import org.mmarini.wheelly.robots.*;
import org.mmarini.wheelly.utils.Angle;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.Map;

import static org.mmarini.wheelly.robots.HeadStatusId.FIX_DIRECTION;

/**
 * World model dumper
 */
public interface InferenceWriter extends AutoCloseable, DataWriter {
    /**
     * Writes camera events
     *
     * @param camera the camera event
     */
    default <T extends InferenceWriter> T write(CameraEvent camera) throws IOException {
        return write(camera.time())
                .write(camera.markerId())
                .write(camera.width())
                .write(camera.height());
    }

    default <T extends InferenceWriter> T write(MotionStatus status) throws IOException {
        write(status.status());
        return (T) switch (status.status()) {
            case HALT -> this;
            case FORWARD, BACKWARD -> write(status.target());
            case ROTATE -> write(status.targetDir());
        };
    }

    default <T extends InferenceWriter> T write(MotionStatusId status) throws IOException {
        return write(status.ordinal());
    }

    default <T extends InferenceWriter> T write(HeadStatus status) throws IOException {
        write(status.status());
        return FIX_DIRECTION.equals(status.status())
                ? write(status.direction())
                : write(status.target());
    }

    default <T extends InferenceWriter> T write(HeadStatusId status) throws IOException {
        write(status.ordinal());
        return (T) this;
    }

    /**
     * Writes camera events
     *
     * @param camera the camera event
     */
    default <T extends InferenceWriter> T write(CorrelatedCameraEvent camera) throws IOException {
        return write(camera.cameraEvent())
                .write(camera.lidar());
    }

    /**
     * Writes the contact message
     *
     * @param contacts the contact message
     */
    default <T extends InferenceWriter> T write(WheellyContactsMessage contacts) throws IOException {
        return write(contacts.time())
                .write(contacts.frontSensors())
                .write(contacts.rearSensors())
                .write(contacts.canMoveForward())
                .write(contacts.canMoveBackward());
    }

    /**
     * Writes the markers
     *
     * @param markers the markers
     */
    default <T extends InferenceWriter> T write(Map<Integer, FiducialMarker> markers) throws IOException {
        write(markers.size());
        for (FiducialMarker marker : markers.values()) {
            write(marker.markerId());
            write(marker.location())
                    .write((float) marker.weight())
                    .write(marker.markerTime())
                    .write(marker.cleanTime());
        }
        return (T) this;
    }

    /**
     * Writes the motion message
     *
     * @param motion the motion message
     */
    default <T extends InferenceWriter> T write(WheellyMotionMessage motion) throws IOException {
        write(motion.time());
        write((float) motion.xPulses());
        write((float) motion.yPulses());
        write(motion.directionDeg());
        write((float) motion.leftPps());
        write((float) motion.rightPps());
        write(motion.imuFailure());
        write(motion.status());
        write(motion.targetDeg());
        write(motion.leftTargetPps());
        write(motion.rightTargetPps());
        write(motion.leftPower());
        write(motion.rightPower());
        write(motion.xTarget());
        write(motion.yTarget());
        return (T) this;
    }

    /**
     * Writes the radar map
     *
     * @param radarMap the radar map
     */
    default <T extends InferenceWriter> T write(RadarMap radarMap) throws IOException {
        write(radarMap.cleanTimestamp());
        MapCell[] cells = radarMap.cells();
        for (MapCell cell : cells) {
            write(cell.echoTime())
                    .write((float) cell.echoWeight())
                    .write(cell.contactTime());
        }
        return (T) this;
    }

    /**
     * Writes the model
     *
     * @param state the state
     */
    default <T extends InferenceWriter> T write(BehaviourState state) throws IOException {
        return write(state.worldModel())
                .write(state.moveActionId())
                .write(state.headActionId());
    }

    /**
     * Writes the model
     *
     * @param model the model
     */
    default <T extends InferenceWriter> T write(WorldModel model) throws IOException {
        return write(model.robotStatus())
                .write(model.markers())
                .write(model.radarMap());
    }

    /**
     * Writes the status
     *
     * @param status the status
     */
    default <T extends InferenceWriter> T write(RobotStatus status) throws IOException {
        write(status.robotTime());
        return write(status.motionMessage())
                .write(status.contactsMessage())
                .write(status.cameraEvent())
                .write(status.lidarMessage());
    }

    /**
     * Writes the point
     *
     * @param point the point
     */
    default <T extends InferenceWriter> T write(Point2D point) throws IOException {
        return write(point.getX())
                .write(point.getY());
    }

    /**
     * Write the topology
     *
     * @param topology the topology
     */
    default <T extends InferenceWriter> T write(GridTopology topology) throws IOException {
        return write(topology.center())
                .write(topology.width())
                .write(topology.height())
                .write(topology.gridSize());
    }

    /**
     * Writes the angle (int deg)
     *
     * @param angle the robot spec
     */
    default <T extends InferenceWriter> T write(Angle angle) throws IOException {
        return write(angle.toIntDeg());
    }

    /**
     * Returns the lidar message
     */
    default <T extends InferenceWriter> T write(WheellyLidarMessage message) throws IOException {
        write(message.time());
        write(message.headDirectionDeg());
        write(message.frontDistance());
        write(message.rearDistance());
        write(message.xPulses());
        write(message.yPulses());
        write(message.robotYawDeg());
        write(message.trackingState());
        write(message.headTargetDeg());
        write(message.xTarget());
        write(message.yTarget());
        return (T) this;
    }

    /**
     * Writes the robot spec
     *
     * @param spec the robot spec
     */
    default <T extends InferenceWriter> T write(RobotSpec spec) throws IOException {
        write(spec.maxRadarDistance());
        write(spec.lidarFOV())
                .write(spec.contactRadius());
        write(spec.cameraFOV())
                .write(spec.headLocation())
                .write(spec.frontLidarDistance())
                .write(spec.rearLidarDistance())
                .write(spec.cameraDistance());
        write(spec.headFOV())
                .write(spec.targetRange());
        write(spec.directionRange());
        return write(spec.maxRotRange())
                .write(spec.maxRotPps())
                .write(spec.maxSpeed())
                .write(spec.decelerateDistance())
                .write(spec.sendInterval())
                .write(spec.scanInterval());
    }

    /**
     * Writes the world spec
     *
     * @param spec the world spec
     */
    default <T extends InferenceWriter> T write(WorldModelSpec spec) throws IOException {
        return write(spec.robotSpec())
                .write(spec.robotMapSize());
    }

    default <T extends InferenceWriter> T write(MoveActionId value) throws IOException {
        write(value.ordinal());
        return (T) this;
    }

    default <T extends InferenceWriter> T write(HeadActionId value) throws IOException {
        write(value.ordinal());
        return (T) this;
    }

    default <T extends InferenceWriter> T write(BehaviourAction value) throws IOException {
        write(value.moveActionId());
        write(value.headActionId());
        return (T) this;
    }

    default <T extends InferenceWriter> T write(BehaviourState state, BehaviourAction action) throws IOException {
        WorldModel worldModel = state.worldModel();
        writeHeader(worldModel.worldSpec(), worldModel.radarMap().topology())
                .write(state)
                .write(action);
        return (T) this;
    }

    /**
     * Write the header of inference file
     *
     * @param spec     the world spec
     * @param topology the grid topology
     */
    <T extends InferenceWriter> T writeHeader(WorldModelSpec spec, GridTopology topology) throws IOException;

    enum Type {
        RL_MACRO_ACTION
    }
}
