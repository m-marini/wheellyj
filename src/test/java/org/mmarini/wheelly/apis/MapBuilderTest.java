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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.yaml.Locator;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.RadarMapTest.GRID_SIZE;
import static org.mmarini.wheelly.apis.Utils.MM;

class MapBuilderTest {
    public static final long SEED = 1234;
    public static final double RADIUS = 0.1;
    static final String YAML_HEADER = """
            ---
            $schema:""" + " " + MapBuilder.SCHEMA_NAME + "\n" +
            "class: " + MapBuilder.class.getName() + "\n" + """
            size: 41
            gridSize: 0.2
            """;
    static final String YAML_SINGLES = YAML_HEADER + """
            shapes:
              - components:
                - singles:
                  - [-1, -1]
                  - [1, 1]
            """;
    static final String YAML_LABEL = YAML_HEADER + """
            shapes:
              - label: A
                components:
                  - singles:
                    - [-1, -1]
                    - [1, 1]
            """;
    static final String YAML_RECT = YAML_HEADER + """
            shapes:
              - components:
                - rect:
                    corner: [1, 1]
                    size: [0.2, 0.2]
            """;
    static final String YAML_LINES = YAML_HEADER + """
            shapes:
              - components:
                - lines:
                  - [1, 1]
                  - [1.2, 1.2]
                  - [1.4, 1]
            """;
    static final String YAML_MISSING_COORDINATE0 = YAML_HEADER + """
            shapes:
              - radius: 0.02
                components:
                  - singles:
                    - []
            """;
    static final String YAML_MISSING_COORDINATE1 = YAML_HEADER + """
            shapes:
              - radius: 0.02
                components:
                - singles:
                  - [1]
            """;
    static final String YAML_MISSING_COORDINATE3 = YAML_HEADER + """
            shapes:
              - radius: 0.02
                components:
                  - singles:
                    - [1,2,3]
            """;
    static final String YAML_MISSING_CORNER = YAML_HEADER + """
            shapes:
              - radius: 0.02
                components:
                  - rect:
                      size: [1,2]
            """;
    static final String YAML_MISSING_SIZE = YAML_HEADER + """
            shapes:
              - radius: 0.02
                components:
                  - rect:
                      corner: [1,2]
            """;
    static final String YAML_MISSING_LINES0 = YAML_HEADER + """
            shapes:
              - radius: 0.02
                components:
                  - lines: []
            """;
    static final String YAML_MISSING_LINES1 = YAML_HEADER + """
            shapes:
              - radius: 0.02
                components:
                  - lines:
                    - [1, 2]
            """;
    static final String YAML_WRONG_OBSTACLE = YAML_HEADER + """
            shapes:
              - radius: 0.02
                components:
                  - bad: bad
            """;

    static Stream<Arguments> dataCreateErrors() {
        return Stream.of(
                Arguments.of(YAML_MISSING_LINES0, "$.shapes[0].components[0].lines: deve esserci un numero minimo di 2 elementi nell'array"),
                Arguments.of(YAML_MISSING_LINES1, "$.shapes[0].components[0].lines: deve esserci un numero minimo di 2 elementi nell'array"),
                Arguments.of(YAML_MISSING_SIZE, "$.shapes[0].components[0].rect.size: è obbligatorio ma è mancante"),
                Arguments.of(YAML_MISSING_CORNER, "$.shapes[0].components[0].rect.corner: è obbligatorio ma è mancante"),
                Arguments.of(YAML_WRONG_OBSTACLE, "$.shapes[0].components[0].singles: è obbligatorio ma è mancante"),
                Arguments.of(YAML_MISSING_COORDINATE0, "deve esserci un numero minimo di 2 elementi nell'array"),
                Arguments.of(YAML_MISSING_COORDINATE1, "deve esserci un numero minimo di 2 elementi nell'array"),
                Arguments.of(YAML_MISSING_COORDINATE3, "deve esserci un numero massimo di 2 elementi nell'array")
        );
    }

    @ParameterizedTest(name = "[{index}] {1}")
    @MethodSource("dataCreateErrors")
    void testCreateErrors(String text, String message) {
        Throwable ex = assertThrows(Throwable.class, () -> MapBuilder.create(org.mmarini.yaml.Utils.fromText(text), Locator.root()));
        assertThat(ex.getMessage(), containsString(message));
    }

    @Test
    void testCreateLabel() {
        List<Obstacle> map = MapBuilder.empty(41, GRID_SIZE)
                .addObstacle(0, 0, "A")
                .addObstacle(1, 1, "B")
                .build();
        assertThat(map, hasSize(2));

        Obstacle obs = map.getFirst();
        assertThat(obs.centre(), pointCloseTo(0, 0, MM));
        assertThat(obs.radius(), closeTo(RADIUS, MM));
        assertEquals("A", obs.label());

        obs = map.get(1);
        assertThat(obs.centre(), pointCloseTo(1, 1, MM));
        assertThat(obs.radius(), closeTo(RADIUS, MM));
        assertEquals("B", obs.label());
    }

    @Test
    void testCreateLines() throws IOException {
        JsonNode node = org.mmarini.yaml.Utils.fromText(YAML_LINES);
        List<Obstacle> map = MapBuilder.create(node, Locator.root()).build();
        assertThat(map, hasSize(3));

        Obstacle obs = map.getFirst();
        assertThat(obs.centre(), pointCloseTo(1, 1, MM));
        assertThat(obs.radius(), closeTo(RADIUS, MM));

        obs = map.get(1);
        assertThat(obs.centre(), pointCloseTo(1.4, 1, MM));
        assertThat(obs.radius(), closeTo(RADIUS, MM));

        obs = map.get(2);
        assertThat(obs.centre(), pointCloseTo(1.2, 1.2, MM));
        assertThat(obs.radius(), closeTo(RADIUS, MM));
    }

    @Test
    void testCreateRect() throws IOException {
        JsonNode node = org.mmarini.yaml.Utils.fromText(YAML_RECT);
        List<Obstacle> map = MapBuilder.create(node, Locator.root()).build();
        assertThat(map, hasSize(4));

        Obstacle obs = map.getFirst();
        assertThat(obs.centre(), pointCloseTo(1, 1, MM));
        assertThat(obs.radius(), closeTo(RADIUS, MM));

        obs = map.get(1);
        assertThat(obs.centre(), pointCloseTo(1.2, 1, MM));
        assertThat(obs.radius(), closeTo(RADIUS, MM));

        obs = map.get(2);
        assertThat(obs.centre(), pointCloseTo(1, 1.2, MM));
        assertThat(obs.radius(), closeTo(RADIUS, MM));

        obs = map.get(3);
        assertThat(obs.centre(), pointCloseTo(1.2, 1.2, MM));
        assertThat(obs.radius(), closeTo(RADIUS, MM));
    }

    @Test
    void testCreateSingle() throws IOException {
        JsonNode node = org.mmarini.yaml.Utils.fromText(YAML_SINGLES);
        List<Obstacle> map = MapBuilder.create(node, Locator.root()).build();
        assertThat(map, hasSize(2));

        Obstacle obs = map.getFirst();
        assertThat(obs.centre(), pointCloseTo(-1, -1, MM));
        assertThat(obs.radius(), closeTo(RADIUS, MM));

        obs = map.get(1);
        assertThat(obs.centre(), pointCloseTo(1, 1, MM));
        assertThat(obs.radius(), closeTo(RADIUS, MM));
    }

    @Test
    void testCreateSingleLabel() throws IOException {
        JsonNode node = org.mmarini.yaml.Utils.fromText(YAML_LABEL);
        List<Obstacle> map = MapBuilder.create(node, Locator.root()).build();
        assertThat(map, hasSize(2));

        Obstacle obs = map.getFirst();
        assertThat(obs.centre(), pointCloseTo(-1, -1, MM));
        assertThat(obs.radius(), closeTo(RADIUS, MM));
        assertEquals("A", obs.label());

        obs = map.get(1);
        assertThat(obs.centre(), pointCloseTo(1, 1, MM));
        assertThat(obs.radius(), closeTo(RADIUS, MM));
        assertEquals("A", obs.label());
    }

    @Test
    void testRandomLabels() {
        MapBuilder builder = MapBuilder.empty(7, GRID_SIZE)
                .add(AreaExpression.rightHalfPlane(new Point2D.Double(), Complex.DEG90), null)
                .rand(new Random(SEED), "A", new Point2D.Double(), 0.4, 2);
        List<Obstacle> map = builder.build();
        assertThat(map, hasSize(greaterThan(2)));

        assertTrue(map.stream().allMatch(obs -> obs.radius() == RADIUS));
        assertEquals(2, map.stream().filter(obs -> obs.label() != null).count());
    }

    @Test
    void testRandomObstacles() {
        MapBuilder builder = MapBuilder.empty(7, GRID_SIZE)
                .rand(new Random(SEED), null, new Point2D.Double(), 0.4, 2);
        List<Obstacle> map = builder.build();
        assertThat(map, hasSize(2));

        assertTrue(map.stream().allMatch(obs -> obs.label() == null));
        assertTrue(map.stream().allMatch(obs -> obs.radius() == RADIUS));
    }
}