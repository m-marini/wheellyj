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

import java.io.IOException;
import java.util.Map;

/**
 * World model dumper
 */
public interface InferenceWriter extends AutoCloseable {

    /**
     * Writes a long number
     *
     * @param data the number
     * @throws IOException in case of error
     */
    InferenceWriter write(long data) throws IOException;

    /**
     * Writes an int number
     *
     * @param data the number
     * @throws IOException in case of error
     */
    InferenceWriter write(int data) throws IOException;

    /**
     * Writes a boolean value
     *
     * @param data the value
     * @throws IOException in case of error
     */
    InferenceWriter write(boolean data) throws IOException;

    /**
     * Writes a double value
     *
     * @param data the value
     * @throws IOException in case of error
     */
    InferenceWriter write(double data) throws IOException;

    /**
     * Writes a string value
     *
     * @param data the value
     * @throws IOException in case of error
     */
    InferenceWriter write(String data) throws IOException;


    /**
     * Writes camera events
     *
     * @param camera the camera event
     */
    default InferenceWriter write(CameraEvent camera) throws IOException {
        return write(camera.timestamp())
                .write(camera.qrCode())
                .write(camera.width())
                .write(camera.height());
    }

    /**
     * Writes robot commands
     *
     * @param commands the commands
     */
    default InferenceWriter write(RobotCommands commands) throws IOException {
        return write(commands.scan())
                .write(commands.scanDirection().toIntDeg())
                .write(commands.move())
                .write(commands.halt())
                .write(commands.moveDirection().toIntDeg())
                .write(commands.speed());
    }

    /**
     * Writes the contact message
     *
     * @param contacts the contact message
     */
    default InferenceWriter write(WheellyContactsMessage contacts) throws IOException {
        return write(contacts.localTime())
                .write(contacts.simulationTime())
                .write(contacts.remoteTime())
                .write(contacts.frontSensors())
                .write(contacts.rearSensors())
                .write(contacts.canMoveForward())
                .write(contacts.canMoveBackward());
    }

    /**
     * Writes the markers
     *
     * @param markers the markers
     */
    default InferenceWriter write(Map<String, LabelMarker> markers) throws IOException {
        write(markers.size());
        for (LabelMarker marker : markers.values()) {
            write(marker.label())
                    .write(marker.location().getX())
                    .write(marker.location().getY())
                    .write(marker.weight())
                    .write(marker.markerTime())
                    .write(marker.cleanTime());
        }
        return this;
    }

    /**
     * Writes the motion message
     *
     * @param motion the motion message
     */
    default InferenceWriter write(WheellyMotionMessage motion) throws IOException {
        return write(motion.localTime())
                .write(motion.simulationTime())
                .write(motion.remoteTime())
                .write(motion.xPulses())
                .write(motion.yPulses())
                .write(motion.directionDeg())
                .write(motion.leftPps())
                .write(motion.rightPps())
                .write(motion.imuFailure())
                .write(motion.halt())
                .write(motion.leftTargetPps())
                .write(motion.rightTargetPps())
                .write(motion.leftPower())
                .write(motion.rightPower());
    }

    /**
     * Writes the radar map
     *
     * @param radarMap the radar map
     */
    default InferenceWriter write(RadarMap radarMap) throws IOException {
        write(radarMap.cleanTimestamp());
        MapCell[] cells = radarMap.cells();
        for (MapCell cell : cells) {
            write(cell.echoTime())
                    .write(cell.echoWeight())
                    .write(cell.contactTime());
        }
        return this;
    }

    /**
     * Writes the proxy message
     *
     * @param proxy the proxy message
     */
    default InferenceWriter write(WheellyProxyMessage proxy) throws IOException {
        return write(proxy.localTime())
                .write(proxy.simulationTime())
                .write(proxy.remoteTime())
                .write(proxy.sensorDirectionDeg())
                .write(proxy.echoDelay())
                .write(proxy.xPulses())
                .write(proxy.yPulses())
                .write(proxy.echoYawDeg());
    }

    /**
     * Writes the model
     *
     * @param model    the model
     * @param commands the commands
     */
    default InferenceWriter write(WorldModel model, RobotCommands commands) throws IOException {
        return write(model).write(commands);
    }

    /**
     * Writes the model
     *
     * @param model the model
     */
    default InferenceWriter write(WorldModel model) throws IOException {
        return write(model.robotStatus())
                .write(model.markers())
                .write(model.radarMap());
    }

    /**
     * Writes the status
     *
     * @param status the status
     */
    default InferenceWriter write(RobotStatus status) throws IOException {
        return write(status.simulationTime())
                .write(status.motionMessage())
                .write(status.proxyMessage())
                .write(status.contactsMessage())
                .write(status.cameraEvent())
                .write(status.cameraProxyMessage());

    }
}
