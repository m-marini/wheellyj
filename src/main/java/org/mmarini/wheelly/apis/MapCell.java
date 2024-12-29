/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.apis;

import org.mmarini.Tuple2;

import java.awt.geom.Point2D;

import static java.lang.Math.min;

/**
 * MapCell keeps the presence of obstacles in the sector
 *
 * @param location      the cell location
 * @param echoTime      the timestamp of last echo
 * @param echoWeight    the weight of signal echo and no echo
 * @param contactTime   the timestamp of last contact signals
 * @param labeledTime   the timestamp of last label signal
 * @param labeledWeight the weight of the labeled and unlabeled signals
 */
public record MapCell(Point2D location, long echoTime, double echoWeight, double contactTime, long labeledTime,
                      double labeledWeight) {
    /**
     * Returns the unknown sector
     *
     * @param location the location
     */
    static MapCell unknown(Point2D location) {
        return new MapCell(location, 0, 0, 0, 0, 0);
    }

    /**
     * Returns the cell with new no echo registered
     *
     * @param echoTime the registration time (ms)
     * @param decay    the decay factor (ms)
     */
    public MapCell addAnechoic(long echoTime, double decay) {
        if (unknown()) {
            return new MapCell(location, echoTime, -1, contactTime, 0, 0);
        } else {
            double alpha = min((echoTime - this.echoTime) / decay, 1);
            // dt -> 0 => alpha -> 0, echoWeight -> echoWeight
            // dt -> decay => alpha -> 1, echoWeight -> -1
            // weight = (-1-echoWeight)*alpha + echoWeight;
            double weight = -(1 + echoWeight) * alpha + echoWeight;
            return new MapCell(location, echoTime, weight, contactTime, labeledTime, labeledWeight);
        }
    }

    /**
     * Returns the cell with new echo registered
     *
     * @param echoTime the registration time (ms)
     * @param decay    the decay factor (ms)
     */
    public MapCell addEchogenic(long echoTime, double decay) {
        if (unknown()) {
            return new MapCell(location, echoTime, 1, contactTime, 0, 0);
        } else {
            double alpha = min((echoTime - this.echoTime) / decay, 1);
            // dt -> 0 => alpha -> 0, echoWeight -> echoWeight
            // dt -> decay => alpha -> 1, echoWeight -> 1
            // weight = (1-echoWeight)*alpha + echoWeight;
            double weight = (1 - echoWeight) * alpha + echoWeight;
            return new MapCell(location, echoTime, weight, contactTime, labeledTime, labeledWeight);
        }
    }

    /**
     * Returns the cell with new registered labeled
     *
     * @param decay the decay factor (ms)
     */
    public MapCell addLabeled(double decay) {
        if (unknown()) {
            return this;
        } else {
            double alpha = min((echoTime - labeledTime) / decay, 1);
            // dt -> 0 => alpha -> 0, echoWeight -> echoWeight
            // dt -> decay => alpha -> 1, echoWeight -> -1
            // weight = (-1-echoWeight)*alpha + echoWeight;
            double weight = (1 - labeledWeight) * alpha + labeledWeight;
            return new MapCell(location, echoTime, echoWeight, contactTime, echoTime, weight);
        }
    }

    /**
     * Returns the cell with new registered unlabeled
     *
     * @param decay the decay factor (ms)
     */
    public MapCell addUnlabeled(double decay) {
        if (unknown()) {
            return this;
        } else {
            double alpha = min((echoTime - labeledTime) / decay, 1);
            // dt -> 0 => alpha -> 0, echoWeight -> echoWeight
            // dt -> decay => alpha -> 1, echoWeight -> -1
            // weight = (-1-echoWeight)*alpha + echoWeight;
            double weight = -(1 + labeledWeight) * alpha + labeledWeight;
            return new MapCell(location, echoTime, echoWeight, contactTime, echoTime, weight);
        }
    }

    /**
     * Returns true if cell is anechoic (no echo)
     */
    public boolean anechoic() {
        return echoTime > 0 && echoWeight <= 0;
    }

    /**
     * Returns the cleaned cell if timeout
     *
     * @param expiredEcho    instant of the echo before which the state is canceled
     * @param expiredContact instant of the contact before which the state is canceled
     */
    public MapCell clean(long expiredEcho, long expiredContact) {
        return echoTime <= expiredEcho
                ? contactTime <= expiredContact
                // both times expired
                ? MapCell.unknown(location)
                // only echo expired
                : new MapCell(location, 0, 0, contactTime, 0, 0)
                : contactTime <= expiredContact
                // only contact expired
                ? new MapCell(location, echoTime, echoWeight, 0, labeledTime, labeledWeight)
                // nothing expired
                : this;
    }

    /**
     * Returns true if the cell is echogenic (echo)
     */
    public boolean echogenic() {
        return echoTime > 0 && echoWeight > 0;
    }

    /**
     * Return true if the cell is empty (no echo and no contact)
     */
    public boolean empty() {
        return !unknown() && !hasContact() && anechoic();
    }

    /**
     * Returns true if the cell recorded contacts
     */
    public boolean hasContact() {
        return contactTime > 0;
    }

    /**
     * Returns true if the cell is hindered (echo or contacts)
     */
    public boolean hindered() {
        return echogenic() || hasContact();
    }

    /**
     * Returns true if the cell is echogenic (echo)
     */
    public boolean labeled() {
        return echoTime > 0 && echoWeight > 0 && labeledWeight > 0;
    }

    /**
     * Returns the cell with contacts
     *
     * @param contactTime the contacts timestamp
     */
    public MapCell setContact(long contactTime) {
        return contactTime == this.contactTime ? this
                : new MapCell(location, echoTime, echoWeight, contactTime, labeledTime, labeledWeight);
    }

    /**
     * Returns the cell at a given location
     *
     * @param location location
     */
    public MapCell setLocation(Point2D location) {
        return location.equals(this.location) ? this
                : new MapCell(location, echoTime, echoWeight, contactTime, labeledTime, labeledWeight);
    }

    /**
     * Returns the cell in unknown state
     */
    public MapCell setUnknown() {
        return unknown() ? this : MapCell.unknown(location);
    }

    /**
     * Returns true if the cell is unknown
     */
    public boolean unknown() {
        return echoTime <= 0 && contactTime <= 0;
    }

    /**
     * Returns the updated cell
     *
     * @param signal         the signal
     * @param maxDistance    maximum distance of cell (m)
     * @param gridSize       receptive distance (m)
     * @param receptiveAngle receptive angle
     * @param decay          the decay factor (ms)
     */
    public MapCell update(RadarMap.SensorSignal signal, double maxDistance, double gridSize, Complex receptiveAngle, double decay) {
        long t0 = signal.timestamp();
        Point2D q = signal.sensorLocation();
        double distance = signal.distance();
        Tuple2<Point2D, Point2D> interval = Geometry.squareArcInterval(location, gridSize, q,
                signal.sensorDirection(),
                receptiveAngle);
        if (interval == null) {
            return this;
        }
        double near = interval._1.distance(q);
        double far = interval._2.distance(q);
        if (near > 0 && near <= maxDistance) {
            // cell is in receptive zone
            if (signal.isEcho() && distance >= near && distance <= far) {
                // signal echo inside the cell
                return addEchogenic(t0, decay);
            }
            if (!signal.isEcho() || distance > far) {
                // signal is not echo or echo is far away the cell
                return addAnechoic(t0, decay);
            }
        }
        return this;
    }

    /**
     * Returns the updated cell
     *
     * @param signal         the signal
     * @param maxDistance    maximum distance of cell (m)
     * @param gridSize       receptive distance (m)
     * @param receptiveAngle receptive angle
     * @param label          the label
     * @param decay          the decay factor (ms)
     */
    public MapCell updateLabel(RadarMap.SensorSignal signal, double maxDistance, double gridSize, Complex receptiveAngle, String label, double decay) {
        long t0 = signal.timestamp();
        Point2D q = signal.sensorLocation();
        double distance = signal.distance();
        Tuple2<Point2D, Point2D> interval = Geometry.squareArcInterval(location, gridSize, q,
                signal.sensorDirection(),
                receptiveAngle);
        if (interval == null) {
            return this;
        }
        double near = interval._1.distance(q);
        double far = interval._2.distance(q);
        if (near > 0 && near <= maxDistance) {
            // cell is in receptive zone
            if (signal.isEcho() && distance >= near && distance <= far) {
                // signal echo inside the cell
                return label == null ?
                        addEchogenic(t0, decay)
                        : "?".equals(label)
                        ? addUnlabeled(decay)
                        : addLabeled(decay);
            }
            if ((!signal.isEcho() || distance > far) && label == null) {
                // signal is not echo or echo is far away the cell
                return addAnechoic(t0, decay);
            }
        }
        return this;
    }
}
