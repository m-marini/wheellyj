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

import org.mmarini.Tuple2;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Reads inference data
 */
public interface InferenceReader extends AutoCloseable, DataReader {

    /**
     * Returns the world model from file or null if end of file
     */
    default Tuple2<WorldModelSpec, GridTopology> readHeader() throws IOException {
        return Tuple2.of(readWorldSpec(), readTopology());
    }

    default Point2D readPoint2D() throws IOException {
        return new Point2D.Double(
                readDouble(),
                readDouble()
        );
    }

    /**
     * Returns the world model from file or null if end of file
     *
     * @throws IOException in case of error
     */
    default Tuple2<WorldModel, RobotCommands> readRecord() throws IOException {
        WorldModel model = readModel();
        RobotCommands commands = readCommands();
        return Tuple2.of(model, commands);
    }

    /**
     * Returns the robot command
     *
     * @throws IOException in case of error
     */
    default RobotCommands readCommands() throws IOException {
        boolean scan = readBoolean();
        Complex scanDirection = readDeg();
        boolean move = readBoolean();
        boolean halt = readBoolean();
        Complex moveDirection = readDeg();
        int speed = readInt();
        return new RobotCommands(scan, scanDirection, move, halt, moveDirection, speed);
    }

    default GridTopology readTopology() throws IOException {
        return GridTopology.create(readPoint2D(),
                readInt(),
                readInt(),
                readDouble());
    }

    /**
     * Returns the camera event
     *
     * @throws IOException in case of error
     */
    default CorrelatedCameraEvent readCorrelatedCamera() throws IOException {
        CameraEvent camera = readCamera();
        WheellyLidarMessage lidar = readLidarMessage();
        return new CorrelatedCameraEvent(camera, lidar);
    }

    /**
     * Returns the angle (DEG) from file or null if end of file
     */
    default Complex readDeg() throws IOException {
        return Complex.fromDeg(readInt());
    }

    /**
     * Returns the camera event
     *
     * @throws IOException in case of error
     */
    default CameraEvent readCamera() throws IOException {
        long timestamp = readLong();
        String qrCode = readString();
        int width = readInt();
        int height = readInt();
        return new CameraEvent(timestamp, qrCode, width, height, null, Complex.DEG0);
    }

    /**
     * Returns the contact message
     *
     * @throws IOException in case of error
     */
    default WheellyContactsMessage readContacts() throws IOException {
        long simulationTime = readLong();
        boolean frontSensor = readBoolean();
        boolean rearSensor = readBoolean();
        boolean canMoveForward = readBoolean();
        boolean canMoveBackward = readBoolean();
        return new WheellyContactsMessage(simulationTime,
                frontSensor, rearSensor, canMoveForward, canMoveBackward);
    }

    /**
     * Returns the lidar message
     */
    default WheellyLidarMessage readLidarMessage() throws IOException {
        long simTime = readLong();
        int headDirectionDeg = readInt();
        int frontDistance = readInt();
        int rearDistance = readInt();
        double xPulses = readDouble();
        double yPulses = readDouble();
        int robotYawDeg = readInt();
        return new WheellyLidarMessage(simTime, frontDistance, rearDistance, xPulses, yPulses, robotYawDeg, headDirectionDeg);
    }

    /**
     * Returns the marker map
     *
     * @throws IOException in case of error
     */
    default Map<String, LabelMarker> readMarkers() throws IOException {
        int n = readInt();
        List<LabelMarker> markers = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String qrCode = readString();
            Point2D location = readPoint2D();
            double weight = readFloat();
            long markerTime = readLong();
            long cleanTime = readLong();
            markers.add(new LabelMarker(qrCode, location, weight, markerTime, cleanTime));
        }
        return markers.stream()
                .collect(Collectors.toMap(
                        LabelMarker::label,
                        UnaryOperator.identity()
                ));
    }

    default RobotSpec readRobotSpec() throws IOException {
        return new RobotSpec(readDouble(),
                readDeg(),
                readDouble(),
                readDeg(),
                readPoint2D(),
                readDouble(),
                readDouble(),
                readDouble(),
                readDeg());
    }

    default WorldModelSpec readWorldSpec() throws IOException {
        RobotSpec robotSpec = readRobotSpec();
        int numSector = readInt();
        int gridSize = readInt();
        return new WorldModelSpec(robotSpec, numSector, gridSize);
    }

    /**
     * Returns the model
     *
     * @throws IOException in case of error
     */
    WorldModel readModel() throws IOException;

    /**
     * Returns the motion message
     *
     * @throws IOException in case of error
     */
    default WheellyMotionMessage readMotion() throws IOException {
        long simulationTime = readLong();
        double xPulses = readFloat();
        double yPulses = readFloat();
        int directionDeg = readInt();
        double leftPps = readFloat();
        double rightPps = readFloat();
        int imuFailure = readInt();
        boolean halt = readBoolean();
        int leftTargetPps = readInt();
        int rightTargetPps = readInt();
        int leftPower = readInt();
        int rightPower = readInt();
        return new WheellyMotionMessage(simulationTime, xPulses, yPulses,
                directionDeg, leftPps, rightPps, imuFailure, halt, leftTargetPps, rightTargetPps, leftPower, rightPower);
    }

    /**
     * Returns the radar map
     *
     * @throws IOException in case of error
     */
    RadarMap readRadar() throws IOException;

    /**
     * Returns the robot status
     *
     * @throws IOException in case of error
     */
    RobotStatus readStatus() throws IOException;
}
