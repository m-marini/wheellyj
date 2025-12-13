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
     * Returns the marker locator
     *
     * @param locationDecay       the decay marker time (ms)
     * @param cleanDecay          the clean decay time (ms)
     * @param correlationInterval the correlation interval (ms)
     * @param markerSize          the marker size (m)
     * @param minNumberEvents     the minimum number of unknown qr code events to update the marker map
     */
    public static MarkerLocator create(double locationDecay, double cleanDecay, long correlationInterval, double markerSize, int minNumberEvents) {
        return new MarkerLocator(locationDecay, cleanDecay, correlationInterval, minNumberEvents, markerSize,
                new AtomicReference<>(new MarkerLocatorStatus(0, null)));
    }

    /**
     * Returns the marker locator from definition
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
        return create(decay, cleanDecay, correlationInterval, markerSize, minNumberEvents);
    }

    /**
     * Returns the cleaning area.
     * It is a circular sector of the given angle directed to the given direction of given radius
     *
     * @param centre    the centre of the area
     * @param direction the direction of area
     * @param radius    the signal distance (m)
     * @param fov       the field of view
     */
    private static Parser createCleaningArea(Point2D centre, Complex direction, double radius, Complex fov) {
        return and(
                circle(centre, radius),
                angle(centre, direction, fov.divAngle(2))
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
     * @param map       the map of marker
     * @param centre    the centre of cleaning area
     * @param direction the direction of cleaning area
     * @param fov       the receptive sensor angle
     * @param time      the current time (ms)
     */
    private Map<String, LabelMarker> filterCleaningArea(Map<String, LabelMarker> map,
                                                        Point2D centre, Complex direction, double distance,
                                                        Complex fov, long time) {
        List<LabelMarker> markers = map.values().stream().toList();
        Parser parser = createCleaningArea(centre, direction, distance, fov);
        return markers.stream()
                .map(marker -> {
                    if (parser.test(marker.location())) {
                        // clean marker
                        long dt = time - marker.cleanTime();
                        double alpha = min(dt / cleanDecay, 1);
                        // dt -> 0 => alpha -> 0, weight -> weight
                        // dt -> decay => alpha -> 1, weight -> -1
                        // weight -> (-1-weight) * alpha + weight;
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
        long cameraTime = cameraEvent.cameraTime();
        long lidarTime = cameraEvent.lidarTime();
        long elaps = cameraTime - lidarTime;
        if (elaps >= 0 && elaps <= correlationInterval) {
            // Correlated messages
            double distance = cameraEvent.markerDistance();
            double maxDistance = robotSpec.maxRadarDistance();
            Complex lidarFov = robotSpec.lidarFOV();
            Complex cameraFov = robotSpec.cameraFOV();
            Complex cameraAzimuth = cameraEvent.lidarYaw();
            double clearDistance = (distance == 0 ? maxDistance : distance);
            Point2D lidarLocation = robotSpec.frontLidarLocation(cameraEvent.robotLocation(), cameraEvent.robotDirection(), cameraEvent.headDirection());

            if (distance == 0) {
                // obstacle is not present
                // Clear area
                status.updateAndGet(s -> s.markEvent(cameraEvent));
                return filterCleaningArea(map, lidarLocation, cameraAzimuth,
                        clearDistance, lidarFov, lidarTime);
            }
            CorrelatedCameraEvent prevEvent = status.get().prevEvent();
            Complex cleanFov = Complex.fromRad(min(lidarFov.toRad(), cameraFov.toRad()));
            if (!RobotSpec.UNKNOWN_QR_CODE.equals(cameraEvent.qrCode())
                    // qr code recognized
                    && cameraEvent.camerEvent().direction().isCloseTo(Complex.DEG0, robotSpec.lidarFOV())
                // camera label direction correlated with the lidar receptive angle
            ) {
                // Marker recognized
                Complex markerDirection = cameraEvent.markerYaw();
                Point2D markerLocation = markerDirection.at(lidarLocation, distance);
                LabelMarker mapMarker = map.get(cameraEvent.qrCode());
                Map<String, LabelMarker> map1 = filterCleaningArea(map, lidarLocation, cameraAzimuth,
                        clearDistance, cleanFov, lidarTime);
                LabelMarker newMarker;
                if (mapMarker != null) {
                    // existing label
                    // Time interval between previous proxy markerTime
                    long dt = lidarTime - mapMarker.markerTime();

                    double gamma = Math.expm1(-(double) dt / locationDecay) + 1;
                    double notGamma = 1 - gamma;
                    double x = mapMarker.location().getX() * gamma + markerLocation.getX() * notGamma;
                    double y = mapMarker.location().getY() * gamma + markerLocation.getY() * notGamma;
                    newMarker = mapMarker.setLocation(new Point2D.Double(x, y))
                            .setMarkerTime(lidarTime)
                            .setWeight(1);
                } else {
                    // new valid label
                    newMarker = new LabelMarker(cameraEvent.qrCode(), markerLocation, 1, lidarTime, lidarTime);
                }
                Map<String, LabelMarker> newMap = new HashMap<>(map1);
                newMap.put(newMarker.label(), newMarker);
                status.updateAndGet(s -> s.markEvent(cameraEvent));
                return newMap;
            } else if (prevEvent != null
                    && (cameraTime <= prevEvent.cameraTime()
                    || !cameraEvent.lidarYaw().isCloseTo(prevEvent.lidarYaw(), EPSILON_1DEG))) {
                // Event not changed or
                status.updateAndGet(s -> s.markEvent(cameraEvent));
                return map;
            } else if (status.updateAndGet(MarkerLocatorStatus::incUnknownCounter).unknownEventCount() >= minNumberEvents) {
                // Marker not recognised and camera event counter reached the threshold
                // Clear area
                status.updateAndGet(s -> s.markEvent(cameraEvent));
                return filterCleaningArea(map, lidarLocation, cameraAzimuth,
                        clearDistance, cleanFov.sub(CLEAR_REDUCTION_ANGLE),
                        lidarTime);
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
