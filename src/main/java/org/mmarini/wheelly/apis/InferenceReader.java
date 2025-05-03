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
 * Reads infrence data
 */
public interface InferenceReader extends AutoCloseable, DataReader {

    /**
     * Returns the world model from file or null if end of file
     *
     * @throws IOException in case of error
     */
    default Tuple2<WorldModel, RobotCommands> read() throws IOException {
        WorldModel model = readModel();
        RobotCommands commands = readCommands();
        return Tuple2.of(model, commands);
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
        return new CameraEvent(timestamp, qrCode, width, height, null);
    }

    /**
     * Returns the robot command
     *
     * @throws IOException in case of error
     */
    default RobotCommands readCommands() throws IOException {
        boolean scan = readBoolean();
        Complex scanDirection = Complex.fromDeg(readInt());
        boolean move = readBoolean();
        boolean halt = readBoolean();
        Complex moveDirection = Complex.fromDeg(readInt());
        int speed = readInt();
        return new RobotCommands(scan, scanDirection, move, halt, moveDirection, speed);
    }

    /**
     * Returns the contact message
     *
     * @throws IOException in case of error
     */
    default WheellyContactsMessage readContacts() throws IOException {
        long localTime = readLong();
        long simulationTime = readLong();
        long remoteTime = readLong();
        boolean frontSensor = readBoolean();
        boolean rearSensor = readBoolean();
        boolean canMoveForward = readBoolean();
        boolean canMoveBackward = readBoolean();
        return new WheellyContactsMessage(localTime, simulationTime, remoteTime,
                frontSensor, rearSensor, canMoveForward, canMoveBackward);
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
            Point2D location = new Point2D.Float(readFloat(), readFloat());
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
        long localTime = readLong();
        long simulationTime = readLong();
        long remoteTime = readLong();
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
        return new WheellyMotionMessage(localTime, simulationTime, remoteTime, xPulses, yPulses,
                directionDeg, leftPps, rightPps, imuFailure, halt, leftTargetPps, rightTargetPps, leftPower, rightPower);
    }

    /**
     * Returns the wheelly motion message
     *
     * @throws IOException in case of error
     */
    default WheellyProxyMessage readProxy() throws IOException {
        long localTime = readLong();
        long simTime = readLong();
        long remoteTime = readLong();
        int sensorDirectionDeg = readInt();
        long echoDelay = readLong();
        double xPulse = readFloat();
        double yPulse = readFloat();
        int echoYawDeg = readInt();
        return new WheellyProxyMessage(localTime, simTime, remoteTime, sensorDirectionDeg, echoDelay, xPulse, yPulse, echoYawDeg);
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
