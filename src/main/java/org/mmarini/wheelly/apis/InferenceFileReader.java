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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.IntToDoubleFunction;

import static java.util.Objects.requireNonNull;

/**
 * Stores and retrieves inference data
 */
public class InferenceFileReader implements InferenceReader {

    public static final WheellySupplyMessage DEFAULT_SUPPLY_MESSAGE = new WheellySupplyMessage(0, 0, 0, 0);
    public static final IntToDoubleFunction DEFAULT_DECODE_VOLTAGE = x -> 12d;
    public static final int BUFFER_SIZE = 128;

    /**
     * Returns the world model dumper
     *
     * @param spec     the world spec
     * @param topology the radar map topology
     * @param file     the files
     * @throws IOException in case of error
     */
    public static InferenceFileReader fromFile(WorldModelSpec spec, GridTopology topology, File file) throws IOException {
        file.getCanonicalFile().getParentFile().mkdirs();
        return new InferenceFileReader(spec, topology, new FileInputStream(requireNonNull(file)), file.length());
    }

    private final WorldModelSpec worldSpec;
    private final InputStream file;
    private final GridTopology topology;
    private final byte[] buffer;
    private final long size;
    private long position;

    /**
     * Creates the world model reader
     *
     * @param worldSpec the world spec
     * @param topology  the radar grid topology
     * @param file      the file
     * @param size the size of file
     */
    protected InferenceFileReader(WorldModelSpec worldSpec, GridTopology topology, InputStream file, long size) {
        this.worldSpec = requireNonNull(worldSpec);
        this.file = requireNonNull(file);
        this.topology = requireNonNull(topology);
        this.size = size;
        this.buffer = new byte[BUFFER_SIZE];
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    /**
     * Returns the position of file pointer
     */
    public long position() throws IOException {
        return position;
    }

    /**
     * Reads the chunk of bytes
     *
     * @param buffer the buffer
     * @param offset the start offset
     * @param length the length
     */
    private InferenceFileReader read(byte[] buffer, int offset, int length) throws IOException {
        int n = file.read(buffer, offset, length);
        if (n != length) {
            throw new EOFException();
        }
        position += n;
        return this;
    }

    @Override
    public boolean readBoolean() throws IOException {
        read(buffer, 0, 1);
        return buffer[0] != 0;
    }

    @Override
    public double readDouble() throws IOException {
        long value = readLong();
        return Double.longBitsToDouble(value);
    }

    @Override
    public int readInt() throws IOException {
        read(buffer, 0, Integer.BYTES);
        int result = 0;
        for (int i = Integer.BYTES - 1; i >= 0; i--) {
            result <<= 8;
            result += buffer[i] & 0xff;
        }
        return result;
    }

    @Override
    public long readLong() throws IOException {
        read(buffer, 0, Long.BYTES);
        long result = 0;
        for (int i = Long.BYTES - 1; i >= 0; i--) {
            result <<= 8;
            result += buffer[i] & 0xff;
        }
        return result;
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
        int n = readInt();
        byte[] buffer = this.buffer;
        if (n > buffer.length) {
            // Reallocate a new buffer
            buffer = new byte[n];
        }
        read(buffer, 0, n);
        return new String(buffer, 0, n, StandardCharsets.UTF_8);
    }

    /**
     * Returns the length of file
     */
    public long size() throws IOException {
        return size;
    }
}
