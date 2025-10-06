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

import static org.junit.jupiter.api.Assertions.*;

class InferenceFileTest {

    public static final File FILE = new File("tmp/dump.bin");
    public static final double GRID_SIZE = 0.3;
    public static final GridTopology TOPOLOGY = GridTopology.create(new Point2D.Double(), 51, 51, GRID_SIZE);
    public static final RadarMap RADAR = RadarMap.empty(TOPOLOGY);
    public static final int NUM_SECTORS = 24;
    public static final Complex RECEPTIVE_ANGLE = Complex.fromDeg(15);
    public static final double MAX_RADAR_DISTANCE = 3d;
    public static final WheellyProxyMessage PROXY_MESSAGE = new WheellyProxyMessage(2, 4,
            5, 6, 7, 8);
    public static final WheellyMotionMessage MOTION_MESSAGE = new WheellyMotionMessage(2, 4, 5,
            6, 7, 8, 9, true, 10, 11, 12, 13);
    public static final WheellyContactsMessage CONTACTS_MESSAGE = new WheellyContactsMessage(2, true,
            true, true, true);
    public static final CameraEvent CAMERA_EVENT = new CameraEvent(0, "?", 3, 4, null, Complex.DEG0);
    public static final CorrelatedCameraEvent CORRELATED_CAMERA_EVENT = new CorrelatedCameraEvent(CAMERA_EVENT, PROXY_MESSAGE);
    public static final RobotCommands COMMANDS = new RobotCommands(true, Complex.DEG0, false, true, Complex.DEG90, 20);
    public static final double CONTACT_RADIUS = 0.28;
    public static final double MARKER_SIZE = 0.3;
    public static final int GRID_MAP_SIZE = 31;
    public static final WorldModelSpec WORLD_MODEL_SPEC = new WorldModelSpec(new RobotSpec(MAX_RADAR_DISTANCE, RECEPTIVE_ANGLE, CONTACT_RADIUS, Complex.DEG0),
            NUM_SECTORS, GRID_MAP_SIZE, MARKER_SIZE);
    public static final RobotStatus ROBOT_STATUS = new RobotStatus(WORLD_MODEL_SPEC.robotSpec(), 1, MOTION_MESSAGE, PROXY_MESSAGE,
            CONTACTS_MESSAGE, InferenceFileReader.DEFAULT_SUPPLY_MESSAGE, InferenceFileReader.DEFAULT_DECODE_VOLTAGE, CORRELATED_CAMERA_EVENT);
    private static final Map<String, LabelMarker> MARKERS = Map.of(
            "?", new LabelMarker("?", new Point2D.Double(1, 2), 1, 2, 3));
    public static final WorldModel MODEL = new WorldModel(WORLD_MODEL_SPEC, ROBOT_STATUS, RADAR, MARKERS, null, null, null);

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
            RobotCommands commandRead = reader.readCommands();
            assertEquals(COMMANDS, commandRead);
        }
    }

    @Test
    void testContacts() throws IOException {
        writer.write(CONTACTS_MESSAGE);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            WheellyContactsMessage contactsRead = reader.readContacts();
            assertEquals(CONTACTS_MESSAGE, contactsRead);
        }
    }

    @Test
    void testCorrelatedCamera() throws IOException {
        writer.write(CORRELATED_CAMERA_EVENT);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            CorrelatedCameraEvent cameraRead = reader.readCorrelatedCamera();
            assertEquals(CORRELATED_CAMERA_EVENT, cameraRead);
        }
    }

    @Test
    void testHeader() {
        Tuple2<WorldModelSpec, GridTopology> header = assertDoesNotThrow(() -> {
            writer.writeHeader(WORLD_MODEL_SPEC, TOPOLOGY);
            try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
                return reader.readHeader();
            }
        });
        WorldModelSpec spec = header._1;
        GridTopology topology = header._2;
        assertEquals(WORLD_MODEL_SPEC, spec);
        assertEquals(TOPOLOGY.center(), topology.center());
        assertEquals(TOPOLOGY.width(), topology.width());
        assertEquals(TOPOLOGY.height(), topology.height());
        assertEquals(TOPOLOGY.gridSize(), topology.gridSize());
    }

    @Test
    void testInference() throws IOException {
        writer.write(MODEL, COMMANDS);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            Tuple2<WorldModel, RobotCommands> t = reader.readRecord();
            WorldModel model = t._1;
            RadarMap radarRead = model.radarMap();
            assertEquals(MODEL.robotStatus(), model.robotStatus());
            assertEquals(MODEL.markers(), model.markers());
            assertEquals(RADAR.cleanTimestamp(), radarRead.cleanTimestamp());
            assertEquals(RADAR.topology().width(), radarRead.topology().width());
            assertEquals(RADAR.topology().height(), radarRead.topology().height());
            assertEquals(RADAR.topology().gridSize(), radarRead.topology().gridSize());
            assertEquals(RADAR.topology().center(), radarRead.topology().center());
            assertArrayEquals(RADAR.cells(), radarRead.cells());
            RobotCommands commands = t._2;
            assertEquals(COMMANDS, commands);
        }
    }

    @Test
    void testMarkers() throws IOException {
        writer.write(MARKERS);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            Map<String, LabelMarker> markersRead = reader.readMarkers();
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
        }
    }

    @Test
    void testProxy() throws IOException {
        writer.write(PROXY_MESSAGE);
        try (InferenceFileReader reader = InferenceFileReader.fromFile(FILE)) {
            WheellyProxyMessage proxyRead = reader.readProxy();
            assertEquals(PROXY_MESSAGE, proxyRead);
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