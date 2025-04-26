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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.Tuple2;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InferenceFileTest {

    public static final File FILE = new File("tmp/dump.bin");
    public static final double GRID_SIZE = 0.3;
    public static final GridTopology TOPOLOGY = new GridTopology(new Point2D.Double(), 51, 51, GRID_SIZE);
    public static final RadarMap RADAR = RadarMap.empty(TOPOLOGY);
    public static final int NUM_SECTORS = 24;
    public static final Complex RECEPTIVE_ANGLE = Complex.fromDeg(15);
    public static final double MAX_RADAR_DISTANCE = 3d;
    private static final double CONTACT_RADIUS = 0.28;
    private static final double MARKER_SIZE = 0.3;
    private static final int GRID_MAP_SIZE = 31;
    public static final WorldModelSpec WORLD_MODEL_SPEC = new WorldModelSpec(new RobotSpec(MAX_RADAR_DISTANCE, RECEPTIVE_ANGLE, CONTACT_RADIUS),
            NUM_SECTORS, GRID_MAP_SIZE, MARKER_SIZE);
    public static final WheellyProxyMessage PROXY_MESSAGE = new WheellyProxyMessage(1, 2, 3, 4,
            5, 6, 7, 8);
    public static final WheellyProxyMessage CAMERA_PROXY_MESSAGE = new WheellyProxyMessage(2, 3, 4, 5,
            6, 7, 8, 9);
    public static final WheellyMotionMessage MOTION_MESSAGE = new WheellyMotionMessage(1, 2, 3, 4, 5,
            6, 7, 8, 9, true, 10, 11, 12, 13);
    public static final WheellyContactsMessage CONTACTS_MESSAGE = new WheellyContactsMessage(1, 2, 3, true,
            true, true, true);
    public static final CameraEvent CAMERA_EVENT = new CameraEvent(1, "?", 3, 4, null);
    public static final RobotStatus ROBOT_STATUS = new RobotStatus(WORLD_MODEL_SPEC.robotSpec(), 1, MOTION_MESSAGE, PROXY_MESSAGE,
            CONTACTS_MESSAGE, InferenceFile.DEFAULT_SUPPLY_MESSAGE, InferenceFile.DEFAULT_DECODE_VOLTAGE, CAMERA_EVENT, CAMERA_PROXY_MESSAGE);
    public static final RobotCommands COMMANDS = new RobotCommands(true, Complex.DEG0, false, true, Complex.DEG90, 20);
    private static final Map<String, LabelMarker> MARKERS = Map.of(
            "?", new LabelMarker("?", new Point2D.Double(1, 2), 1, 2, 3));
    public static final WorldModel MODEL = new WorldModel(WORLD_MODEL_SPEC, ROBOT_STATUS, RADAR, MARKERS, null, null, null);

    private InferenceFile file;

    @BeforeEach
    void setUp() throws IOException {
        this.file = InferenceFile.fromFile(WORLD_MODEL_SPEC, TOPOLOGY, FILE).clear();
    }

    @AfterEach
    void tearDown() throws IOException {
        file.close();
        FILE.delete();
    }

    @Test
    void testCamera() throws IOException {
        file.write(CAMERA_EVENT);
        CameraEvent cameraRead = file.reset().readCamera();
            assertEquals(CAMERA_EVENT, cameraRead);
    }

    @Test
    void testCommands() throws IOException {
        file.write(COMMANDS);
        RobotCommands commandRead = file.reset().readCommands();
            assertEquals(COMMANDS, commandRead);
    }

    @Test
    void testContacts() throws IOException {
        file.write(CONTACTS_MESSAGE);
        WheellyContactsMessage contactsRead = file.reset().readContacts();
            assertEquals(CONTACTS_MESSAGE, contactsRead);
    }

    @Test
    void testInference() throws IOException {
        file.write(MODEL, COMMANDS);
        Tuple2<WorldModel, RobotCommands> t = file.reset().read();
            WorldModel model = t._1;
            RadarMap radarRead = model.radarMap();
            assertEquals(MODEL.robotStatus(), model.robotStatus());
            assertEquals(MODEL.markers(), model.markers());
            assertEquals(RADAR.cleanTimestamp(), radarRead.cleanTimestamp());
            assertEquals(RADAR.topology(), radarRead.topology());
            assertArrayEquals(RADAR.cells(), radarRead.cells());
            assertArrayEquals(RADAR.vertices(), radarRead.vertices());
            assertArrayEquals(RADAR.verticesByCells(), radarRead.verticesByCells());
            RobotCommands commands = t._2;
            assertEquals(COMMANDS, commands);
    }

    @Test
    void testMarkers() throws IOException {
        file.write(MARKERS);
        Map<String, LabelMarker> markersRead = file.reset().readMarkers();
            assertEquals(MARKERS, markersRead);
    }

    @Test
    void testModel() throws IOException {
        file.write(MODEL);
        WorldModel model = file.reset().readModel();
            RadarMap radarRead = model.radarMap();
            assertEquals(MODEL.robotStatus(), model.robotStatus());
            assertEquals(MODEL.markers(), model.markers());
            assertEquals(RADAR.cleanTimestamp(), radarRead.cleanTimestamp());
            assertEquals(RADAR.topology(), radarRead.topology());
            assertArrayEquals(RADAR.cells(), radarRead.cells());
            assertArrayEquals(RADAR.vertices(), radarRead.vertices());
            assertArrayEquals(RADAR.verticesByCells(), radarRead.verticesByCells());
    }

    @Test
    void testMotion() throws IOException {
        file.write(MOTION_MESSAGE);
        WheellyMotionMessage motionRead = file.reset().readMotion();
            assertEquals(MOTION_MESSAGE, motionRead);
        }

    @Test
    void testProxy() throws IOException {
        file.write(PROXY_MESSAGE);
        WheellyProxyMessage proxyRead = file.reset().readProxy();
            assertEquals(PROXY_MESSAGE, proxyRead);
    }

    @Test
    void testRadarMap() throws IOException {
        file.write(RADAR);
        RadarMap radarRead = file.reset().readRadar();
            assertEquals(RADAR.cleanTimestamp(), radarRead.cleanTimestamp());
            assertEquals(RADAR.topology(), radarRead.topology());
            assertArrayEquals(RADAR.cells(), radarRead.cells());
            assertArrayEquals(RADAR.vertices(), radarRead.vertices());
            assertArrayEquals(RADAR.verticesByCells(), radarRead.verticesByCells());
    }

    @Test
    void testStatus() throws IOException {
        file.write(ROBOT_STATUS);
        RobotStatus contactsRead = file.reset().readStatus();
            assertEquals(ROBOT_STATUS, contactsRead);
    }
}