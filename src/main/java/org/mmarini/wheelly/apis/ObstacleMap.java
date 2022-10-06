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

import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.util.Objects.requireNonNull;

/**
 * Ths obstacle map defines the location of obstacle in a grid space
 */
public class ObstacleMap {
    public static ObstacleMap create(INDArray indArray, float gridSize) {
        return new ObstacleMap(indArray, new GridTopology(gridSize));
    }

    private final INDArray coordinates;
    private final GridTopology topology;

    /**
     * Create the obstacle map
     *
     * @param coordinates the coordinates array of n x 2 float
     * @param topology    the grid topology
     */
    public ObstacleMap(INDArray coordinates, GridTopology topology) {
        requireNonNull(coordinates);
        if (coordinates.shape().length != 2) {
            throw new IllegalArgumentException("Wrong rank");
        }
        if (coordinates.shape()[1] != 2) {
            throw new IllegalArgumentException("Wrong shape");
        }
        if (coordinates.dataType() != DataType.FLOAT) {
            throw new IllegalArgumentException("Wrong data type");
        }
        this.coordinates = coordinates;
        this.topology = requireNonNull(topology);
    }

    /**
     * Returns true if map contains obstacle at location
     *
     * @param x x coordinate
     * @param y coordinate
     */
    public boolean contains(float x, float y) {
        float[] location = topology.snap(x, y);
        INDArray point = Nd4j.createFromArray(location);
        int n = getSize();
        for (int i = 0; i < n; i++) {
            INDArray o = getCoordinates(i);
            if (o.equals(point)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the coordinates of obstacles
     */
    public INDArray getCoordinates() {
        return coordinates;
    }

    public INDArray getCoordinates(int i) {
        return getCoordinates().getRow(i);
    }

    public Point2D getPoint(int index) {
        return new Point2D.Float(coordinates.getFloat(index, 0), coordinates.getFloat(index, 1));
    }

    /**
     * Returns the points of the map
     */
    public List<Point2D> getPoints() {
        return IntStream.range(0, getSize()).mapToObj(i ->
                new Point2D.Float(coordinates.getFloat(i, 0), coordinates.getFloat(i, 1))
        ).collect(Collectors.toList());
    }

    /**
     * Returns the number of obstacles
     */
    public int getSize() {
        return (int) coordinates.shape()[0];
    }

    /**
     * Returns the grid topology
     */
    public GridTopology getTopology() {
        return topology;
    }

    public int indexOfNearest(float x, float y, float direction, float directionRange) {
        int n = getSize();
        INDArray point = Nd4j.createFromArray(x, y).reshape(1, 2);
        INDArray vect = coordinates.sub(point);
        INDArray n1 = vect.norm2(1).reshape(vect.shape()[0], 1);
        INDArray dirVersor = Nd4j.createFromArray((float) cos(direction), (float) sin(direction)).reshape(1, 2);
        INDArray cosDir = vect.mmul(dirVersor.transpose()).div(n1);
        float cosThreshold = (float) cos(directionRange);
        INDArray valid = cosDir.gte(cosThreshold);
        int index = -1;
        Float minDist = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            int val = valid.getInt(i, 0);
            float dist = n1.getFloat(i, 0);
            if (dist == 0) {
                return i;
            } else if (val == 1 && dist < minDist) {
                index = i;
                minDist = dist;
            }
        }
        return index;
    }
}