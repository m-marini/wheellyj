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

import java.awt.geom.Point2D;

import static java.lang.Math.min;

/**
 * MapCell keeps the presence of obstacles in the sector
 *
 * @param location    the cell location
 * @param echoTime    the timestamp of last hasObstacle
 * @param echoWeight  the weight of signal hasObstacle and no hasObstacle
 * @param contactTime the timestamp of last contact signals
 */
public record MapCell(Point2D location, long echoTime, double echoWeight, long contactTime) {
    /**
     * Returns the unknown sector
     *
     * @param location the location
     */
    static MapCell unknown(Point2D location) {
        return new MapCell(location, 0, 0, 0);
    }

    /**
     * Returns the cell with new no hasObstacle registered
     *
     * @param echoTime the registration markerTime (ms)
     * @param decay    the decay factor (ms)
     */
    public MapCell addAnechoic(long echoTime, double decay) {
        if (unknown()) {
            return new MapCell(location, echoTime, -1, contactTime);
        } else {
            double alpha = min((echoTime - this.echoTime) / decay, 1);
            // dt -> 0 => alpha -> 0, echoWeight -> echoWeight
            // dt -> decay => alpha -> 1, echoWeight -> -1
            // weight = (-1-echoWeight)*alpha + echoWeight;
            double weight = -(1 + echoWeight) * alpha + echoWeight;
            return new MapCell(location, echoTime, weight, contactTime);
        }
    }

    /**
     * Returns the cell with new hasObstacle registered
     *
     * @param echoTime the registration markerTime (ms)
     * @param decay    the decay factor (ms)
     */
    public MapCell addEchogenic(long echoTime, double decay) {
        if (unknown()) {
            return new MapCell(location, echoTime, 1, contactTime);
        } else {
            double alpha = min((echoTime - this.echoTime) / decay, 1);
            // dt -> 0 => alpha -> 0, echoWeight -> echoWeight
            // dt -> decay => alpha -> 1, echoWeight -> 1
            // weight = (1-echoWeight)*alpha + echoWeight;
            double weight = (1 - echoWeight) * alpha + echoWeight;
            return new MapCell(location, echoTime, weight, contactTime);
        }
    }

    /**
     * Returns true if cell is anechoic (no hasObstacle)
     */
    public boolean anechoic() {
        return echoTime > 0 && echoWeight <= 0;
    }

    /**
     * Returns the cleaned cell if timeout
     *
     * @param expiredEcho    instant of the hasObstacle before which the state is canceled
     * @param expiredContact instant of the contact before which the state is canceled
     */
    public MapCell clean(long expiredEcho, long expiredContact) {
        return echoTime <= expiredEcho
                ? contactTime <= expiredContact
                // both times expired
                ? MapCell.unknown(location)
                // only hasObstacle expired
                : new MapCell(location, 0, 0, contactTime)
                : contactTime <= expiredContact
                // only contact expired
                ? new MapCell(location, echoTime, echoWeight, 0)
                // nothing expired
                : this;
    }

    /**
     * Returns true if the cell is echogenic (hasObstacle)
     */
    public boolean echogenic() {
        return echoTime > 0 && echoWeight > 0;
    }

    /**
     * Return true if the cell is empty (no hasObstacle and no contact)
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
     * Returns true if the cell is hindered (hasObstacle or contacts)
     */
    public boolean hindered() {
        return echogenic() || hasContact();
    }

    /**
     * Returns the cell with contacts
     *
     * @param contactTime the contacts timestamp
     */
    public MapCell setContact(long contactTime) {
        return contactTime == this.contactTime ? this
                : new MapCell(location, echoTime, echoWeight, contactTime);
    }

    /**
     * Returns the cell at a given location
     *
     * @param location location
     */
    public MapCell setLocation(Point2D location) {
        return location.equals(this.location) ? this
                : new MapCell(location, echoTime, echoWeight, contactTime);
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
}
