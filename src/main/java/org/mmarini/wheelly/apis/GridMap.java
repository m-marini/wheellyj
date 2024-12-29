/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
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

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

import static java.lang.Math.PI;
import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;

/**
 * The grid map keeps the status of the squared area round a center point.
 *
 * @param topology   the grid topology
 * @param cells     the cells
 * @param center    the map center in world coordinate
 * @param direction the map direction in world compass
 */
public record GridMap(GridTopology topology, MapCell[] cells, Point2D center, Complex direction) {
    /**
     * Returns the grid map for the given radar map, center and direction
     *
     * @param map       the map
     * @param center    the center of grid map (m)
     * @param direction the direction of grid
     * @param mapSize   the number of horizontal and vertical cells of map
     */
    public static GridMap create(RadarMap map, Point2D center, Complex direction, int mapSize) {
        GridTopology radarTopology = map.topology();
        Point2D mapCenter = radarTopology.snap(center);
        GridTopology mapTopology = new GridTopology(new Point2D.Float(), mapSize, mapSize, radarTopology.gridSize());
        int n = mapSize * mapSize;
        MapCell[] cells = new MapCell[n];
        double dirRad = direction.toRad() + 2 * PI;
        int dirIdx = ((int) round(dirRad / PI * 2)) % 4;
        Complex mapDirection = Complex.fromRad(dirIdx * PI / 2);
        AffineTransform trans = AffineTransform.getTranslateInstance(mapCenter.getX(), mapCenter.getY());
        trans.rotate(-mapDirection.toRad());
        for (int i = 0; i < n; i++) {
            Point2D mapLocation = mapTopology.location(i);
            Point2D radarLocation = trans.transform(mapLocation, null);
            int idx = radarTopology.indexOf(radarLocation);
            cells[i] = idx >= 0
                    ? map.cell(idx).setLocation(mapLocation)
                    : MapCell.unknown(mapLocation);
        }
        return new GridMap(mapTopology, cells, mapCenter, mapDirection);
    }

    /**
     * Creates the grid map
     *
     * @param topology   the grid topology
     * @param cells     the cells
     * @param center    the map center in world coordinate (m)
     * @param direction the map direction in world compass
     */
    public GridMap(GridTopology topology, MapCell[] cells, Point2D center, Complex direction) {
        this.topology = requireNonNull(topology);
        this.cells = requireNonNull(cells);
        this.center = requireNonNull(center);
        this.direction = requireNonNull(direction);
    }

    /**
     * Returns the cell at location or null if not found
     *
     * @param x abscissa in the map (m)
     * @param y ordinate in the map (m)
     */
    public MapCell cell(double x, double y) {
        int idx = topology.indexOf(x, y);
        return idx >= 0 && idx < cells.length ? cells[idx] : null;
    }

    /**
     * Returns the GridMap with the center
     *
     * @param center the center (m)
     */
    public GridMap setCenter(Point2D center) {
        return center.equals(this.center)
                ? this
                : new GridMap(topology, cells, center, direction);
    }

    /**
     * Returns the GridMap with the direction
     *
     * @param direction the direction
     */
    public GridMap setDirection(Complex direction) {
        return direction.equals(this.direction)
                ? this
                : new GridMap(topology, cells, center, direction);
    }
}
