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

import org.mmarini.wheelly.protobuf.aruco.MarkerEvent;
import org.mmarini.wheelly.protobuf.inferences.*;
import org.mmarini.wheelly.protobuf.robot.ContactsTelemetry;
import org.mmarini.wheelly.protobuf.robot.LidarTelemetry;
import org.mmarini.wheelly.protobuf.robot.MotionTelemetry;
import org.mmarini.wheelly.robots.WheellyContactsMessage;
import org.mmarini.wheelly.robots.WheellyLidarMessage;
import org.mmarini.wheelly.robots.WheellyMotionMessage;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Serialises and writes AI inference and training records to a binary
 * Protocol Buffers file.
 * <p>
 * Upon receiving the first inference step, this writer automatically opens the
 * destination file and persists a delimited {@link InferenceHeader} containing
 * the world model specifications and grid topology, followed by a sequence of
 * delimited {@link InferenceRecord} entries.
 * </p>
 */
public class InferenceFileWriter implements InferenceWriter {

    /**
     * Creates a new {@code InferenceFileWriter} configured to write to the
     * specified file path.
     *
     * @param file the {@link Path} to the destination Protocol Buffers file
     * @return a new {@link InferenceFileWriter} instance
     * @throws NullPointerException if {@code file} is {@code null}
     */
    public static InferenceFileWriter fromFile(Path file) {
        return new InferenceFileWriter(file);
    }

    /**
     * Builds a Protocol Buffers {@link InferenceHeader} message from the domain
     * world model specification and spatial grid topology.
     *
     * @param worldModelSpec the domain world model specification
     * @param topology       the domain spatial grid topology
     * @return the serialisable protobuf {@link InferenceHeader} message
     */
    private static InferenceHeader toProtoBuf(org.mmarini.wheelly.perception.WorldModelSpec worldModelSpec,
                                              org.mmarini.wheelly.perception.GridTopology topology) {
        GridTopology protoTopology = toProtoBuf(topology);
        WorldModelSpec protoWorldSpec = toProtoBuf(worldModelSpec);
        return InferenceHeader.newBuilder()
                .setTopology(protoTopology)
                .setWorldSpec(protoWorldSpec).build();
    }

    /**
     * Converts a domain {@link org.mmarini.wheelly.perception.WorldModelSpec}
     * into its Protocol Buffers {@link WorldModelSpec} representation.
     *
     * @param worldModelSpec the domain world model specification
     * @return the corresponding protobuf {@link WorldModelSpec} message
     */
    private static WorldModelSpec toProtoBuf(org.mmarini.wheelly.perception.WorldModelSpec worldModelSpec) {
        RobotSpec protoRobotSpec = toProtoBuf(worldModelSpec.robotSpec());
        return WorldModelSpec.newBuilder()
                .setRobotMapSize(worldModelSpec.robotMapSize())
                .setRobotSpec(protoRobotSpec)
                .build();
    }

    /**
     * Converts a domain {@link org.mmarini.wheelly.robots.RobotSpec} into its
     * Protocol Buffers {@link RobotSpec} representation.
     *
     * @param robotSpec the domain robot specification
     * @return the corresponding protobuf {@link RobotSpec} message
     */
    private static RobotSpec toProtoBuf(org.mmarini.wheelly.robots.RobotSpec robotSpec) {
        return RobotSpec.newBuilder()
                .setSendInterval(robotSpec.sendInterval())
                .setScanInterval(robotSpec.scanInterval())
                .setLidarFOV(robotSpec.lidarFOV().toIntDeg())
                .setCameraFOV(robotSpec.cameraFOV().toIntDeg())
                .setHeadFOV(robotSpec.headFOV().toIntDeg())
                .setDirectionRange(robotSpec.directionRange().toIntDeg())
                .setMaxRotRange(robotSpec.maxRotRange().toIntDeg())
                .setMaxRotPps(robotSpec.maxRotPps())
                .setMaxSpeed(robotSpec.maxSpeed())
                .setHeadLocation(toProtoBuf(robotSpec.headLocation()))
                .setMaxRadarDistance((float) robotSpec.maxRadarDistance())
                .setContactRadius((float) robotSpec.contactRadius())
                .setFrontLidarDistance((float) robotSpec.frontLidarDistance())
                .setRearLidarDistance((float) robotSpec.rearLidarDistance())
                .setCameraDistance((float) robotSpec.cameraDistance())
                .setTargetRange((float) robotSpec.targetRange())
                .setDecelerateDistance((float) robotSpec.decelerateDistance())
                .build();
    }

    /**
     * Converts a domain {@link org.mmarini.wheelly.perception.GridTopology} into its
     * Protocol Buffers {@link GridTopology} representation.
     *
     * @param topology the domain grid topology
     * @return the corresponding protobuf {@link GridTopology} message
     */
    private static GridTopology toProtoBuf(org.mmarini.wheelly.perception.GridTopology topology) {
        Point2D centre = toProtoBuf(topology.center());
        return GridTopology.newBuilder()
                .setCenter(centre)
                .setWidth(topology.width())
                .setHeight(topology.height())
                .setGridSize((float) topology.gridSize())
                .build();
    }

    /**
     * Converts a Java AWT {@link java.awt.geom.Point2D} into a Protocol Buffers
     * {@link Point2D} message.
     *
     * @param center the 2D coordinate point
     * @return the corresponding protobuf {@link Point2D} message
     */
    private static Point2D toProtoBuf(java.awt.geom.Point2D center) {
        return Point2D.newBuilder()
                .setX((float) center.getX())
                .setY((float) center.getY())
                .build();
    }

    /**
     * Converts a state-action pair into a Protocol Buffers {@link InferenceRecord}.
     *
     * @param state  the observed domain {@link org.mmarini.wheelly.behaviours.BehaviourState}
     * @param action the executed domain {@link org.mmarini.wheelly.behaviours.BehaviourAction}
     * @return the corresponding protobuf {@link InferenceRecord} message
     */
    private static InferenceRecord toProtoBuf(org.mmarini.wheelly.behaviours.BehaviourState state,
                                              org.mmarini.wheelly.behaviours.BehaviourAction action) {
        return InferenceRecord.newBuilder()
                .setState(toProtoBuf(state))
                .setAction(toProtoBuf(action))
                .build();
    }

    /**
     * Converts a domain {@link org.mmarini.wheelly.behaviours.BehaviourAction} into its
     * Protocol Buffers {@link BehaviourAction} representation.
     *
     * @param action the domain behaviour action
     * @return the corresponding protobuf {@link BehaviourAction} message
     */
    private static BehaviourAction toProtoBuf(org.mmarini.wheelly.behaviours.BehaviourAction action) {
        return BehaviourAction.newBuilder()
                .setHeadActionId(action.headActionId().ordinal())
                .setMoveActionId(action.moveActionId().ordinal())
                .build();
    }

    /**
     * Converts a domain {@link org.mmarini.wheelly.behaviours.BehaviourState} into its
     * Protocol Buffers {@link BehaviourState} representation.
     *
     * @param state the domain behaviour state
     * @return the corresponding protobuf {@link BehaviourState} message
     */
    private static BehaviourState toProtoBuf(org.mmarini.wheelly.behaviours.BehaviourState state) {
        return BehaviourState.newBuilder()
                .setWorldModel(toProtoBuf(state.worldModel()))
                .setHeadActionId(state.headActionId().ordinal())
                .setMoveActionId(state.moveActionId().ordinal())
                .build();
    }

    /**
     * Converts a domain {@link org.mmarini.wheelly.perception.WorldModel} into its
     * Protocol Buffers {@link WorldModel} representation.
     *
     * @param worldModel the domain world model
     * @return the corresponding protobuf {@link WorldModel} message
     */
    private static WorldModel toProtoBuf(org.mmarini.wheelly.perception.WorldModel worldModel) {
        return WorldModel.newBuilder()
                .setRobotStatus(toProtoBuf(worldModel.robotStatus()))
                .setRadarMap(toProtoBuf(worldModel.radarMap()))
                .addAllMarkers(toProtoBuf(worldModel.markers()))
                .build();
    }

    /**
     * Converts a domain {@link org.mmarini.wheelly.control.RobotStatus} into its
     * Protocol Buffers {@link RobotStatus} representation.
     *
     * @param robotStatus the domain robot status snapshot
     * @return the corresponding protobuf {@link RobotStatus} message
     */
    private static RobotStatus toProtoBuf(org.mmarini.wheelly.control.RobotStatus robotStatus) {
        return RobotStatus.newBuilder()
                .setRobotTime(robotStatus.robotTime())
                .setContacts(toProtoBuf(robotStatus.contactsMessage()))
                .setCamera(toProtoBuf(robotStatus.cameraEvent()))
                .setLidars(toProtoBuf(robotStatus.lidarMessage()))
                .setMotion(toProtoBuf(robotStatus.motionMessage()))
                .build();
    }

    /**
     * Converts a domain {@link WheellyMotionMessage} into a Protocol Buffers
     * {@link MotionTelemetry} message.
     *
     * @param motion the domain motion telemetry message
     * @return the corresponding protobuf {@link MotionTelemetry} message
     */
    private static MotionTelemetry toProtoBuf(WheellyMotionMessage motion) {
        return MotionTelemetry.newBuilder()
                .setTimestamp(motion.time())
                .setX((float) motion.xPulses())
                .setY((float) motion.yPulses())
                .setRobotDeg(motion.directionDeg())
                .setLeftPps((float) motion.leftPps())
                .setRightPps((float) motion.rightPps())
                .setImuFailure(motion.imuFailure())
                .setStatusIdValue(motion.status().ordinal())
                .setTargetDeg(motion.targetDeg())
                .setXTarget((float) motion.xTarget())
                .setYTarget((float) motion.yTarget())
                .setLeftTargetPps(motion.leftTargetPps())
                .setRightTargetPps(motion.rightTargetPps())
                .setLeftPower(motion.leftPower())
                .setRightPower(motion.rightPower())
                .build();
    }

    /**
     * Converts a domain {@link WheellyLidarMessage} into a Protocol Buffers
     * {@link LidarTelemetry} message.
     *
     * @param lidar the domain LiDAR telemetry message
     * @return the corresponding protobuf {@link LidarTelemetry} message
     */
    private static LidarTelemetry toProtoBuf(WheellyLidarMessage lidar) {
        return LidarTelemetry.newBuilder()
                .setTimestamp(lidar.time())
                .setFrontDistance(lidar.frontDistance())
                .setRearDistance(lidar.rearDistance())
                .setX((float) lidar.xPulses())
                .setY((float) lidar.yPulses())
                .setRobotYaw(lidar.robotYawDeg())
                .build();
    }

    /**
     * Converts a domain {@link org.mmarini.wheelly.robots.CorrelatedCameraEvent}
     * into its Protocol Buffers {@link CorrelatedCameraEvent} representation.
     *
     * @param cameraEvent the domain correlated camera event
     * @return the corresponding protobuf {@link CorrelatedCameraEvent} message
     */
    private static CorrelatedCameraEvent toProtoBuf(org.mmarini.wheelly.robots.CorrelatedCameraEvent cameraEvent) {
        return CorrelatedCameraEvent.newBuilder()
                .setMarker(toProtoBuf(cameraEvent.cameraEvent()))
                .setLidars(toProtoBuf(cameraEvent.lidar()))
                .build();
    }

    /**
     * Converts a domain {@link org.mmarini.wheelly.robots.CameraEvent} into its
     * Protocol Buffers {@link MarkerEvent} representation.
     *
     * @param cameraEvent the domain camera event
     * @return the corresponding protobuf {@link MarkerEvent} message
     */
    private static MarkerEvent toProtoBuf(org.mmarini.wheelly.robots.CameraEvent cameraEvent) {
        return MarkerEvent.newBuilder()
                .setTimestamp(cameraEvent.time())
                .setMarkerId(cameraEvent.markerId())
                .setWidth(cameraEvent.width())
                .setHeight(cameraEvent.height())
                .addAllCoords(toProtoBuf(cameraEvent.points()))
                .build();
    }

    /**
     * Flattens an array of 2D points into a list of interleaved X and Y
     * floating-point coordinates.
     *
     * @param points the array of {@link java.awt.geom.Point2D} instances, or {@code null}
     * @return a {@link List} of flattened coordinates, or an empty list if {@code points} is {@code null}
     */
    private static List<Integer> toProtoBuf(java.awt.geom.Point2D[] points) {
        return points != null
                ? Arrays.stream(points)
                .flatMap(p -> Stream.of((int) p.getX(), (int) p.getY()))
                .toList()
                : List.of();
    }

    /**
     * Converts a domain {@link WheellyContactsMessage} into a Protocol Buffers
     * {@link ContactsTelemetry} message.
     *
     * @param contacts the domain contacts telemetry message
     * @return the corresponding protobuf {@link ContactsTelemetry} message
     */
    private static ContactsTelemetry toProtoBuf(WheellyContactsMessage contacts) {
        return ContactsTelemetry.newBuilder()
                .setTimestamp(contacts.time())
                .setFrontSensor(contacts.frontSensors())
                .setRearSensor(contacts.rearSensors())
                .setCanMoveForward(contacts.canMoveForward())
                .setCanMoveBackward(contacts.canMoveBackward())
                .build();
    }

    /**
     * Converts a map of domain {@link org.mmarini.wheelly.perception.FiducialMarker}
     * instances into a list of Protocol Buffers {@link FiducialMarker} messages.
     *
     * @param markers the map of marker IDs to domain fiducial markers
     * @return a {@link List} of protobuf {@link FiducialMarker} messages
     */
    private static List<FiducialMarker> toProtoBuf(Map<Integer, org.mmarini.wheelly.perception.FiducialMarker> markers) {
        return markers.values().stream()
                .map(InferenceFileWriter::toProtoBuf)
                .toList();
    }

    /**
     * Converts a domain {@link org.mmarini.wheelly.perception.FiducialMarker} into
     * its Protocol Buffers {@link FiducialMarker} representation.
     *
     * @param fiducialMarker the domain fiducial marker
     * @return the corresponding protobuf {@link FiducialMarker} message
     */
    private static FiducialMarker toProtoBuf(org.mmarini.wheelly.perception.FiducialMarker fiducialMarker) {
        return FiducialMarker.newBuilder()
                .setId(fiducialMarker.markerId())
                .setLocation(toProtoBuf(fiducialMarker.location()))
                .setWeight((float) fiducialMarker.weight())
                .setMarkerTime(fiducialMarker.markerTime())
                .setCleanTime(fiducialMarker.cleanTime())
                .build();
    }

    /**
     * Converts a domain {@link org.mmarini.wheelly.perception.RadarMap} into its
     * Protocol Buffers {@link RadarMap} representation.
     *
     * @param radarMap the domain radar occupancy map
     * @return the corresponding protobuf {@link RadarMap} message
     */
    private static RadarMap toProtoBuf(org.mmarini.wheelly.perception.RadarMap radarMap) {
        return RadarMap.newBuilder()
                .setCleanTimeStamp(radarMap.cleanTimestamp())
                .addAllCells(toProtoBuf(radarMap.cells()))
                .build();
    }

    /**
     * Converts an array of domain {@link org.mmarini.wheelly.perception.MapCell}
     * instances into a linearised list of Protocol Buffers {@link MapCell} messages.
     *
     * @param cells the array of domain map cells
     * @return a {@link List} of protobuf {@link MapCell} messages
     */
    private static List<MapCell> toProtoBuf(org.mmarini.wheelly.perception.MapCell[] cells) {
        return Arrays.stream(cells)
                .map(InferenceFileWriter::toProtoBuf)
                .toList();
    }

    /**
     * Converts a domain {@link org.mmarini.wheelly.perception.MapCell} into its
     * Protocol Buffers {@link MapCell} representation.
     *
     * @param mapCell the domain map cell
     * @return the corresponding protobuf {@link MapCell} message
     */
    private static MapCell toProtoBuf(org.mmarini.wheelly.perception.MapCell mapCell) {
        return MapCell.newBuilder()
                .setContactTime(mapCell.contactTime())
                .setEchoTime(mapCell.echoTime())
                .setEchoWeight((float) mapCell.echoWeight())
                .build();
    }

    private final Path file;
    private boolean headerWritten;
    private OutputStream stream;

    /**
     * Creates a new writer backed by the specified destination file path.
     *
     * @param file the {@link Path} to the target binary Protocol Buffers file
     * @throws NullPointerException if {@code file} is {@code null}
     */
    public InferenceFileWriter(Path file) {
        this.file = requireNonNull(file);
    }

    @Override
    public void close() throws Exception {
        if (stream != null) {
            stream.close();
            stream = null;
        }
    }

    /**
     * Lazily creates parent directories if needed and opens the underlying output
     * stream if not already initialised.
     *
     * @throws IOException if an I/O error occurs whilst creating directories or
     *                     opening the output file
     */
    private void init() throws IOException {
        if (stream == null) {
            Files.createDirectories(file.toAbsolutePath().getParent());
            stream = Files.newOutputStream(file);
        }
    }

    @Override
    public <T extends InferenceWriter> T write(org.mmarini.wheelly.behaviours.BehaviourState state,
                                               org.mmarini.wheelly.behaviours.BehaviourAction action) throws IOException {
        init();
        writeHeader(state.worldModel().worldSpec(), state.worldModel().radarMap().topology());
        InferenceRecord record = toProtoBuf(state, action);
        record.writeDelimitedTo(stream);
        return (T) this;
    }

    /**
     * Writes the delimited {@link InferenceHeader} to the output stream once,
     * prior to persisting the first inference record.
     *
     * @param worldModelSpec the world model specification to record in the header
     * @param topology       the spatial grid topology to record in the header
     * @throws IOException if an I/O error occurs whilst serialising or flushing
     *                     the header
     */
    private void writeHeader(org.mmarini.wheelly.perception.WorldModelSpec worldModelSpec,
                             org.mmarini.wheelly.perception.GridTopology topology) throws IOException {
        init();
        if (!headerWritten) {
            InferenceHeader protoHeader = toProtoBuf(worldModelSpec, topology);
            protoHeader.writeDelimitedTo(stream);
            stream.flush();
            headerWritten = true;
        }
    }
}
