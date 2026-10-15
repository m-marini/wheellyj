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

package org.mmarini.wheelly.apis;

import org.mmarini.Tuple2;
import org.mmarini.wheelly.envs.MacroEnvState;
import org.mmarini.wheelly.fsm.AgentAction;
import org.mmarini.wheelly.fsm.HeadActionId;
import org.mmarini.wheelly.fsm.MoveActionId;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.function.IntToDoubleFunction;

import static java.util.Objects.requireNonNull;

/**
 * Stores and retrieves inference data
 */
public class InferenceFileReader extends DataFileReader implements InferenceReader {

    public static final WheellySupplyMessage DEFAULT_SUPPLY_MESSAGE = new WheellySupplyMessage(0, 0);
    public static final IntToDoubleFunction DEFAULT_DECODE_VOLTAGE = x -> 12d;

    /**
     * Returns the world model dumper
     *
     * @param file the files
     * @throws IOException in case of error
     */
    public static InferenceFileReader fromFile(File file) throws IOException {
        requireNonNull(file);
        file.getCanonicalFile().getParentFile().mkdirs();
        FileInputStream stream = new FileInputStream(file);
        return new InferenceFileReader(stream, file.length()).init();
    }

    private WorldModelSpec worldSpec;
    private GridTopology topology;
    private InferenceWriter.Type type;

    /**
     * Creates the world model reader
     *
     * @param file the file
     * @param size the size of file
     */
    protected InferenceFileReader(InputStream file, long size) {
        super(file, size);
    }

    /**
     * Initialises file by reading the header
     */
    private InferenceFileReader init() throws IOException {
        type = InferenceWriter.Type.values()[readInt()];
        worldSpec = readWorldSpec();
        topology = readTopology();
        return this;
    }

    private AgentAction readAgentAction() throws IOException {
        MoveActionId move = readMoveActionId();
        HeadActionId head = readHeadActionId();
        return new AgentAction(move, head);
    }

    public MacroEnvState readMacroEnvState() throws IOException {
        WorldModel model = readModel();
        MoveActionId move = readMoveActionId();
        HeadActionId head = readHeadActionId();
        return new MacroEnvState(model, move, head);
    }

    public Tuple2<MacroEnvState, AgentAction> readMacroEnvStep() throws IOException {
        MacroEnvState state = readMacroEnvState();
        AgentAction action = readAgentAction();
        return Tuple2.of(state, action);
    }

    @Override
    public WorldModel readModel() throws IOException {
        RobotStatus robotStatus = readStatus();
        Map<String, LabelMarker> markers = readMarkers();
        RadarMap radar = readRadar();
        return new WorldModel(worldSpec, robotStatus, radar, markers, null, null, null);
    }

    @Override
    public RadarMap readRadar() throws IOException {
        long cleanTimestamp = readLong();
        RadarMap radarMap = RadarMap.empty(topology).setCleanTimestamp(cleanTimestamp);
        MapCell[] cells = radarMap.cells();
        for (int i = 0; i < cells.length; i++) {
            long echoTime = readLong();
            double echoWeight = readFloat();
            long contactTime = readLong();
            cells[i] = new MapCell(cells[i].location(), echoTime, echoWeight, contactTime);
        }
        return radarMap;
    }

    @Override
    public RobotStatus readStatus() throws IOException {
        long simTime = readLong();
        WheellyMotionMessage motion = readMotion();
        WheellyContactsMessage contacts = readContacts();
        CorrelatedCameraEvent camera = readCorrelatedCamera();
        WheellyLidarMessage lidar = readLidarMessage();
        return new RobotStatus(worldSpec.robotSpec(), simTime, motion, contacts,
                DEFAULT_SUPPLY_MESSAGE, DEFAULT_DECODE_VOLTAGE, camera, lidar);
    }

    /**
     * Returns the grid topology
     */
    public GridTopology topology() {
        return topology;
    }

    public InferenceWriter.Type type() {
        return type;
    }

    /**
     * Returns the world model spec
     */
    public WorldModelSpec worldSpec() {
        return worldSpec;
    }
}
