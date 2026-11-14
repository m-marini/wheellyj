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
import org.mmarini.wheelly.behaviours.*;
import org.mmarini.wheelly.control.RobotStatus;
import org.mmarini.wheelly.perception.*;
import org.mmarini.wheelly.robots.*;
import org.mmarini.wheelly.utils.Angle;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.angleCloseTo;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.robots.RobotSpec.*;
import static org.mmarini.wheelly.storage.serde.InferenceFileReader.DEFAULT_DECODE_VOLTAGE;
import static org.mmarini.wheelly.storage.serde.InferenceFileReader.DEFAULT_SUPPLY_MESSAGE;
import static org.mmarini.wheelly.utils.Utils.MM;

class InferenceFileTest {

    public static final Path FILE = Path.of("tmp/dump.bin");
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
            CONTACTS_MESSAGE, DEFAULT_SUPPLY_MESSAGE, DEFAULT_DECODE_VOLTAGE, CORRELATED_CAMERA_EVENT, LIDAR_MESSAGE);
    public static final WorldModelSpec WORLD_MODEL_SPEC = new WorldModelSpec(DEFAULT_ROBOT_SPEC,
            GRID_MAP_SIZE);
    public static final MacroAction AGENT_ACTION = new MacroAction(MoveActionId.MICRO_FORWARD_ACTION, HeadActionId.SCAN_ACTION);
    public static final int NUM_RANDOM_TEST_CASES = 30;
    public static final long SEED = 1234L;
    private static final Map<Integer, FiducialMarker> MARKERS = Map.of(
            UNKNOWN_MARKER, new FiducialMarker(UNKNOWN_MARKER, new Point2D.Double(1, 2), 1, 2, 3));
    public static final WorldModel MODEL = new WorldModel(WORLD_MODEL_SPEC, ROBOT_STATUS, RADAR, MARKERS, null, null);
    public static final MacroActionsState MACRO_ENV_STATE = new MacroActionsState(MODEL, MoveActionId.MICRO_FORWARD_ACTION, HeadActionId.SCAN_ACTION);

    private InferenceFileWriter writer;

    @BeforeEach
    void setUp() throws IOException {
        Files.deleteIfExists(FILE);
        this.writer = assertDoesNotThrow(() ->
                InferenceFileWriter.fromFile(FILE));
    }

    @AfterEach
    void tearDown() throws Exception {
        writer.close();
        Files.deleteIfExists(FILE);
    }

    @Test
    void testRead() throws Exception {
        writer.write(MACRO_ENV_STATE, AGENT_ACTION);
        writer.close();

        try (InferenceFileReader reader = new InferenceFileReader(FILE)) {
            Tuple2<BehaviourState, BehaviourAction> step = reader.readMacroActionStep();
            assertNotNull(step);
            BehaviourState state = step.v1();
            BehaviourAction action = step.v2();
            assertEquals(MoveActionId.MICRO_FORWARD_ACTION, action.moveActionId());
            assertEquals(HeadActionId.SCAN_ACTION, action.headActionId());
            assertEquals(MoveActionId.MICRO_FORWARD_ACTION, state.moveActionId());
            assertEquals(HeadActionId.SCAN_ACTION, state.headActionId());

            WorldModel worldModel = state.worldModel();

            RobotStatus robotStatus = worldModel.robotStatus();
            assertEquals(ROBOT_STATUS.robotTime(), robotStatus.robotTime());
            assertEquals(ROBOT_STATUS.contactsMessage(), robotStatus.contactsMessage());
            assertEquals(ROBOT_STATUS.lidarMessage(), robotStatus.lidarMessage());
            assertEquals(ROBOT_STATUS.cameraEvent(), robotStatus.cameraEvent());
            assertEquals(ROBOT_STATUS.motionMessage(), robotStatus.motionMessage());

            Map<Integer, FiducialMarker> markers = worldModel.markers();
            assertEquals(MARKERS, markers);

            RadarMap map = worldModel.radarMap();
            assertEquals(RADAR.cleanTimestamp(), map.cleanTimestamp());
            MapCell[] expected = RADAR.cells();
            MapCell[] cells = map.cells();
            assertThat(cells, arrayWithSize(expected.length));
            for (int i = 0; i < cells.length; i++) {
                assertThat("Cell[" + i + "]", cells[i].location(), pointCloseTo(expected[i].location(), MM));
                assertEquals(expected[i].contactTime(), cells[i].contactTime(), "Cell[" + i + "]");
                assertEquals(expected[i].echoTime(), cells[i].echoTime(), "Cell[" + i + "]");
                assertEquals(expected[i].echoWeight(), cells[i].echoWeight(), "Cell[" + i + "]");
            }

            step = reader.readMacroActionStep();
            assertNull(step);
        }
    }

    @Test
    void testReadHeader() throws Exception {
        writer.write(MACRO_ENV_STATE, AGENT_ACTION);
        writer.close();

        try (InferenceFileReader reader = new InferenceFileReader(FILE)) {
            reader.init();
            assertEquals(TOPOLOGY.width(), reader.topology().width());
            assertEquals(TOPOLOGY.height(), reader.topology().height());
            assertEquals(TOPOLOGY.gridSize(), reader.topology().gridSize(), MM);
            assertThat(reader.topology().center(), pointCloseTo(TOPOLOGY.center(), MM));
            assertEquals(WORLD_MODEL_SPEC.robotMapSize(), reader.worldSpec().robotMapSize());
            RobotSpec robotSpec = reader.worldSpec().robotSpec();

            assertEquals(DEFAULT_ROBOT_SPEC.sendInterval(), robotSpec.sendInterval());
            assertEquals(DEFAULT_ROBOT_SPEC.scanInterval(), robotSpec.scanInterval());
            assertThat(robotSpec.lidarFOV(), angleCloseTo(DEFAULT_ROBOT_SPEC.lidarFOV()));
            assertThat(robotSpec.cameraFOV(), angleCloseTo(DEFAULT_ROBOT_SPEC.cameraFOV()));
            assertThat(robotSpec.headFOV(), angleCloseTo(DEFAULT_ROBOT_SPEC.headFOV()));
            assertThat(robotSpec.directionRange(), angleCloseTo(DEFAULT_ROBOT_SPEC.directionRange()));
            assertThat(robotSpec.maxRotRange(), angleCloseTo(DEFAULT_ROBOT_SPEC.maxRotRange()));
            assertEquals(DEFAULT_ROBOT_SPEC.maxRotPps(), robotSpec.maxRotPps());
            assertEquals(DEFAULT_ROBOT_SPEC.maxSpeed(), robotSpec.maxSpeed());
            assertThat(robotSpec.headLocation(), pointCloseTo(DEFAULT_HEAD_X, DEFAULT_HEAD_Y, MM));
            assertEquals(DEFAULT_ROBOT_SPEC.maxRadarDistance(), robotSpec.maxRadarDistance(), MM);
            assertEquals(DEFAULT_ROBOT_SPEC.contactRadius(), robotSpec.contactRadius(), MM);
            assertEquals(DEFAULT_ROBOT_SPEC.frontLidarDistance(), robotSpec.frontLidarDistance(), MM);
            assertEquals(DEFAULT_ROBOT_SPEC.rearLidarDistance(), robotSpec.rearLidarDistance(), MM);
            assertEquals(DEFAULT_ROBOT_SPEC.cameraDistance(), robotSpec.cameraDistance(), MM);
            assertEquals(DEFAULT_TARGET_RANGE, robotSpec.targetRange(), MM);
            assertEquals(DEFAULT_ROBOT_SPEC.decelerateDistance(), robotSpec.decelerateDistance(), MM);
        }
    }

    @Test
    void testReope() throws Exception {
        writer.write(MACRO_ENV_STATE, AGENT_ACTION);
        writer.close();

        try (InferenceFileReader reader = new InferenceFileReader(FILE)) {
            Tuple2<BehaviourState, BehaviourAction> step = reader.readMacroActionStep();
            assertNotNull(step);
            BehaviourState state = step.v1();
            BehaviourAction action = step.v2();
            assertEquals(MoveActionId.MICRO_FORWARD_ACTION, action.moveActionId());
            assertEquals(HeadActionId.SCAN_ACTION, action.headActionId());
            assertEquals(MoveActionId.MICRO_FORWARD_ACTION, state.moveActionId());
            assertEquals(HeadActionId.SCAN_ACTION, state.headActionId());
            WorldModel worldModel = state.worldModel();

            RobotStatus robotStatus = worldModel.robotStatus();
            assertEquals(ROBOT_STATUS.robotTime(), robotStatus.robotTime());
            assertEquals(ROBOT_STATUS.contactsMessage(), robotStatus.contactsMessage());
            assertEquals(ROBOT_STATUS.lidarMessage(), robotStatus.lidarMessage());
            assertEquals(ROBOT_STATUS.cameraEvent(), robotStatus.cameraEvent());
            assertEquals(ROBOT_STATUS.motionMessage(), robotStatus.motionMessage());

            Map<Integer, FiducialMarker> markers = worldModel.markers();
            assertEquals(MARKERS, markers);

            RadarMap map = worldModel.radarMap();
            assertEquals(RADAR.cleanTimestamp(), map.cleanTimestamp());
            MapCell[] expected = RADAR.cells();
            MapCell[] cells = map.cells();
            assertThat(cells, arrayWithSize(expected.length));
            for (int i = 0; i < cells.length; i++) {
                assertThat("Cell[" + i + "]", cells[i].location(), pointCloseTo(expected[i].location(), MM));
                assertEquals(expected[i].contactTime(), cells[i].contactTime(), "Cell[" + i + "]");
                assertEquals(expected[i].echoTime(), cells[i].echoTime(), "Cell[" + i + "]");
                assertEquals(expected[i].echoWeight(), cells[i].echoWeight(), "Cell[" + i + "]");
            }

            step = reader.readMacroActionStep();
            assertNull(step);

            reader.close();
            int n = 0;
            while (reader.readMacroActionStep() != null) {
                n++;
            }
            assertEquals(1, n);
        }
    }

    @Test
    void testWrite() throws Exception {
        writer.write(MACRO_ENV_STATE, AGENT_ACTION);
        writer.close();
        // header size reduced from 115 to 85 bytes with protobuf
        assertEquals(5412, Files.size(FILE));
    }
}