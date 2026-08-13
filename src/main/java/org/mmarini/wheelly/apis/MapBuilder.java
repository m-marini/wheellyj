/*
 * Copyright (c) 2022-2026 Marco Marini, marco.marini@mmarini.org
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
import org.jetbrains.annotations.NotNull;
import org.mmarini.MapStream;
import org.mmarini.Tuple2;
import org.mmarini.yaml.Locator;

import java.awt.geom.Point2D;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apps.AppYaml.loadDoubleArray;

/**
 * The builder of point Map
 */
public record MapBuilder(int size, double gridSize, AreaExpression exp, Map<String, AreaExpression> labelAreas) {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/map-schema-1.0";
    public static final String SIZE_ID = "size";
    public static final String GRID_SIZE_ID = "gridSize";
    public static final String SHAPES_ID = "shapes";
    public static final String RADIUS_ID = "radius";
    public static final String COMPONENTS_ID = "components";
    public static final String SINGLES_ID = "singles";
    public static final String RECT_ID = "rect";
    public static final String LINES_ID = "lines";
    public static final String CORNER_ID = "corner";
    public static final String LABEL_ID = "label";

    /**
     * Returns the map builder from JSON
     *
     * @param root    the root document
     * @param locator the definition locator
     */
    static MapBuilder create(JsonNode root, Locator locator) {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        int size = locator.path(SIZE_ID).getNode(root).asInt();
        double gridSize = locator.path(GRID_SIZE_ID).getNode(root).asDouble();
        Map<String, AreaExpression> labelAreas = new HashMap<>();
        // Creates obstacles area expression
        AreaExpression exp = AreaExpression.or(
                // Scan for all shapes
                locator.path(SHAPES_ID).elements(root)
                        .map(shapeLocator -> {
                            double radius = shapeLocator.path(RADIUS_ID).getNode(root).asDouble(gridSize / 2);
                            String label = shapeLocator.path(LABEL_ID).getNode(root).asText(null);
                            // Scan for all components
                            AreaExpression exp1 = AreaExpression.or(
                                    shapeLocator.path(COMPONENTS_ID).elements(root)
                                            .flatMap(componentLocator -> {
                                                Locator singlesLocator = componentLocator.path(SINGLES_ID);
                                                Locator rectLocator = componentLocator.path(RECT_ID);
                                                Locator linesLocator = componentLocator.path(LINES_ID);
                                                if (!singlesLocator.getNode(root).isMissingNode()) {
                                                    // Scan for single component
                                                    return singlesLocator.elements(root)
                                                            .map(ptsLocator -> {
                                                                double[] center = loadDoubleArray(root, ptsLocator);
                                                                return AreaExpression.circle(new Point2D.Double(center[0], center[1]), radius);
                                                            });
                                                } else if (!rectLocator.getNode(root).isMissingNode()) {
                                                    // Scan for rectangular component
                                                    double[] corner = loadDoubleArray(root, rectLocator.path(CORNER_ID));
                                                    double[] sizes = loadDoubleArray(root, rectLocator.path(SIZE_ID));
                                                    return rectangle(radius, corner, sizes);
                                                } else {
                                                    // Scan for lines component
                                                    double[] coords = linesLocator.elements(root)
                                                            .flatMap(ptsLocator -> ptsLocator.elements(root).map(
                                                                    l -> l.getNode(root).asDouble())

                                                            ).mapToDouble(v -> v)
                                                            .toArray();
                                                    return lines(radius, coords);
                                                }
                                            }));
                            if (label != null) {
                                labelAreas.merge(label, exp1, AreaExpression::or);
                            }
                            return exp1;
                        }));
        return new MapBuilder(size, gridSize, exp, labelAreas);
    }

    /**
     * Returns an empty map builder
     *
     * @param size     the number of horizontal and vertical cells
     * @param gridSize the grid size (m)
     */
    public static MapBuilder empty(int size, double gridSize) {
        return new MapBuilder(size, gridSize, null, Map.of());
    }

    /**
     *
     * @param halfWidth the half width of lines
     * @param coords    the vertices of lines
     */
    static Stream<AreaExpression> lines(double halfWidth, double[] coords) {
        Point2D[] vertices = new Point2D[coords.length / 2];
        for (int i = 0; i < vertices.length; i++) {
            vertices[i] = new Point2D.Double(coords[i * 2], coords[i * 2 + 1]);
        }
        return lines(halfWidth, vertices);
    }

    /**
     * Returns the line area expression
     *
     * @param halfWidth the half width of lines
     * @param vertices  the vertices of lines
     */
    @NotNull
    private static Stream<AreaExpression> lines(double halfWidth, Point2D... vertices) {
        return IntStream.range(0, vertices.length - 1)
                .mapToObj(i ->
                        AreaExpression.roundSegment(vertices[i], vertices[i + 1], halfWidth));
    }

    /**
     * Returns the rectangle area expression
     *
     * @param halfWidth the half width of rectangle
     * @param corner    the corner
     * @param size      the rectangle size
     */
    static Stream<AreaExpression> rectangle(double halfWidth, double[] corner, double[] size) {
        return lines(halfWidth,
                new Point2D.Double(corner[0], corner[1]),
                new Point2D.Double(corner[0] + size[0], corner[1]),
                new Point2D.Double(corner[0] + size[0], corner[1] + size[1]),
                new Point2D.Double(corner[0], corner[1] + size[1]),
                new Point2D.Double(corner[0], corner[1])
        );
    }

    /**
     * Adds an obstacles area expression
     *
     * @param exp   the expression
     * @param label the label
     */
    public MapBuilder add(AreaExpression exp, String label) {
        requireNonNull(exp);
        AreaExpression exp1 = this.exp == null
                ? exp
                : AreaExpression.or(exp, this.exp);
        Map<String, AreaExpression> labelAreas1 = labelAreas;
        if (label != null) {
            labelAreas1 = new HashMap<>(labelAreas1);
            labelAreas1.merge(label, exp, AreaExpression::or);
        }
        return new MapBuilder(size, gridSize, exp1, labelAreas1);
    }

    /**
     * Adds a circle obstacle to the builder
     *
     * @param x     the centre abscissa
     * @param y     the centre ordinate
     * @param label the label
     */
    public MapBuilder addObstacle(double x, double y, String label) {
        return add(AreaExpression.circle(new Point2D.Double(x, y), gridSize / 2), label);
    }

    /**
     * Returns the stream of all cell centres
     */
    private Stream<Point2D> allCentreStream() {
        double offset = (size - 1) * gridSize / 2;
        return IntStream.range(0, size)
                .mapToDouble(i ->
                        i * gridSize - offset)
                .boxed()
                .flatMap(y ->
                        IntStream.range(0, size)
                                .mapToDouble(i ->
                                        i * gridSize - offset)
                                .mapToObj(x ->
                                        new Point2D.Double(x, y)
                                )
                );
    }

    /**
     * Returns the obstacles map centered in (0,0)
     */
    public List<Obstacle> build() {
        if (exp == null) {
            return List.of();
        }
        AreaExpression.Parser parser = exp.createParser();
        double radius = gridSize / 2;
        return allCentreStream()
                .filter(parser::test)
                .map(center -> {
                    String label = MapStream.of(labelAreas)
                            .tuples()
                            .filter(t ->
                                    t._2.createParser().test(center))
                            .findAny()
                            .map(Tuple2::getV1)
                            .orElse(null);
                    return new Obstacle(center, radius, label);
                })
                .toList();
    }

    /**
     *
     * @param random              the random generator
     * @param label               the obstacle label
     * @param center              the forbidden circle centre
     * @param minObstacleDistance the forbidden circle radius
     * @param numObstacles        the number of obstacles
     */
    public MapBuilder rand(Random random, String label, Point2D center, double minObstacleDistance, int numObstacles) {
        if (numObstacles == 0) {
            return this;
        }
        /*
        La fase di build deve identificare gli ostacoli con label quindi è necessario
        memorizzare le equazioni degli ostacoli associati a etichette.
        */
        /*
        Si determina l'area target rimuovendo tutte le aree già vincolate e l'area intorno al robot.
        Poi si scelgono a caso celle disponibili e si aggiungono le equazioni delle celle selezionate.
         */
        AreaExpression forbiden = AreaExpression.circle(new Point2D.Double(center.getX(), center.getY()), minObstacleDistance);
        AreaExpression.Parser availableArea = AreaExpression.not(
                        exp == null
                                ? forbiden
                                : AreaExpression.or(exp, forbiden))
                .createParser();
        List<Point2D> targets = new ArrayList<>(allCentreStream()
                .filter(availableArea::test)
                .toList());
        Collections.shuffle(targets, random);
        AreaExpression randomObs = AreaExpression.or(
                targets.stream().limit(numObstacles)
                        .map(pts ->
                                AreaExpression.circle(pts, gridSize / 2)));
        return add(randomObs, label);
    }
}
