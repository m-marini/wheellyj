/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.fsm;

import org.mmarini.wheelly.apis.Complex;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class PathBuilder {

    public static PathBuilder builder(Point2D location, double robotDeg) {
        return new PathBuilder(new ArrayList<>(), location, Complex.fromDeg(robotDeg));
    }

    private final List<Point2D> path;
    private Point2D location;
    private Complex direction;

    protected PathBuilder(List<Point2D> path, Point2D location, Complex direction) {
        this.path = path;
        this.location = location;
        this.direction = direction;
    }

    public PathBuilder add() {
        path.add(location);
        return this;
    }

    public List<Point2D> build() {
        return path;
    }

    public PathBuilder move(double distance) {
        location = direction.at(location, distance);
        return this;
    }

    public PathBuilder turn(Complex deltaDeg) {
        direction = direction.add(deltaDeg);
        return this;
    }

    public PathBuilder turn(double deltaDeg) {
        return turn(Complex.fromDeg(deltaDeg));
    }
}
