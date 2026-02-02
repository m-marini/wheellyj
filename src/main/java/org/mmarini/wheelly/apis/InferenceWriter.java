/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.Map;

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
        return write(camera.simulationTime())
                .write(camera.qrCode())
                .write(camera.width())
                .write(camera.height());
    }

    /**
     * Writes camera events
     *
     * @param camera the camera event
     */
    default <T extends InferenceWriter> T write(CorrelatedCameraEvent camera) throws IOException {
        return write(camera.camerEvent())
                .write(camera.lidar());
    }

    /**
     * Writes robot commands
     *
     * @param commands the commands
     */
    default <T extends InferenceWriter> T write(RobotCommands commands) throws IOException {
        write(commands.scan());
        write(commands.scanDirection())
                .write(commands.move())
                .write(commands.halt());
        return write(commands.moveDirection())
                .write(commands.speed());
    }

    /**
     * Writes the contact message
     *
     * @param contacts the contact message
     */
    default <T extends InferenceWriter> T write(WheellyContactsMessage contacts) throws IOException {
        return write(contacts.simulationTime())
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
    default <T extends InferenceWriter> T write(Map<String, LabelMarker> markers) throws IOException {
        write(markers.size());
        for (LabelMarker marker : markers.values()) {
            write(marker.label());
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
        return write(motion.simulationTime())
                .write((float) motion.xPulses())
                .write((float) motion.yPulses())
                .write(motion.directionDeg())
                .write((float) motion.leftPps())
                .write((float) motion.rightPps())
                .write(motion.imuFailure())
                .write(motion.halt())
                .write(motion.leftTargetPps())
                .write(motion.rightTargetPps())
                .write(motion.leftPower())
                .write(motion.rightPower());
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
     * @param model    the model
     * @param commands the commands
     */
    default <T extends InferenceWriter> T write(WorldModel model, RobotCommands commands) throws IOException {
        return write(model)
                .write(commands);
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
        write(status.simulationTime());
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
    default <T extends InferenceWriter> T write(Complex angle) throws IOException {
        return write(angle.toIntDeg());
    }

    /**
     * Returns the lidar message
     */
    default <T extends InferenceWriter> T write(WheellyLidarMessage message) throws IOException {
        return write(message.simulationTime())
                .write(message.headDirectionDeg())
                .write(message.frontDistance())
                .write(message.rearDistance())
                .write(message.xPulses())
                .write(message.yPulses())
                .write(message.robotYawDeg());
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
        return write(spec.headFOV());
    }

    /**
     * Writes the world spec
     *
     * @param spec the world spec
     */
    default <T extends InferenceWriter> T write(WorldModelSpec spec) throws IOException {
        return write(spec.robotSpec())
                .write(spec.numSectors())
                .write(spec.robotMapSize());
    }

    /**
     * Write the header of inference file
     *
     * @param spec     the world spec
     * @param topology the grid topology
     */
    default <T extends InferenceWriter> T writeHeader(WorldModelSpec spec, GridTopology topology) throws IOException {
        return write(spec).write(topology);
    }
}
