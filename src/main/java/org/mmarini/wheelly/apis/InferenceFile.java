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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;
import java.util.function.IntToDoubleFunction;

import static java.util.Objects.requireNonNull;

/**
 * Stores and retrieves inference data
 */
public class InferenceFile implements InferenceReader, InferenceWriter {

    public static final WheellySupplyMessage DEFAULT_SUPPLY_MESSAGE = new WheellySupplyMessage(0, 0, 0, 0);
    public static final IntToDoubleFunction DEFAULT_DECODE_VOLTAGE = x -> 12d;

    /**
     * Returns the world model dumper
     *
     * @param spec     the world spec
     * @param topology the radar map topology
     * @param file     the files
     * @throws IOException in case of error
     */
    public static InferenceFile fromFile(WorldModelSpec spec, GridTopology topology, File file) throws IOException {
        file.getCanonicalFile().getParentFile().mkdirs();
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        return new InferenceFile(spec, topology, raf);
    }

    private final WorldModelSpec worldSpec;
    private final RandomAccessFile file;
    private final GridTopology topology;

    /**
     * Creates the world model reader
     *
     * @param worldSpec the world spec
     * @param topology  the radar grid topology
     * @param file      the file
     */
    public InferenceFile(WorldModelSpec worldSpec, GridTopology topology, RandomAccessFile file) {
        this.worldSpec = requireNonNull(worldSpec);
        this.file = requireNonNull(file);
        this.topology = requireNonNull(topology);
    }

    /**
     * Moves file cursor to the end of the file
     */
    public InferenceFile append() throws IOException {
        file.seek(file.length());
        return this;
    }

    /**
     * Clears the file
     */
    public InferenceFile clear() throws IOException {
        file.setLength(0);
        return this;
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    /**
     * Returns the position of file pointer
     */
    public long position() throws IOException {
        return file.getFilePointer();
    }

    /**
     * Returns the length of file
     */
    public long size() throws IOException {
        return file.length();
    }

    @Override
    public boolean readBoolean() throws IOException {
        return file.readBoolean();
    }

    @Override
    public double readDouble() throws IOException {
        return file.readDouble();
    }

    @Override
    public int readInt() throws IOException {
        return file.readInt();
    }

    @Override
    public long readLong() throws IOException {
        return file.readLong();
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
            double echoWeight = readDouble();
            double contactTime = readDouble();
            cells[i] = new MapCell(cells[i].location(), echoTime, echoWeight, contactTime);
        }
        return radarMap;
    }

    @Override
    public RobotStatus readStatus() throws IOException {
        long simTime = readLong();
        WheellyMotionMessage motion = readMotion();
        WheellyProxyMessage proxy = readProxy();
        WheellyContactsMessage contacts = readContacts();
        CameraEvent camera = readCamera();
        WheellyProxyMessage cameraProxy = readProxy();
        return new RobotStatus(worldSpec.robotSpec(), simTime, motion, proxy, contacts,
                DEFAULT_SUPPLY_MESSAGE, DEFAULT_DECODE_VOLTAGE, camera, cameraProxy);
    }

    @Override
    public String readString() throws IOException {
        return file.readUTF();
    }

    /**
     * Moves file cursor to begin of the file
     */
    public InferenceFile reset() throws IOException {
        file.seek(0);
        return this;
    }

    @Override
    public InferenceFile write(long data) throws IOException {
        file.writeLong(data);
        return this;
    }

    @Override
    public InferenceFile write(int data) throws IOException {
        file.writeInt(data);
        return this;
    }

    @Override
    public InferenceFile write(boolean data) throws IOException {
        file.writeBoolean(data);
        return this;
    }

    @Override
    public InferenceFile write(double data) throws IOException {
        file.writeDouble(data);
        return this;
    }

    @Override
    public InferenceFile write(String data) throws IOException {
        file.writeUTF(data);
        return this;
    }
}
