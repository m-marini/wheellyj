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

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.AreaExpression.*;

/**
 * Computes the label marker locator
 *
 * @param decayTime           the decay time (ms)
 * @param correlationInterval the correlation interval (ms)
 * @param maxDistance         the maximum echo distance (m)
 * @param receptiveAngle      the receptive angle of camera
 * @param markerSize          the size of marker (m)
 */
public record MarkerLocator(long decayTime, long correlationInterval, double maxDistance, Complex receptiveAngle,
                            double markerSize) {

    /**
     * Creates the marker locator
     *
     * @param decayTime           the decay time (ms)
     * @param correlationInterval the correlation interval (ms)
     * @param maxDistance         the maximum echo distance (m)
     * @param receptiveAngle      the receptive angle of camera
     * @param markerSize          the size of marker (m)
     */
    public MarkerLocator(long decayTime, long correlationInterval, double maxDistance, Complex receptiveAngle, double markerSize) {
        this.decayTime = decayTime;
        this.correlationInterval = correlationInterval;
        this.maxDistance = maxDistance;
        this.receptiveAngle = requireNonNull(receptiveAngle);
        this.markerSize = markerSize;
    }

    /**
     * Returns the cleaning area
     *
     * @param center    the center of area
     * @param direction the direction of area
     */
    private Parser createCleaningArea(Point2D center, Complex direction, double distance) {
        return and(
                circle(center, distance),
                angle(center, direction, receptiveAngle)
        ).createParser();
    }

    /**
     * Returns the filtered map area
     *
     * @param map       the map of marker
     * @param center    the center of cleaning area
     * @param direction the direction of cleaning area
     */
    private Map<String, LabelMarker> filterCleaningArea(Map<String, LabelMarker> map, Point2D center, Complex direction, double distance) {
        List<LabelMarker> markers = map.values().stream().toList();
        Parser parser = createCleaningArea(center, direction, distance);
        return markers.stream()
                .filter(marker ->
                        !parser.test(marker.location()))
                .collect(Collectors.toMap(
                        LabelMarker::label,
                        x -> x
                ));
    }

    /**
     * Returns the updated label marker map by camera event and proxy message
     *
     * @param map          the current map
     * @param cameraEvent  the camera event
     * @param proxyMessage the proxy message
     */
    public Map<String, LabelMarker> update(Map<String, LabelMarker> map, CameraEvent cameraEvent, WheellyProxyMessage proxyMessage) {
        requireNonNull(map);
        requireNonNull(proxyMessage);
        double distance = proxyMessage.echoDelay() * RobotStatus.DISTANCE_SCALE;
        Point2D robotLocation = RobotStatus.pulses2Location(proxyMessage.xPulses(), proxyMessage.yPulses());
        if (cameraEvent == null) {
            // proxy message only
            return filterCleaningArea(map, robotLocation, proxyMessage.echoDirection(), distance + markerSize);
        } else if (proxyMessage.simulationTime() > cameraEvent.timestamp() && proxyMessage.simulationTime() <= cameraEvent.timestamp() + correlationInterval) {
            // Correlated messages
            if (cameraEvent.qrCode().equals(CameraEvent.UNKNOWN_QR_CODE)) {
                // no recognized qrcode
                return filterCleaningArea(map, robotLocation, proxyMessage.echoDirection(), distance + markerSize);
            } else {
                if (distance == 0 || distance > maxDistance) {
                    // no echo
                    return filterCleaningArea(map, robotLocation, proxyMessage.echoDirection(), maxDistance + markerSize);
                } else {
                    Point2D echoLocation = proxyMessage.echoDirection().at(robotLocation, distance);
                    LabelMarker marker = map.get(cameraEvent.qrCode());
                    LabelMarker newMarker;
                    if (marker != null) {
                        // existing label
                        // Time interval between previous proxy time
                        long dt = proxyMessage.simulationTime() - marker.time();
                        double gamma = Math.exp(-(double) dt / decayTime);
                        double notGamma = 1 - gamma;
                        double x = marker.location().getX() * gamma + echoLocation.getX() * notGamma;
                        double y = marker.location().getY() * gamma + echoLocation.getY() * notGamma;
                        newMarker = marker.setLocation(new Point2D.Double(x, y))
                                .setTime(proxyMessage.simulationTime());
                    } else {
                        // new valid label
                        newMarker = new LabelMarker(cameraEvent.qrCode(), proxyMessage.simulationTime(), echoLocation);
                    }
                    Map<String, LabelMarker> newMap = new HashMap<>(map);
                    newMap.put(newMarker.label(), newMarker);
                    return newMap;
                }
            }
        }
        return map;
    }
}
