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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mmarini.RandomArgumentsGenerator;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.behaviours.HeadActionId;
import org.mmarini.wheelly.behaviours.MacroAction;
import org.mmarini.wheelly.behaviours.MacroActionsState;
import org.mmarini.wheelly.behaviours.MoveActionId;
import org.mmarini.wheelly.control.RobotStatus;
import org.mmarini.wheelly.perception.*;
import org.mmarini.wheelly.robots.*;
import org.mmarini.wheelly.utils.Angle;

import java.awt.geom.Point2D;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.robots.RobotSpec.*;

class InferenceFileTest {

    public static final File FILE = new File("tmp/dump.bin");
    public static final double GRID_SIZE = 0.3;
    public static final GridTopology TOPOLOGY = GridTopology.create(new Point2D.Double(), 51, 51, GRID_SIZE);
    public static final RadarMap RADAR = RadarMap.empty(TOPOLOGY);
    public static final WheellyMotionMessage MOTION_MESSAGE = new WheellyMotionMessage(2, 4, 5,
            6, 7, 8, 9, MotionStatusId.HALT, 0, 10, 11, 12, 13, 0, 0);
    public static final WheellyContactsMessage CONTACTS_MESSAGE = new WheellyContactsMessage(2, true,
            true, true, true);
    public static final WheellyLidarMessage LIDAR_MESSAGE = new WheellyLidarMessage(2, 0, 0,
            0, 0, 0, 0, HeadStatusId.FIX_DIRECTION,
            0, 0, 0);
    public static final CameraEvent CAMERA_EVENT = new CameraEvent(0, UNKNOWN_MARKER, 3, 4, null, Angle.DEG0);
    public static final CorrelatedCameraEvent CORRELATED_CAMERA_EVENT = new CorrelatedCameraEvent(CAMERA_EVENT, LIDAR_MESSAGE);
    public static final int GRID_MAP_SIZE = 31;
    public static final RobotStatus ROBOT_STATUS = new RobotStatus(DEFAULT_ROBOT_SPEC, 1, MOTION_MESSAGE,
            CONTACTS_MESSAGE, InferenceFileReader.DEFAULT_SUPPLY_MESSAGE, InferenceFileReader.DEFAULT_DECODE_VOLTAGE, CORRELATED_CAMERA_EVENT, LIDAR_MESSAGE);
    public static final WorldModelSpec WORLD_MODEL_SPEC = new WorldModelSpec(DEFAULT_ROBOT_SPEC,
            GRID_MAP_SIZE);
    public static final MacroAction AGENT_ACTION = new MacroAction(MoveActionId.MICRO_FORWARD_ACTION, HeadActionId.SCAN_ACTION);
    public static final int NUM_RANDOM_TEST_CASES = 30;
    public static final long SEED = 1234L;
    private static final Map<Integer, FiducialMarker> MARKERS = Map.of(
            UNKNOWN_MARKER, new FiducialMarker(UNKNOWN_MARKER, new Point2D.Double(1, 2), 1, 2, 3));
    public static final WorldModel MODEL = new WorldModel(WORLD_MODEL_SPEC, ROBOT_STATUS, RADAR, MARKERS, null, null);
    public static final MacroActionsState MACRO_ENV_STATE = new MacroActionsState(MODEL, MoveActionId.MICRO_FORWARD_ACTION, HeadActionId.SCAN_ACTION);

    public static Stream<Arguments> dataCamera() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(0L, 60000L) // long time,
                .uniform(0, 30) // int markerId,
                .uniform(100, 600) // int width,
                .uniform(100, 600) //  int height
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataHeadStatusScan() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-90, 90)
                .build(NUM_RANDOM_TEST_CASES);
    }

    static Stream<Arguments> dataIntegers() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(Integer.MIN_VALUE, Integer.MAX_VALUE - 1)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataLidar() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(0L, 60000L) // long time,
                .uniform(0, 2000) // int frontDistance,
                .uniform(0, 2000) // int rearDistance,
                .uniform(distance2Pulse(-3), distance2Pulse(3)) // double xPulses,
                .uniform(distance2Pulse(-3), distance2Pulse(3)) // double yPulses,
                .uniform(-180, 179) // int yawDeg,
                .uniform(-90, 90) // int headDirDeg,
                .choiceObj(HeadStatusId.values()) // HeadStatusId trackingState,
                .uniform(-180, 179) // int headTargetDeg,
                .uniform(distance2Pulse(-3), distance2Pulse(3)) // double xTarget,
                .uniform(distance2Pulse(-3), distance2Pulse(3)) // double yTarget)
                .build(NUM_RANDOM_TEST_CASES);
    }

    static Stream<Arguments> dataLongs() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(Long.MIN_VALUE, Long.MAX_VALUE - 1)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataMotionStatusRotate() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-180, 179)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataTarget() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3d, 3d)
                .uniform(-3d, 3d)
                .build(NUM_RANDOM_TEST_CASES);
    }

    private InferenceFileWriter writer;

    @BeforeEach
    void setUp() {
        FILE.delete();
        this.writer = assertDoesNotThrow(() -> {
            InferenceFileWriter file = InferenceFileWriter.fromFile(FILE);
            file.writeHeader(WORLD_MODEL_SPEC, TOPOLOGY);
            return file;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        writer.close();
        FILE.delete();
    }

    @ParameterizedTest
    @MethodSource("dataCamera")
    void testCamera(long time,
                    int markerId,
                    int width,
                    int height) throws IOException {
        CameraEvent camera = CameraEvent.create(time, markerId, width, height, 1);
        writer.write(camera);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            CameraEvent cameraRead = reader.readCamera();
            assertEquals(camera, cameraRead);
            assertThrows(IOException.class, reader::readByte);
        }
    }

    @Test
    void testContacts() throws IOException {
        writer.write(CONTACTS_MESSAGE);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            WheellyContactsMessage contactsRead = reader.readContacts();
            assertEquals(CONTACTS_MESSAGE, contactsRead);
            assertThrows(EOFException.class, reader::readByte);
        }
    }

    @Test
    void testCorrelatedCamera() throws IOException {
        writer.write(CORRELATED_CAMERA_EVENT);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            CorrelatedCameraEvent cameraRead = reader.readCorrelatedCamera();
            assertEquals(CORRELATED_CAMERA_EVENT, cameraRead);
            assertThrows(EOFException.class, reader::readByte);
        }
    }

    @Test
    void testDLMacroActionStep() throws IOException {
        writer.write(MACRO_ENV_STATE, AGENT_ACTION);
        writer.write(MACRO_ENV_STATE, AGENT_ACTION);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            Tuple2<MacroActionsState, MacroAction> step1 = reader.readMacroActionStep();
            Tuple2<MacroActionsState, MacroAction> step2 = reader.readMacroActionStep();
            assertThrows(EOFException.class, reader::readByte);

            MacroActionsState state = step1._1;
            WorldModel model = state.worldModel();
            RadarMap radarRead = model.radarMap();
            assertEquals(MODEL.robotStatus(), model.robotStatus());
            assertEquals(MODEL.markers(), model.markers());
            assertEquals(RADAR.cleanTimestamp(), radarRead.cleanTimestamp());
            assertEquals(RADAR.topology().width(), radarRead.topology().width());
            assertEquals(RADAR.topology().height(), radarRead.topology().height());
            assertEquals(RADAR.topology().gridSize(), radarRead.topology().gridSize());
            assertEquals(RADAR.topology().center(), radarRead.topology().center());
            assertArrayEquals(RADAR.cells(), radarRead.cells());
            assertEquals(MACRO_ENV_STATE.headActionId(), state.headActionId());
            assertEquals(MACRO_ENV_STATE.moveActionId(), state.moveActionId());
            assertEquals(AGENT_ACTION, step1._2);


            state = step2._1;
            model = state.worldModel();
            radarRead = model.radarMap();
            assertEquals(MODEL.robotStatus(), model.robotStatus());
            assertEquals(MODEL.markers(), model.markers());
            assertEquals(RADAR.cleanTimestamp(), radarRead.cleanTimestamp());
            assertEquals(RADAR.topology().width(), radarRead.topology().width());
            assertEquals(RADAR.topology().height(), radarRead.topology().height());
            assertEquals(RADAR.topology().gridSize(), radarRead.topology().gridSize());
            assertEquals(RADAR.topology().center(), radarRead.topology().center());
            assertArrayEquals(RADAR.cells(), radarRead.cells());
            assertEquals(MACRO_ENV_STATE.headActionId(), state.headActionId());
            assertEquals(MACRO_ENV_STATE.moveActionId(), state.moveActionId());
            assertEquals(AGENT_ACTION, step2._2);
        }
    }

    @Test
    void testHeadActionId() throws IOException {
        for (HeadActionId value : HeadActionId.values()) {
            writer.write(value);
        }
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            for (HeadActionId value : HeadActionId.values()) {
                assertEquals(value, reader.readHeadActionId());
            }
            assertThrows(EOFException.class, reader::readByte);
        }
    }

    @ParameterizedTest
    @MethodSource("dataHeadStatusScan")
    void testHeadStatusScan(int deg) throws IOException {
        HeadStatus status = HeadStatus.scan(Angle.fromDeg(deg));
        writer.write(status);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            HeadStatus readStatus = reader.readHeadStatus();
            assertEquals(status, readStatus);
            assertThrows(EOFException.class, reader::readByte);
        }
    }

    @ParameterizedTest
    @MethodSource("dataTarget")
    void testHeadStatusTrack(double x, double y) throws IOException {
        Point2D target = new Point2D.Double(x, y);
        HeadStatus trackFront = HeadStatus.trackFrontFace(target);
        HeadStatus trackRear = HeadStatus.trackRearFace(target);
        writer.write(trackFront);
        writer.write(trackRear);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            HeadStatus status = reader.readHeadStatus();
            assertEquals(trackFront, status);
            status = reader.readHeadStatus();
            assertEquals(trackRear, status);
            assertThrows(EOFException.class, reader::readByte);
        }
    }

    @Test
    void testHeader() throws IOException {
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            assertEquals(InferenceWriter.Type.RL_MACRO_ACTION, reader.type());
            assertEquals(WORLD_MODEL_SPEC, reader.worldSpec());
            assertEquals(TOPOLOGY.center(), reader.topology().center());
            assertEquals(TOPOLOGY.width(), reader.topology().width());
            assertEquals(TOPOLOGY.height(), reader.topology().height());
            assertEquals(TOPOLOGY.gridSize(), reader.topology().gridSize());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 0xff, 0xffff, 0xffffff, -1, -2, -0xff, -0xffff, -0xffffff, Integer.MIN_VALUE, Integer.MAX_VALUE})
    @MethodSource("dataIntegers")
    void testIntegers(int value) throws IOException {
        writer.write(value);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            int read = reader.readInt();
            assertEquals(value, read);
            assertThrows(EOFException.class, reader::readByte);
        }
    }

    @ParameterizedTest
    @MethodSource("dataLidar")
    void testLidar(long time,
                   int frontDistance,
                   int rearDistance,
                   double xPulses,
                   double yPulses,
                   int yawDeg,
                   int headDirDeg,
                   HeadStatusId trackingState,
                   int headTargetDeg,
                   double xTarget,
                   double yTarget) throws IOException {
        WheellyLidarMessage lidar = new WheellyLidarMessage(time, frontDistance, rearDistance,
                xPulses, yPulses, yawDeg, headDirDeg, trackingState, headTargetDeg, xTarget, yTarget);
        writer.write(lidar);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            WheellyLidarMessage readLidar = reader.readLidarMessage();
            assertEquals(lidar, readLidar);
            assertThrows(EOFException.class, reader::readByte);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 2, 0xff, 0xffff, 0xffffff, -1, -2, -0xff, -0xffff, -0xffffff, Long.MAX_VALUE, Long.MIN_VALUE})
    @MethodSource("dataLongs")
    void testLongs(long value) throws IOException {
        writer.write(value);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            long read = reader.readLong();
            assertEquals(value, read);
            assertThrows(EOFException.class, reader::readByte);
        }
    }

    @Test
    void testMacroEnvState() throws IOException {
        writer.write(MACRO_ENV_STATE);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            MacroActionsState state = reader.readMacroActionState();
            WorldModel model = state.worldModel();
            RadarMap radarRead = model.radarMap();
            assertEquals(MODEL.robotStatus(), model.robotStatus());
            assertEquals(MODEL.markers(), model.markers());
            assertEquals(RADAR.cleanTimestamp(), radarRead.cleanTimestamp());
            assertEquals(RADAR.topology().width(), radarRead.topology().width());
            assertEquals(RADAR.topology().height(), radarRead.topology().height());
            assertEquals(RADAR.topology().gridSize(), radarRead.topology().gridSize());
            assertEquals(RADAR.topology().center(), radarRead.topology().center());
            assertArrayEquals(RADAR.cells(), radarRead.cells());
            assertEquals(MACRO_ENV_STATE.headActionId(), state.headActionId());
            assertEquals(MACRO_ENV_STATE.moveActionId(), state.moveActionId());
            assertThrows(EOFException.class, reader::readByte);
        }
    }

    @Test
    void testMarkers() throws IOException {
        writer.write(MARKERS);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            Map<Integer, FiducialMarker> markersRead = reader.readMarkers();
            assertEquals(MARKERS, markersRead);
            assertThrows(IOException.class, reader::readByte);
        }
    }

    @Test
    void testModel() throws IOException {
        writer.write(MODEL);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            WorldModel model = reader.readModel();
            RadarMap radarRead = model.radarMap();
            assertEquals(MODEL.robotStatus(), model.robotStatus());
            assertEquals(MODEL.markers(), model.markers());
            assertEquals(RADAR.cleanTimestamp(), radarRead.cleanTimestamp());
            assertEquals(RADAR.topology().width(), radarRead.topology().width());
            assertEquals(RADAR.topology().height(), radarRead.topology().height());
            assertEquals(RADAR.topology().gridSize(), radarRead.topology().gridSize());
            assertEquals(RADAR.topology().center(), radarRead.topology().center());
            assertArrayEquals(RADAR.cells(), radarRead.cells());
            assertThrows(IOException.class, reader::readByte);
        }
    }

    @Test
    void testMotion() throws IOException {
        writer.write(MOTION_MESSAGE);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            WheellyMotionMessage motionRead = reader.readMotion();
            assertEquals(MOTION_MESSAGE, motionRead);
            assertThrows(EOFException.class, reader::readByte);
        }
    }

    @ParameterizedTest
    @MethodSource("dataTarget")
    void testMotionStatus(double x, double y) throws IOException {
        Point2D data = new Point2D.Double(x, y);
        MotionStatus forward = MotionStatus.forward(data);
        writer.write(forward);
        MotionStatus backward = MotionStatus.backward(data);
        writer.write(backward);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            MotionStatus status = reader.readMotionStatus();
            assertEquals(forward, status);
            status = reader.readMotionStatus();
            assertEquals(backward, status);
            assertThrows(EOFException.class, reader::readByte);
        }
    }

    @Test
    void testMotionStatusHalt() throws IOException {
        writer.write(MotionStatus.halt());
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            MotionStatus status = reader.readMotionStatus();
            assertEquals(MotionStatus.halt(), status);
            assertThrows(EOFException.class, reader::readByte);
        }
    }

    @ParameterizedTest
    @MethodSource("dataMotionStatusRotate")
    void testMotionStatusRotate(int deg) throws IOException {
        MotionStatus status = MotionStatus.rotate(Angle.fromDeg(deg));
        writer.write(status);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            MotionStatus readStatus = reader.readMotionStatus();
            assertEquals(status, readStatus);
            assertThrows(EOFException.class, reader::readByte);
        }
    }

    @Test
    void testMoveActionId() throws IOException {
        for (MoveActionId value : MoveActionId.values()) {
            writer.write(value);
        }
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            for (MoveActionId value : MoveActionId.values()) {
                assertEquals(value, reader.readMoveActionId());
            }
            assertThrows(EOFException.class, reader::readByte);
        }
    }

    @Test
    void testRadarMap() throws IOException {
        writer.write(RADAR);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            RadarMap radarRead = reader.readRadar();
            assertEquals(RADAR.cleanTimestamp(), radarRead.cleanTimestamp());
            assertEquals(RADAR.topology().width(), radarRead.topology().width());
            assertEquals(RADAR.topology().height(), radarRead.topology().height());
            assertEquals(RADAR.topology().gridSize(), radarRead.topology().gridSize());
            assertEquals(RADAR.topology().center(), radarRead.topology().center());
            assertArrayEquals(RADAR.cells(), radarRead.cells());
            assertThrows(IOException.class, reader::readByte);
        }
    }

    @Test
    void testStatus() throws IOException {
        writer.write(ROBOT_STATUS);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            RobotStatus contactsRead = reader.readStatus();
            assertEquals(ROBOT_STATUS, contactsRead);
            assertThrows(IOException.class, reader::readByte);
        }
    }
}