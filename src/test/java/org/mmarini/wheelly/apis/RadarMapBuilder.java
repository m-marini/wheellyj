/*
 * Copyright (c) 2025-2026 Marco Marini, marco.marini@mmarini.org
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

import org.mmarini.Tuple2;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.mmarini.Utils.mul;

public class RadarMapBuilder {
    public static final double GRID_SIZE = 0.2;
    public static final double EPSILON = 1e-5;
    public static final GridTopology TOPOLOGY = GridTopology.create(new Point2D.Double(), 51, 51, GRID_SIZE);
    public static final int DECAY = 1000;
    public static final String UNKNOWN_ID = ".";
    public static final String EMPTY_ID = "+";
    public static final String OBSTACLE_ID = "o";
    public static final String CONTACT_ID = "x";

    public static boolean isMarker(String id) {
        return !id.isEmpty() && Character.isUpperCase(id.charAt(0));
    }

    /**
     * Returns the list of location and labels from the given text map
     *
     * @param topology the grid topology
     * @param mapText  the map text
     */
    public static Stream<Tuple2<Point2D, String>> parseMap(GridTopology topology, String mapText) {
        List<Tuple2<Point, String>> result = parseMap(mapText);
        int maxX = result.stream().mapToInt(t -> t._1.x).max().orElse(0);
        int maxY = result.stream().mapToInt(t -> t._1.y).max().orElse(0);
        Point2D p0 = new Point2D.Double(
                -topology.gridSize() * maxX / 2,
                -topology.gridSize() * maxY / 2);
        return result.stream()
                .map(Tuple2.map1(p ->
                        org.mmarini.Utils.add(p0,
                                mul(p, topology.gridSize()))));
    }

    /**
     * Returns the list of points and labels from the given text map
     *
     * @param mapText the map text
     */
    public static List<Tuple2<Point, String>> parseMap(String mapText) {
        List<Tuple2<Point, String>> result = new ArrayList<>();
        String[] lines = mapText.split("\\s");
        for (int j = lines.length - 1; j >= 0; j--) {
            String line = lines[j];
            for (int i = 0; i < line.length(); i++) {
                String ch = line.substring(i, i + 1);
                result.add(Tuple2.of(new Point(i, lines.length - 1 - j), ch));
            }
        }
        return result;
    }

    /**
     * Returns the list labels by cell index from the given text map
     *
     * @param topology the grid topology
     * @param mapText  the map text
     */
    public static Stream<Tuple2<Integer, String>> parseMapByIndices(GridTopology topology, String mapText) {
        return parseMap(topology, mapText)
                .map(t ->
                        t.setV1(topology.indexOf(t._1)))
                .filter(t -> t._1 >= 0);
    }

    private UnaryOperator<RadarMap> radarMapper;
    private long simulationTime;
    private GridTopology topology;

    public RadarMapBuilder() {
        this.simulationTime = 1;
        this.topology = TOPOLOGY;
        this.radarMapper = UnaryOperator.identity();
    }

    public RadarMapBuilder add(UnaryOperator<RadarMap> mapper) {
        UnaryOperator<RadarMap> radarMapper0 = radarMapper;
        radarMapper = map ->
                mapper.apply(radarMapper0.apply(map));
        return this;
    }

    public RadarMapBuilder addContactsCell(Point2D location) {
        return addContactsCell(() -> location);
    }

    public RadarMapBuilder addContactsCell(Supplier<Point2D> location) {
        return mapCell(location, cell ->
                cell.setContact(simulationTime));
    }

    public RadarMapBuilder addEchoCell(Point2D location) {
        return addEchoCell(() -> location);
    }

    public RadarMapBuilder addEchoCell(Supplier<Point2D> supplier) {
        return mapCell(supplier, cell ->
                cell.addEchogenic(simulationTime, DECAY));
    }

    public RadarMapBuilder addEmptyCell(Supplier<Point2D> location) {
        return mapCell(location, cell ->
                cell.addAnechoic(simulationTime, DECAY));
    }

    public RadarMapBuilder addEmptyCell(Point2D location) {
        return addEmptyCell(() -> location);
    }

    public RadarMapBuilder addSimulationTime(long deltaTime) {
        simulationTime += deltaTime;
        return this;
    }

    public RadarMap build() {
        return radarMapper.apply(RadarMap.empty(topology));
    }

    Function<String, UnaryOperator<MapCell>> cellMapper(String unknown, String empty, String obstacle, String contact) {
        return id -> {
            if (unknown.equals(id)) {
                return MapCell::setUnknown;
            } else if (empty.equals(id)) {
                return cell ->
                        cell.addAnechoic(simulationTime, DECAY);
            } else if (obstacle.equals(id)) {
                return cell ->
                        cell.addEchogenic(simulationTime, DECAY);
            } else if (contact.equals(id)) {
                return cell ->
                        cell.setContact(simulationTime);
            } else if (isMarker(id)) {
                return cell ->
                        cell.addEchogenic(simulationTime, DECAY);
            } else {
                return UnaryOperator.identity();
            }

        };
    }

    public RadarMapBuilder gridSize(double gridSize) {
        topology = GridTopology.create(topology.center(),
                topology.width(),
                topology.height(),
                gridSize);
        return this;
    }

    public RadarMapBuilder map(UnaryOperator<MapCell> cellMapper) {
        return add(radarMap ->
                radarMap.map(cellMapper));
    }

    public RadarMapBuilder mapCell(Supplier<Point2D> supplier, UnaryOperator<MapCell> cellMapper) {
        return add(radarMap -> {
            Point2D location = supplier.get();
            return radarMap.updateCellAt(location, cellMapper);
        });
    }

    public RadarMapBuilder mapRadar(IntStream range, UnaryOperator<MapCell> cellMapper) {
        return add(radarMap ->
                radarMap.map(range, cellMapper));
    }

    public RadarMapBuilder radarMap(String mapText) {
        return setRadarMap(UNKNOWN_ID, EMPTY_ID, OBSTACLE_ID, CONTACT_ID, mapText);
    }

    public RadarMapBuilder radarSize(int width, int height) {
        topology = topology.size(width, height);
        return this;
    }

    public RadarMapBuilder setRadarMap(String unknown, String empty, String obstacle, String contact,
                                       String mapText) {
        // Creates the cell mapper by label
        Function<String, UnaryOperator<MapCell>> cellMappers = cellMapper(unknown, empty, obstacle, contact);
        return add(radarMap -> {
            // Creates the cell mapper by cell index
            GridTopology topology1 = radarMap.topology();
            List<Tuple2<Integer, UnaryOperator<MapCell>>> labels = parseMapByIndices(topology1, mapText)
                    .map(t ->
                            t.setV2(cellMappers.apply(t._2))
                    )
                    .toList();
            // Creates the resulting cell array
            MapCell[] cells = Arrays.copyOf(radarMap.cells(), radarMap.cells().length);
            // Apply the cell mappers for each index
            for (Tuple2<Integer, UnaryOperator<MapCell>> t : labels) {
                cells[t._1] = t._2.apply(cells[t._1]);
            }
            return new RadarMap(topology1, cells, radarMap.cleanTimestamp());
        });
    }

    public RadarMapBuilder simulationTime(long simulationTime) {
        this.simulationTime = simulationTime;
        return this;
    }

    public GridTopology topology() {
        return this.topology;
    }
}
