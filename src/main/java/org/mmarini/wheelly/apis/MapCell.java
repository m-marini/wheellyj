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

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * MapCell keeps the presence of obstacles in the sector
 *
 * @param location       the cell location
 * @param echoTime       the timestamp of last echo
 * @param echoCounter    the difference between the number of signal echo and no echo
 * @param contactTime    the timestamp of last contact signals
 * @param labeledCounter the difference between the number of labeled and unlabeled signals
 */
public record MapCell(Point2D location, long echoTime, int echoCounter, long contactTime, int labeledCounter) {
    /**
     * Returns the unknown sector
     *
     * @param location the location
     */
    static MapCell unknown(Point2D location) {
        return new MapCell(location, 0, 0, 0, 0);
    }

    /**
     * Returns the cell with new no echo registered
     *
     * @param echoTime the registration time
     */
    public MapCell addAnechoic(long echoTime) {
        return unknown() ? new MapCell(location, echoTime, 0, contactTime, 0)
                : new MapCell(location, echoTime, echoCounter - 1, contactTime, labeledCounter);
    }

    /**
     * Returns the cell with new echo registered
     *
     * @param echoTime the registration time
     */
    public MapCell addEchogenic(long echoTime) {
        return new MapCell(location, echoTime,
                unknown() ? 1 : echoCounter + 1, contactTime, labeledCounter);
    }

    /**
     * Returns the cell with new registered labeled
     */
    public MapCell addLabeled() {
        return new MapCell(location, echoTime, echoCounter, contactTime, labeledCounter + 1);
    }

    /**
     * Returns the cell with new registered unlabeled
     */
    public MapCell addUnlabeled() {
        return new MapCell(location, echoTime, echoCounter, contactTime, labeledCounter - 1);
    }

    /**
     * Returns true if cell is anechoic (no echo)
     */
    public boolean anechoic() {
        return echoTime > 0 && echoCounter <= 0;
    }

    /**
     * Returns the cleaned cell
     *
     * @param echoLimit    echo limit time
     * @param contactLimit contact limit time
     */
    public MapCell clean(long echoLimit, long contactLimit) {
        return echoTime < echoLimit
                ? contactTime < contactLimit
                // both times expired
                ? new MapCell(location, echoTime, min(max(echoCounter, 0), 1), 0, labeledCounter)
                // only echo expired
                : new MapCell(location, echoTime, min(max(echoCounter, 0), 1), contactTime, labeledCounter)
                : contactTime < contactLimit
                // only contact expired
                ? new MapCell(location, echoTime, echoCounter, 0, labeledCounter)
                // nothing expired
                : this;
    }

    /**
     * Returns true if the cell is echogenic (echo)
     */
    public boolean echogenic() {
        return echoTime > 0 && echoCounter > 0;
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
        return echoTime > 0 && echoCounter > 0 && labeledCounter > 0;
    }

    /**
     * Returns the cell with contacts
     *
     * @param contactTime the contacts timestamp
     */
    public MapCell setContact(long contactTime) {
        return new MapCell(location, echoTime, echoCounter, contactTime, labeledCounter);
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
     */
    public MapCell update(RadarMap.SensorSignal signal, double maxDistance, double gridSize, Complex receptiveAngle) {
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
        /*
        return near == 0 || near > maxDistance || (distance < near && signal.isEcho())
                ? this
                : distance <= far && signal.isEcho()
                ? addEchogenic(t0)
                : addAnechoic(t0);

         */
        if (near > 0 && near <= maxDistance) {
            // cell is in receptive zone
            if (signal.isEcho() && distance >= near && distance <= far) {
                // signal echo inside the cell
                return addEchogenic(t0);
            }
            if (!signal.isEcho() || distance > far) {
                // signal is not echo or echo is far away the cell
                return addAnechoic(t0);
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
     */
    public MapCell updateLabel(RadarMap.SensorSignal signal, double maxDistance, double gridSize, Complex receptiveAngle, String label) {
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
        /*
        return near == 0 || near > maxDistance || (distance < near && signal.isEcho())
                ? this
                : distance <= far && signal.isEcho()
                ? addEchogenic(t0)
                : addAnechoic(t0);

         */
        if (near > 0 && near <= maxDistance) {
            // cell is in receptive zone
            if (signal.isEcho() && distance >= near && distance <= far) {
                // signal echo inside the cell
                return label == null ?
                        addEchogenic(t0)
                        : "?".equals(label)
                        ? addUnlabeled()
                        : addLabeled();
            }
            if ((!signal.isEcho() || distance > far) && label == null) {
                // signal is not echo or echo is far away the cell
                return addAnechoic(t0);
            }
        }
        return this;
    }
}
