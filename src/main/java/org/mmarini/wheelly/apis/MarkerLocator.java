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

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.yaml.Locator;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.AreaExpression.*;

/**
 * Computes the label marker locator
 *
 * @param locationDecay       the decay marker time (ms)
 * @param cleanDecay          the clean decay time (ms)
 * @param correlationInterval the correlation interval (ms)
 * @param minNumberEvents     the minimum number of unknown qr code events to update the marker map
 * @param markerSize          the marker size (m)
 * @param status              the marker locator status
 */
public record MarkerLocator(double locationDecay, double cleanDecay, long correlationInterval, int minNumberEvents,
                            double markerSize, AtomicReference<MarkerLocatorStatus> status) {

    public static final double EPSILON_1DEG = sin(toRadians(1));
    public static final Complex CLEAR_REDUCTION_ANGLE = Complex.fromDeg(3);

    /**
     * Returns the empty radar from definition
     *
     * @param root    the document
     * @param locator the locator of radar map definition
     */
    public static MarkerLocator create(JsonNode root, Locator locator) {
        double decay = locator.path("markerDecay").getNode(root).asDouble();
        double cleanDecay = locator.path("markerCleanDecay").getNode(root).asDouble();
        long correlationInterval = locator.path("correlationInterval").getNode(root).asLong();
        double markerSize = locator.path("markerSize").getNode(root).asDouble();
        int minNumberEvents = locator.path("minNumberEvents").getNode(root).asInt(1);
        return new MarkerLocator(decay, cleanDecay, correlationInterval, minNumberEvents, markerSize, new AtomicReference<>(new MarkerLocatorStatus(0, null)));
    }

    /**
     * Returns the cleaning area
     *
     * @param centre         the centre of the area
     * @param direction      the direction of area
     * @param distance       the echo siatance (m)
     * @param receptiveAngle the sensor receptive angle
     */
    private static Parser createCleaningArea(Point2D centre, Complex direction, double distance, Complex receptiveAngle) {
        return and(
                circle(centre, distance),
                angle(centre, direction, receptiveAngle)
        ).createParser();
    }

    /**
     * Creates the marker locator
     *
     * @param locationDecay       the decay marker time (ms)
     * @param cleanDecay          the clean decay time (ms)
     * @param correlationInterval the correlation interval (ms)
     * @param minNumberEvents     the minimum number of unknown qr code events to update the marker map
     * @param markerSize          the marker size (m)
     */
    public MarkerLocator(double locationDecay, double cleanDecay, long correlationInterval, int minNumberEvents,
                         double markerSize) {
        this(locationDecay, cleanDecay, correlationInterval, minNumberEvents,
                markerSize, new AtomicReference<>(new MarkerLocatorStatus(0, null)));
    }

    /**
     * Creates the marker locator
     *
     * @param locationDecay       the decay marker time (ms)
     * @param cleanDecay          the clean decay time (ms)
     * @param correlationInterval the correlation interval (ms)
     * @param minNumberEvents     the minimum number of unknown qr code events to update the marker map
     * @param markerSize          the marker size (m)
     * @param status              the marker locator status
     */
    public MarkerLocator(double locationDecay, double cleanDecay, long correlationInterval, int minNumberEvents,
                         double markerSize, AtomicReference<MarkerLocatorStatus> status) {
        this.locationDecay = locationDecay;
        this.cleanDecay = cleanDecay;
        this.correlationInterval = correlationInterval;
        this.minNumberEvents = minNumberEvents;
        this.markerSize = markerSize;
        this.status = requireNonNull(status);
    }

    /**
     * Returns the filtered map area
     *
     * @param map            the map of marker
     * @param centre         the centre of cleaning area
     * @param direction      the direction of cleaning area
     * @param receptiveAngle the receptive sensor angle
     * @param time           the current time (ms)
     */
    private Map<String, LabelMarker> filterCleaningArea(Map<String, LabelMarker> map,
                                                        Point2D centre, Complex direction, double distance,
                                                        Complex receptiveAngle, long time) {
        List<LabelMarker> markers = map.values().stream().toList();
        Parser parser = createCleaningArea(centre, direction, distance, receptiveAngle);
        return markers.stream()
                .map(marker -> {
                    if (parser.test(marker.location())) {
                        // clean marker
                        double alpha = min((time - marker.cleanTime()) / cleanDecay, 1);
                        // dt -> 0 => alpha -> 0, echoWeight -> echoWeight
                        // dt -> decay => alpha -> 1, echoWeight -> -1
                        // weight = (-1-echoWeight)*alpha + echoWeight;
                        double weight = marker.weight();
                        weight = -(1 + weight) * alpha + weight;
                        return marker.setCleanTime(time).setWeight(weight);
                    }
                    return marker;
                })
                .filter(marker -> marker.weight() > 0)
                .collect(Collectors.toMap(
                        LabelMarker::label,
                        x -> x
                ));
    }

    /**
     * Returns the updated label marker map by camera event and proxy message
     *
     * @param map         the current map
     * @param cameraEvent the camera event
     * @param robotSpec   the robot specification
     */
    public Map<String, LabelMarker> update(Map<String, LabelMarker> map, CorrelatedCameraEvent cameraEvent, RobotSpec robotSpec) {
        requireNonNull(map);
        requireNonNull(cameraEvent);
        long cameraTime = cameraEvent.simulationTime();
        long proxyTime = cameraEvent.proxyTime();
        long elaps = cameraTime - proxyTime;
        if (elaps >= 0 && elaps <= correlationInterval) {
            // Correlated messages
            Point2D cameraLocation = cameraEvent.cameraLocation();
            double distance = cameraEvent.markerDistance();
            double maxDistance = robotSpec.maxRadarDistance();
            Complex receptiveAngle = robotSpec.receptiveAngle();
            Complex halfViewAngle = robotSpec.cameraViewAngle().divAngle(2);
            Complex cameraAzimuth = cameraEvent.cameraAzimuth();
            double clearDistance = (distance == 0 ? maxDistance : distance) + markerSize / 2;

            if (distance == 0) {
                // echo not present
                // Clear area
                status.updateAndGet(s -> s.markEvent(cameraEvent));
                return filterCleaningArea(map, cameraLocation, cameraAzimuth,
                        clearDistance, receptiveAngle, cameraTime);
            }
            Complex clearAngle = Complex.fromRad(min(halfViewAngle.toRad(), receptiveAngle.toRad()));
            CorrelatedCameraEvent prevEvent = status.get().prevEvent();
            if (!RobotSpec.UNKNOWN_QR_CODE.equals(cameraEvent.qrCode())
                    // qr code recognized
                    && cameraEvent.camerEvent().direction().isCloseTo(Complex.DEG0, robotSpec.receptiveAngle())
                // direction correlated with the proxy receptive angle
            ) {
                // Marker recognized
                Complex markerDirection = cameraEvent.markerAzimuth();
                Point2D markerLocation = markerDirection.at(cameraLocation, distance + markerSize / 2);
                LabelMarker marker = map.get(cameraEvent.qrCode());
                Map<String, LabelMarker> map1 = filterCleaningArea(map, cameraLocation, cameraAzimuth,
                        clearDistance, clearAngle, cameraTime);
                LabelMarker newMarker;
                if (marker != null) {
                    // existing label
                    // Time interval between previous proxy markerTime
                    long dt = cameraTime - marker.markerTime();

                    double gamma = Math.expm1(-(double) dt / locationDecay) + 1;
                    double notGamma = 1 - gamma;
                    double x = marker.location().getX() * gamma + markerLocation.getX() * notGamma;
                    double y = marker.location().getY() * gamma + markerLocation.getY() * notGamma;
                    newMarker = marker.setLocation(new Point2D.Double(x, y))
                            .setMarkerTime(cameraTime)
                            .setWeight(1);
                } else {
                    // new valid label
                    newMarker = new LabelMarker(cameraEvent.qrCode(), markerLocation, 1, cameraTime, cameraTime);
                }
                Map<String, LabelMarker> newMap = new HashMap<>(map1);
                newMap.put(newMarker.label(), newMarker);
                status.updateAndGet(s -> s.markEvent(cameraEvent));
                return newMap;
            } else if (prevEvent != null
                    && (cameraTime <= prevEvent.simulationTime()
                    || !cameraEvent.cameraAzimuth().isCloseTo(prevEvent.cameraAzimuth(), EPSILON_1DEG))) {
                // Event not changed or
                status.updateAndGet(s -> s.markEvent(cameraEvent));
                return map;
            } else if (status.updateAndGet(MarkerLocatorStatus::incUnknownCounter).unknownEventCount() >= minNumberEvents) {
                // Marker not recognised and camera event counter reached the threshold
                // Clear area
                status.updateAndGet(s -> s.markEvent(cameraEvent));
                return filterCleaningArea(map, cameraLocation, cameraAzimuth,
                        clearDistance, clearAngle.sub(CLEAR_REDUCTION_ANGLE), cameraTime);
            } else {
                // camera event ignored
                status.updateAndGet(s -> s.prevEvent(cameraEvent));
                return map;
            }
        }
        return map;
    }

    public record MarkerLocatorStatus(int unknownEventCount, CorrelatedCameraEvent prevEvent) {
        public MarkerLocatorStatus incUnknownCounter() {
            return new MarkerLocatorStatus(unknownEventCount + 1, prevEvent);
        }

        public MarkerLocatorStatus markEvent(CorrelatedCameraEvent cameraEvent) {
            return unknownEventCount(0).prevEvent(cameraEvent);
        }

        public MarkerLocatorStatus prevEvent(CorrelatedCameraEvent prevEvent) {
            return !Objects.equals(this.prevEvent, prevEvent)
                    ? new MarkerLocatorStatus(unknownEventCount, prevEvent)
                    : this;
        }

        public MarkerLocatorStatus unknownEventCount(int unknownEventCount) {
            return this.unknownEventCount != unknownEventCount
                    ? new MarkerLocatorStatus(unknownEventCount, prevEvent)
                    : this;
        }
    }
}
