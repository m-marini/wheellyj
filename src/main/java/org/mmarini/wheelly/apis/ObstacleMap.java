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
import org.nd4j.linalg.ops.transforms.Transforms;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;

/**
 * Ths obstacle map defines the location of obstacle in a grid space
 *
 * @param indices     the indices of obstacle
 * @param gridSize    the grid size (m)
 * @param coordinates the coordinates array of n x 2 float
 */
public record ObstacleMap(List<Point> indices, double gridSize, INDArray coordinates) {

    /**
     * Returns the map of obstacles
     *
     * @param indices  the obstacle location indices
     * @param gridSize the gridSize
     */
    public static ObstacleMap create(Collection<Point> indices, double gridSize) {
        // Creates the unique indices
        List<Point> uniqueIndices = indices.stream().distinct().collect(Collectors.toList());
        // Creates the coordinates
        double[] ary = uniqueIndices.stream()
                .map(index -> toPoint(index, gridSize))
                .flatMapToDouble(p -> DoubleStream.of(p.getX(), p.getY()))
                .toArray();
        // Creates the map
        return new ObstacleMap(uniqueIndices,
                gridSize,
                Nd4j.createFromArray(ary).castTo(DataType.FLOAT).reshape(uniqueIndices.size(), 2));
    }

    /**
     * Returns the indices of the point
     *
     * @param x        the abscissa of the point
     * @param y        the ordinate of the point
     * @param gridSize the grid size
     */
    public static Point toIndex(double x, double y, double gridSize) {
        int i = (int) round(x / gridSize);
        int j = (int) round(y / gridSize);
        return new Point(i, j);
    }

    /**
     * Returns the point for the given index
     *
     * @param point    the index
     * @param gridSize the grid size
     */
    public static Point2D toPoint(Point point, double gridSize) {
        return new Point2D.Double(point.x * gridSize, point.y * gridSize);
    }

    /**
     * Create the obstacle map
     *
     * @param indices     the indices of obstacle
     * @param gridSize    the grid size
     * @param coordinates the coordinates array of n x 2 float
     */
    public ObstacleMap(List<Point> indices, double gridSize, INDArray coordinates) {
        this.indices = requireNonNull(indices);
        this.coordinates = requireNonNull(coordinates);
        this.gridSize = gridSize;
        if (coordinates.shape().length != 2) {
            throw new IllegalArgumentException("Wrong rank");
        }
        if (coordinates.shape()[1] != 2) {
            throw new IllegalArgumentException("Wrong shape");
        }
        if (coordinates.dataType() != DataType.FLOAT) {
            throw new IllegalArgumentException("Wrong data type");
        }
    }

    /**
     * Returns true if map contains obstacle at location
     *
     * @param x x coordinate
     * @param y coordinate
     */
    public boolean contains(double x, double y) {
        return indices.contains(toIndex(x, y, gridSize));
    }

    /**
     * Returns the number of obstacles
     */
    public int getSize() {
        return (int) coordinates.shape()[0];
    }

    /**
     * Returns the index of nearest obstacle from the given point to the given direction with given direction range
     *
     * @param x              the x point coordinate
     * @param y              the y point coordinate
     * @param direction      the direction
     * @param directionRange the direction range
     */
    public Point2D nearest(double x, double y, Complex direction, Complex directionRange) {
        int n = getSize();
        if (n == 0) {
            return null;
        }
        INDArray point = Nd4j.createFromArray(x, y).reshape(1, 2);

        // Computes the vectors of obstacles relative the given position (x,y)
        INDArray vect = coordinates.sub(point);

        // Computes the distances of obstacles relative the given position
        INDArray distances = vect.norm2(1).reshape(vect.shape()[0], 1);

        // Computes the direction vectors obstacle relative to position
        INDArray directions = vect.div(distances);

        // Computes the direction vector of the given direction
        INDArray dirVector = Nd4j.createFromArray((float) direction.x(), (float) direction.y()).reshape(1, 2);

        // Computes the scalar product of direction vectors by direction vector (versus of points through the given direction)
        INDArray cosDir = directions.mmul(dirVector.transpose());

        // Computes the right orthogonal direction vector of the given direction
        INDArray orthoVector = Nd4j.createFromArray((float) direction.y(), -(float) direction.x()).reshape(1, 2);

        // Computes the vector product of direction vectors by direction vector (scalar product by orthogonal direction vector)
        // (cos of point direction to respect the give direction)
        INDArray sinDir = directions.mmul(orthoVector.transpose());
        Transforms.abs(sinDir, false);

        // Calculates the limit cosine of direction range
        float sinThreshold = (float) directionRange.sin();

        // Finds the eligible obstacle points
        INDArray validCos = cosDir.gte(0);
        INDArray validSin = sinDir.lte(sinThreshold);
        INDArray valid = Transforms.and(validSin, validCos);

        int index = -1;
        float minDist = Float.MAX_VALUE;
        // Find for nearest valid obstacles
        for (int i = 0; i < n; i++) {
            int val = valid.getInt(i, 0);
            float dist = distances.getFloat(i, 0);
            if (dist == 0) {
                // the obstacle coincides with the given position
                return toPoint(indices.get(i), gridSize);
            } else if (val == 1 && dist < minDist) {
                // the obstacle is valid and is near the previous found
                index = i;
                minDist = dist;
            }
        }
        return index >= 0 ? toPoint(indices.get(index), gridSize) : null;
    }

    /**
     * Returns the points of the map
     */
    public List<Point2D> points() {
        return indices.stream()
                .map(index -> toPoint(index, gridSize))
                .toList();
    }
}
