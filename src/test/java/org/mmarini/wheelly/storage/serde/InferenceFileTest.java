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
import org.mmarini.Tuple2;
import org.mmarini.wheelly.behaviours.AgentAction;
import org.mmarini.wheelly.behaviours.HeadActionId;
import org.mmarini.wheelly.behaviours.MacroEnvState;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.robots.RobotSpec.DEFAULT_ROBOT_SPEC;

class InferenceFileTest {

    public static final File FILE = new File("tmp/dump.bin");
    public static final double GRID_SIZE = 0.3;
    public static final GridTopology TOPOLOGY = GridTopology.create(new Point2D.Double(), 51, 51, GRID_SIZE);
    public static final RadarMap RADAR = RadarMap.empty(TOPOLOGY);
    public static final int NUM_SECTORS = 24;
    public static final WheellyMotionMessage MOTION_MESSAGE = new WheellyMotionMessage(2, 4, 5,
            6, 7, 8, 9, MotionStatusId.HALT, 0, 10, 11, 12, 13, 0, 0);
    public static final WheellyContactsMessage CONTACTS_MESSAGE = new WheellyContactsMessage(2, true,
            true, true, true);
    public static final WheellyLidarMessage LIDAR_MESSAGE = new WheellyLidarMessage(2, 0, 0,
            0, 0, 0, 0, HeadStatusId.FIX_DIRECTION,
            0, 0, 0);
    public static final CameraEvent CAMERA_EVENT = new CameraEvent(0, "?", 3, 4, null, Angle.DEG0);
    public static final CorrelatedCameraEvent CORRELATED_CAMERA_EVENT = new CorrelatedCameraEvent(CAMERA_EVENT, LIDAR_MESSAGE);
    public static final RobotCommand COMMANDS = RobotCommand.halt();
    public static final int GRID_MAP_SIZE = 31;
    public static final RobotStatus ROBOT_STATUS = new RobotStatus(DEFAULT_ROBOT_SPEC, 1, MOTION_MESSAGE,
            CONTACTS_MESSAGE, InferenceFileReader.DEFAULT_SUPPLY_MESSAGE, InferenceFileReader.DEFAULT_DECODE_VOLTAGE, CORRELATED_CAMERA_EVENT, LIDAR_MESSAGE);
    public static final WorldModelSpec WORLD_MODEL_SPEC = new WorldModelSpec(DEFAULT_ROBOT_SPEC,
            NUM_SECTORS, GRID_MAP_SIZE);
    public static final MotionStatus FORWARD_MOTION = MotionStatus.forward(new Point2D.Double(1, 2));
    public static final MotionStatus BACKWARD_MOTION = MotionStatus.backward(new Point2D.Double(-1, -2));
    public static final MotionStatus ROTATE_MOTION = MotionStatus.rotate(Angle.DEG180);
    public static final HeadStatus SCAN_STATUS = HeadStatus.scan(Angle.DEG90);
    public static final HeadStatus TRACK_FRONT_STATUS = HeadStatus.trackFrontFace(new Point2D.Double(2, 3));
    public static final HeadStatus TRACK_REAR_STATUS = HeadStatus.trackRearFace(new Point2D.Double(-2, -3));
    public static final AgentAction AGENT_ACTION = new AgentAction(MoveActionId.MICRO_FORWARD_ACTION, HeadActionId.SCAN_ACTION);
    private static final Map<String, FiducialMarker> MARKERS = Map.of(
            "?", new FiducialMarker("?", new Point2D.Double(1, 2), 1, 2, 3));
    public static final WorldModel MODEL = new WorldModel(WORLD_MODEL_SPEC, ROBOT_STATUS, RADAR, MARKERS, null, null, null);
    public static final MacroEnvState MACRO_ENV_STATE = new MacroEnvState(MODEL, MoveActionId.MICRO_FORWARD_ACTION, HeadActionId.SCAN_ACTION);
    private InferenceFileWriter writer;

    @BeforeEach
    void setUp() {
        FILE.delete();
        this.writer = assertDoesNotThrow(() -> {
            InferenceFileWriter file = InferenceFileWriter.fromFile(FILE);
            file.writeHeader(InferenceWriter.Type.DL_MACRO_ACTION, WORLD_MODEL_SPEC, TOPOLOGY);
            return file;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        writer.close();
        FILE.delete();
    }

    @Test
    void testCamera() throws IOException {
        writer.write(CAMERA_EVENT);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            CameraEvent cameraRead = reader.readCamera();
            assertEquals(CAMERA_EVENT, cameraRead);
            assertThrows(IOException.class, reader::readByte);
        }
    }

    @Test
    void testCommands() throws IOException {
        writer.write(COMMANDS);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            RobotCommand commandRead = reader.readCommand();
            assertEquals(COMMANDS, commandRead);
            assertThrows(EOFException.class, reader::readByte);
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
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            Tuple2<MacroEnvState, AgentAction> step = reader.readMacroEnvStep();
            MacroEnvState state = step._1;
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
            assertEquals(AGENT_ACTION, step._2);
            assertThrows(EOFException.class, reader::readByte);
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

    @Test
    void testHeadStatus() throws IOException {
        writer.write(SCAN_STATUS);
        writer.write(TRACK_FRONT_STATUS);
        writer.write(TRACK_REAR_STATUS);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            HeadStatus status = reader.readHeadStatus();
            assertEquals(SCAN_STATUS, status);
            status = reader.readHeadStatus();
            assertEquals(TRACK_FRONT_STATUS, status);
            status = reader.readHeadStatus();
            assertEquals(TRACK_REAR_STATUS, status);
            assertThrows(EOFException.class, reader::readByte);
        }
    }

    @Test
    void testHeader() throws IOException {
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            assertEquals(InferenceWriter.Type.DL_MACRO_ACTION, reader.type());
            assertEquals(WORLD_MODEL_SPEC, reader.worldSpec());
            assertEquals(TOPOLOGY.center(), reader.topology().center());
            assertEquals(TOPOLOGY.width(), reader.topology().width());
            assertEquals(TOPOLOGY.height(), reader.topology().height());
            assertEquals(TOPOLOGY.gridSize(), reader.topology().gridSize());
        }
    }

    @Test
    void testLidar() throws IOException {
        writer.write(LIDAR_MESSAGE);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            WheellyLidarMessage motionRead = reader.readLidarMessage();
            assertEquals(LIDAR_MESSAGE, motionRead);
            assertThrows(EOFException.class, reader::readByte);
        }
    }

    @Test
    void testMacroEnvState() throws IOException {
        writer.write(MACRO_ENV_STATE);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            MacroEnvState state = reader.readMacroEnvState();
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
            Map<String, FiducialMarker> markersRead = reader.readMarkers();
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

    @Test
    void testMotionStatus() throws IOException {
        writer.write(FORWARD_MOTION);
        writer.write(BACKWARD_MOTION);
        writer.write(ROTATE_MOTION);
        writer.write(MotionStatus.halt());
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            MotionStatus status = reader.readMotionStatus();
            assertEquals(FORWARD_MOTION, status);
            status = reader.readMotionStatus();
            assertEquals(BACKWARD_MOTION, status);
            status = reader.readMotionStatus();
            assertEquals(ROTATE_MOTION, status);
            status = reader.readMotionStatus();
            assertEquals(MotionStatus.halt(), status);
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