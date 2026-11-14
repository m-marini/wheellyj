/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
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

import org.mmarini.Tuple2;
import org.mmarini.wheelly.behaviours.HeadActionId;
import org.mmarini.wheelly.behaviours.MacroActionsState;
import org.mmarini.wheelly.behaviours.MoveActionId;
import org.mmarini.wheelly.protobuf.aruco.MarkerEvent;
import org.mmarini.wheelly.protobuf.inferences.*;
import org.mmarini.wheelly.protobuf.inferences.CorrelatedCameraEvent;
import org.mmarini.wheelly.protobuf.inferences.RobotSpec;
import org.mmarini.wheelly.protobuf.robot.ContactsTelemetry;
import org.mmarini.wheelly.protobuf.robot.LidarTelemetry;
import org.mmarini.wheelly.protobuf.robot.MotionTelemetry;
import org.mmarini.wheelly.robots.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntToDoubleFunction;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Reads and deserialises AI inference and training records from a binary
 * Protocol Buffers file.
 * <p>
 * The file is expected to begin with a delimited {@link InferenceHeader}
 * defining the world model specifications and grid topology, followed by a
 * sequence of delimited {@link InferenceRecord} entries representing
 * state-action pairs.
 * </p>
 */
public class InferenceFileReader implements InferenceReader {

    /**
     * Default power supply telemetry message used when supply readings are not
     * persisted in the inference stream.
     */
    public static final WheellySupplyMessage DEFAULT_SUPPLY_MESSAGE = new WheellySupplyMessage(0, 0);

    /**
     * Default voltage decoding function returning a nominal 12.0 V supply.
     */
    public static final IntToDoubleFunction DEFAULT_DECODE_VOLTAGE = x -> 12d;

    /**
     * Converts a Protocol Buffers {@link GridTopology} message into the domain
     * {@link org.mmarini.wheelly.perception.GridTopology} representation.
     *
     * @param topology the protobuf grid topology message
     * @return the corresponding domain grid topology instance
     */
    private static org.mmarini.wheelly.perception.GridTopology fromProto(GridTopology topology) {
        java.awt.geom.Point2D center = fromProto(topology.getCenter());
        return org.mmarini.wheelly.perception.GridTopology.create(center, topology.getWidth(), topology.getHeight(), topology.getGridSize());
    }

    /**
     * Converts a Protocol Buffers {@link Point2D} message into a Java AWT
     * {@link java.awt.geom.Point2D}.
     *
     * @param point the protobuf 2D point message
     * @return the corresponding {@link java.awt.geom.Point2D.Float} instance
     */
    private static java.awt.geom.Point2D fromProto(Point2D point) {
        return new java.awt.geom.Point2D.Float(point.getX(), point.getY());
    }

    /**
     * Converts a Protocol Buffers {@link RobotSpec} message into the domain
     * {@link org.mmarini.wheelly.robots.RobotSpec} configuration.
     *
     * @param robotSpec the protobuf robot specification message
     * @return the corresponding domain robot specification instance
     */
    private static org.mmarini.wheelly.robots.RobotSpec fromProto(RobotSpec robotSpec) {
        return org.mmarini.wheelly.robots.RobotSpec.create(
                robotSpec.getMaxRadarDistance(),
                robotSpec.getLidarFOV(),
                robotSpec.getContactRadius(),
                robotSpec.getCameraFOV(),
                robotSpec.getHeadLocation().getX(),
                robotSpec.getHeadLocation().getY(),
                robotSpec.getFrontLidarDistance(),
                robotSpec.getRearLidarDistance(),
                robotSpec.getCameraDistance(),
                robotSpec.getHeadFOV(),
                robotSpec.getTargetRange(),
                robotSpec.getDirectionRange(),
                robotSpec.getMaxRotRange(),
                robotSpec.getMaxRotPps(),
                robotSpec.getMaxSpeed(),
                robotSpec.getDecelerateDistance(),
                robotSpec.getSendInterval(),
                robotSpec.getScanInterval()
        );
    }

    /**
     * Converts a Protocol Buffers {@link WorldModelSpec} message into the domain
     * {@link org.mmarini.wheelly.perception.WorldModelSpec} configuration.
     *
     * @param worldSpec the protobuf world model specification message
     * @return the corresponding domain world model specification instance
     */
    private static org.mmarini.wheelly.perception.WorldModelSpec fromProto(WorldModelSpec worldSpec) {
        org.mmarini.wheelly.robots.RobotSpec robotSpec = fromProto(worldSpec.getRobotSpec());
        return new org.mmarini.wheelly.perception.WorldModelSpec(robotSpec, worldSpec.getRobotMapSize());
    }

    /**
     * Converts a Protocol Buffers {@link FiducialMarker} message into the domain
     * {@link org.mmarini.wheelly.perception.FiducialMarker} object.
     *
     * @param marker the protobuf fiducial marker message
     * @return the corresponding domain fiducial marker instance
     */
    public static org.mmarini.wheelly.perception.FiducialMarker fromProto(FiducialMarker marker) {
        return new org.mmarini.wheelly.perception.FiducialMarker(
                marker.getId(),
                fromProto(marker.getLocation()),
                marker.getWeight(),
                marker.getMarkerTime(), marker.getCleanTime());
    }

    /**
     * Converts a list of {@link Float} coordinates into a primitive {@code double} array.
     *
     * @param coordsList the list of floating-point coordinates
     * @return a primitive {@code double[]} containing the converted coordinates
     */
    private static double[] fromProto(List<Integer> coordsList) {
        return coordsList.stream()
                .mapToDouble(x -> x)
                .toArray();
    }

    /**
     * Converts a list of Protocol Buffers {@link FiducialMarker} messages into a map
     * indexed by marker identifier.
     *
     * @param markersList the list of protobuf fiducial marker messages
     * @return a map associating each marker ID with its domain {@link org.mmarini.wheelly.perception.FiducialMarker}
     */
    private static Map<Integer, org.mmarini.wheelly.perception.FiducialMarker> fromProtoBuf(List<FiducialMarker> markersList) {
        return markersList.stream()
                .map(InferenceFileReader::fromProto)
                .collect(Collectors.toMap(
                        org.mmarini.wheelly.perception.FiducialMarker::markerId,
                        Function.identity()
                ));
    }

    /**
     * Converts a Protocol Buffers {@link CorrelatedCameraEvent} message into the domain
     * {@link org.mmarini.wheelly.robots.CorrelatedCameraEvent} representation.
     *
     * @param camera the protobuf correlated camera event message
     * @return the corresponding domain correlated camera event instance
     */
    private static org.mmarini.wheelly.robots.CorrelatedCameraEvent fromProtoBuf(CorrelatedCameraEvent camera) {
        return new org.mmarini.wheelly.robots.CorrelatedCameraEvent(
                fromProtoBuf(camera.getMarker()),
                fromProtoBuf(camera.getLidars()));

    }

    /**
     * Converts a Protocol Buffers {@link CameraEvent} message into the domain
     * {@link org.mmarini.wheelly.robots.CameraEvent} representation.
     *
     * @param camera the protobuf camera event message
     * @return the corresponding domain camera event instance
     */
    public static org.mmarini.wheelly.robots.CameraEvent fromProtoBuf(MarkerEvent camera) {
        return org.mmarini.wheelly.robots.CameraEvent.create(
                camera.getTimestamp(),
                camera.getMarkerId(),
                camera.getWidth(),
                camera.getHeight(), 1,
                fromProto(camera.getCoordsList()));
    }

    /**
     * Converts a Protocol Buffers {@link LidarTelemetry} message into a domain
     * {@link WheellyLidarMessage}.
     *
     * @param lidars the protobuf LiDAR telemetry message
     * @return the corresponding {@link WheellyLidarMessage} instance
     */
    public static WheellyLidarMessage fromProtoBuf(LidarTelemetry lidars) {
        return new WheellyLidarMessage(lidars.getTimestamp(),
                lidars.getFrontDistance(),
                lidars.getRearDistance(),
                lidars.getX(), lidars.getY(),
                lidars.getRobotYaw(),
                lidars.getHeadDirection(),
                HeadStatusId.values()[lidars.getHeadStatusValue()],
                lidars.getHeadTarget(),
                lidars.getXTarget(), lidars.getYTarget());
    }

    /**
     * Converts a Protocol Buffers {@link ContactsTelemetry} message into a domain
     * {@link WheellyContactsMessage}.
     *
     * @param contacts the protobuf contacts telemetry message
     * @return the corresponding {@link WheellyContactsMessage} instance
     */
    public static WheellyContactsMessage fromProtoBuf(ContactsTelemetry contacts) {
        return new WheellyContactsMessage(contacts.getTimestamp(),
                contacts.getFrontSensor(), contacts.getRearSensor(),
                contacts.getCanMoveForward(), contacts.getCanMoveBackward());
    }

    /**
     * Converts a Protocol Buffers {@link MotionTelemetry} message into a domain
     * {@link WheellyMotionMessage}.
     *
     * @param motion the protobuf motion telemetry message
     * @return the corresponding {@link WheellyMotionMessage} instance
     */
    public static WheellyMotionMessage fromProtoBuf(MotionTelemetry motion) {
        return new WheellyMotionMessage(motion.getTimestamp(),
                motion.getX(), motion.getY(),
                motion.getRobotDeg(),
                motion.getLeftPps(), motion.getRightPps(),
                motion.getImuFailure(),
                MotionStatusId.values()[motion.getStatusIdValue()],
                motion.getTargetDeg(),
                motion.getLeftTargetPps(), motion.getRightTargetPps(),
                motion.getLeftPower(), motion.getRightPower(),
                motion.getXTarget(), motion.getYTarget());
    }

    private final Path file;
    private InputStream stream;
    private org.mmarini.wheelly.perception.GridTopology topology;
    private org.mmarini.wheelly.perception.WorldModelSpec worldSpec;

    /**
     * Creates a new reader backed by the specified inference file path.
     *
     * @param file the {@link Path} to the binary Protocol Buffers inference file
     * @throws NullPointerException if {@code file} is {@code null}
     */
    public InferenceFileReader(Path file) {
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
     * Converts a Protocol Buffers {@link WorldModel} message into the domain
     * {@link org.mmarini.wheelly.perception.WorldModel} instance using the
     * {@link #worldSpec} loaded from the file header.
     *
     * @param worldModel the protobuf world model message
     * @return the corresponding domain world model instance
     */
    private org.mmarini.wheelly.perception.WorldModel fromProto(WorldModel worldModel) {
        return new org.mmarini.wheelly.perception.WorldModel(worldSpec,
                fromProtoBuf(worldModel.getRobotStatus()),
                fromProtoBuf(worldModel.getRadarMap()),
                fromProtoBuf(worldModel.getMarkersList()),
                null, null);
    }

    /**
     * Converts a Protocol Buffers {@link RadarMap} message into a domain
     * {@link org.mmarini.wheelly.perception.RadarMap} initialised with the
     * {@link #topology} loaded from the file header.
     *
     * @param radarMap the protobuf radar map message
     * @return the populated domain radar map instance
     * @throws IllegalArgumentException if the number of cells in the protobuf message
     *                                  does not match the expected grid topology size
     */
    private org.mmarini.wheelly.perception.RadarMap fromProtoBuf(RadarMap radarMap) {
        long cleanTimestamp = radarMap.getCleanTimeStamp();
        org.mmarini.wheelly.perception.RadarMap radarMap1 = org.mmarini.wheelly.perception.RadarMap.empty(topology)
                .setCleanTimestamp(cleanTimestamp);
        org.mmarini.wheelly.perception.MapCell[] cells = radarMap1.cells();
        List<MapCell> protoCells = radarMap.getCellsList();
        if (protoCells.size() != cells.length) {
            throw new IllegalArgumentException(format("Expected %d number of cells (%d)",
                    cells.length, protoCells.size()));
        }
        for (int i = 0; i < cells.length; i++) {
            cells[i] = fromProtoBuf(protoCells.get(i), cells[i].location());
        }
        return radarMap1;
    }

    /**
     * Converts a Protocol Buffers {@link MapCell} message into a domain
     * {@link org.mmarini.wheelly.perception.MapCell} at the specified spatial location.
     *
     * @param mapCell  the protobuf map cell message
     * @param location the 2D spatial centre coordinates of the cell
     * @return the corresponding domain map cell instance
     */
    private org.mmarini.wheelly.perception.MapCell fromProtoBuf(MapCell mapCell, java.awt.geom.Point2D location) {
        return new org.mmarini.wheelly.perception.MapCell(location,
                mapCell.getEchoTime(), mapCell.getEchoWeight(), mapCell.getContactTime());
    }

    /**
     * Converts a Protocol Buffers {@link RobotStatus} message into a domain
     * {@link org.mmarini.wheelly.control.RobotStatus} snapshot, supplying default
     * power supply telemetry values.
     *
     * @param robotStatus the protobuf robot status message
     * @return the corresponding domain robot status instance
     */
    private org.mmarini.wheelly.control.RobotStatus fromProtoBuf(RobotStatus robotStatus) {
        return new org.mmarini.wheelly.control.RobotStatus(worldSpec.robotSpec(),
                robotStatus.getRobotTime(),
                fromProtoBuf(robotStatus.getMotion()),
                fromProtoBuf(robotStatus.getContacts()),
                DEFAULT_SUPPLY_MESSAGE, DEFAULT_DECODE_VOLTAGE,
                fromProtoBuf(robotStatus.getCamera()),
                fromProtoBuf(robotStatus.getLidars()));
    }

    /**
     * Converts a Protocol Buffers {@link InferenceRecord} into a state-action pair.
     *
     * @param inferenceRecord the protobuf inference record message, or {@code null}
     *                        if end-of-stream has been reached
     * @return a {@link Tuple2} containing the deserialised {@link org.mmarini.wheelly.behaviours.BehaviourState}
     * and {@link org.mmarini.wheelly.behaviours.BehaviourAction}, or {@code null}
     * if {@code inferenceRecord} is {@code null}
     */
    private Tuple2<org.mmarini.wheelly.behaviours.BehaviourState, org.mmarini.wheelly.behaviours.BehaviourAction> fromProtoBuf(InferenceRecord inferenceRecord) {
        return inferenceRecord != null
                ? Tuple2.of(
                fromProtoBuf(inferenceRecord.getState()),
                fromProtoBuf(inferenceRecord.getAction()))
                : null;
    }

    /**
     * Converts a Protocol Buffers {@link BehaviourAction} message into a domain
     * {@link org.mmarini.wheelly.behaviours.MacroAction}.
     *
     * @param action the protobuf behaviour action message
     * @return the corresponding domain behaviour action instance
     */
    private org.mmarini.wheelly.behaviours.BehaviourAction fromProtoBuf(BehaviourAction action) {
        return new org.mmarini.wheelly.behaviours.MacroAction(
                MoveActionId.values()[action.getMoveActionId()],
                HeadActionId.values()[action.getHeadActionId()]);
    }

    /**
     * Converts a Protocol Buffers {@link BehaviourState} message into a domain
     * {@link MacroActionsState}.
     *
     * @param state the protobuf behaviour state message
     * @return the corresponding domain behaviour state instance
     */
    private org.mmarini.wheelly.behaviours.BehaviourState fromProtoBuf(BehaviourState state) {
        return new MacroActionsState(fromProto(state.getWorldModel()),
                MoveActionId.values()[state.getMoveActionId()],
                HeadActionId.values()[state.getHeadActionId()]);
    }

    /**
     * Lazily opens the underlying input stream and parses the file header if not
     * already initialised.
     *
     * @throws IOException if an I/O error occurs whilst opening the file or
     *                     reading the header
     */
    void init() throws IOException {
        if (stream == null) {
            stream = Files.newInputStream(file);
            readHeader();
        }
    }

    /**
     * Reads and validates the delimited {@link InferenceHeader} from the beginning
     * of the input stream, initialising {@link #topology} and {@link #worldSpec}.
     *
     * @throws IOException if the header type is unrecognised or an I/O error occurs
     */
    private void readHeader() throws IOException {
        InferenceHeader header = InferenceHeader.parseDelimitedFrom(stream);
        if (header.getType() != 0) {
            throw new IOException(format("Type %d not recognized", header.getType()));
        }
        this.topology = fromProto(header.getTopology());
        this.worldSpec = fromProto(header.getWorldSpec());
    }

    @Override
    public Tuple2<org.mmarini.wheelly.behaviours.BehaviourState, org.mmarini.wheelly.behaviours.BehaviourAction> readMacroActionStep() throws IOException {
        init();
        return fromProtoBuf(InferenceRecord.parseDelimitedFrom(stream));
    }

    @Override
    public void reset() throws Exception {
        close();
    }

    /**
     * Returns the spatial grid topology parsed from the inference file header.
     *
     * @return the {@link org.mmarini.wheelly.perception.GridTopology}, or {@code null}
     * if the reader has not yet been initialised
     */
    org.mmarini.wheelly.perception.GridTopology topology() {
        return topology;
    }

    /**
     * Returns the world model specification parsed from the inference file header.
     *
     * @return the {@link org.mmarini.wheelly.perception.WorldModelSpec}, or {@code null}
     * if the reader has not yet been initialised
     */
    org.mmarini.wheelly.perception.WorldModelSpec worldSpec() {
        return worldSpec;
    }
}
