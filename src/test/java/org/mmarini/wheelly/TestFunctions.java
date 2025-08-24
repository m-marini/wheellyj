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

package org.mmarini.wheelly;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.core.Flowable;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.params.provider.Arguments;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.GridTopology;
import org.mmarini.wheelly.apis.RobotApi;
import org.mmarini.wheelly.apis.WheellyMessage;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.Utils.zipWithIndex;
import static org.mmarini.yaml.Utils.fromResource;

public interface TestFunctions {
    static Predicate<? super WheellyMessage> after(long time) {
        return msg -> msg.simulationTime() > time;
    }

    static Predicate<? super WheellyMessage> before(long time) {
        return msg -> msg.simulationTime() < time;
    }

    static <T extends WheellyMessage> T findMessage(List<T> messages, Predicate<? super WheellyMessage> pred) {
        return messages.stream().filter(pred).findFirst().orElse(null);
    }

    /**
     * Pause the execution until simulation time >= time
     *
     * @param robot the robot
     * @param time  the time
     */
    static void pause(RobotApi robot, long time) {
        robot.readMotion()
                .filter(msg -> msg.simulationTime() >= time)
                .blockingFirst();
    }

    static ArgumentJsonParser jsonFileArguments(String resource) throws IOException {
        return new ArgumentJsonParser(fromResource(resource));
    }

    static Matcher<INDArray> matrixCloseTo(long[] shape, double epsilon, float... exp) {
        return matrixCloseTo(Nd4j.create(exp).reshape(shape), epsilon);
    }

    static Matcher<INDArray> matrixCloseTo(INDArray exp, double epsilon) {
        requireNonNull(exp);
        return new CustomMatcher<>(format("INDArray close to %s within +- %f",
                exp,
                epsilon)) {
            @Override
            public boolean matches(Object o) {
                return o instanceof INDArray
                        && ((INDArray) o).equalsWithEps(exp, epsilon);
            }
        };
    }

    static Matcher<INDArray> matrixShape(long... expShape) {
        requireNonNull(expShape);
        return new CustomMatcher<>(format("INDArray with shape %s",
                Arrays.toString(expShape))) {
            @Override
            public boolean matches(Object o) {
                return o instanceof INDArray
                        && Arrays.equals(((INDArray) o).shape(), expShape);
            }
        };
    }

    static Predicate<WheellyMessage> notBefore(long time) {
        return msg -> msg.simulationTime() >= time;
    }

    static Stream<Tuple2<Point, String>> parseMap(String... lines) {
        Stream.Builder<Tuple2<Point, String>> builder = Stream.builder();
        for (int j = lines.length - 1; j >= 0; j--) {
            String line = lines[j];
            for (int i = 0; i < line.length(); i++) {
                String ch = line.substring(i, i + 1);
                builder.add(Tuple2.of(new Point(i, lines.length - 1 - j), ch));
            }
        }
        return builder.build();
    }

    static <T extends WheellyMessage> void waitFor(Flowable<T> messages, Predicate<T> pred, long timeout) {
        messages.filter(pred::test)
                .firstElement()
                .timeout(timeout, TimeUnit.MILLISECONDS)
                .blockingGet();
    }

    static String text(String... lines) {
        return String.join("\n", lines) + "\n";
    }


    class ArgumentJsonParser {
        public static <T> Predicate<Tuple2<T, String>> all() {
            return x -> true;
        }

        public static Function<Stream<Tuple2<Point, String>>, Stream<Object[]>> allIndicesByValue(GridTopology topology, String regEx) {
            Predicate<String> p = Pattern.compile(regEx).asMatchPredicate();
            return stream -> mapToIndex(topology, stream)
                    .map(t ->
                            new Object[]{t._1, p.test(t._2)});
        }

        public static Function<Stream<Tuple2<Point, String>>, Stream<Object[]>> allPointsOfValue(GridTopology topology, String regEx) {
            Predicate<String> p = Pattern.compile(regEx).asMatchPredicate();
            return stream -> mapToPoint(topology, stream)
                    .filter(t -> p.test(t._2))
                    .map(t -> new Object[]{t._1});
        }

        public static Function<Stream<Tuple2<Point, String>>, Stream<Object>> anyPointOfValue(GridTopology topology, String regex) {
            Predicate<String> p = Pattern.compile(regex).asMatchPredicate();
            return stream -> Stream.of(mapToPoint(topology, stream)
                    .filter(t -> p.test(t._2))
                    .findAny()
                    .map(t -> t._1)
                    .orElse(null));
        }

        public static Stream<Tuple2<Integer, String>> mapToIndex(GridTopology topology, Stream<Tuple2<Point, String>> stream) {
            return stream
                    .filter(t -> t._1.x >= 0 && t._1.x < topology.width()
                            && t._1.y >= 0 && t._1.y < topology.height())
                    .map(t -> t.setV1(t._1.x + t._1.y * topology.width()));
        }

        public static Stream<Tuple2<Point2D, String>> mapToPoint(GridTopology topology, Stream<Tuple2<Point, String>> stream) {
            return stream
                    .filter(t -> t._1.x >= 0 && t._1.x < topology.width()
                            && t._1.y >= 0 && t._1.y < topology.height())
                    .map(t -> t.setV1(topology.location(t._1.x + t._1.y * topology.width())));
        }

        private final JsonNode root;
        private final List<Function<Locator, Object[][]>> fieldParsers;

        public ArgumentJsonParser(JsonNode root) {
            this.root = root;
            this.fieldParsers = new ArrayList<>();
        }

        public <T> ArgumentJsonParser add(String key, BiFunction<Locator, JsonNode, T> func) {
            fieldParsers.add(locator -> {
                Locator fieldLocator = locator.path(key);
                JsonNode node = fieldLocator.getNode(root);
                if (node.isMissingNode()) {
                    throw new IllegalArgumentException(format("Missing node %s", fieldLocator));
                }
                return new Object[][]{{func.apply(fieldLocator, root)}};
            });
            return this;
        }

        public <T> ArgumentJsonParser add(String key, Function<JsonNode, T> func) {
            fieldParsers.add(locator -> {
                Locator fieldLocator = locator.path(key);
                JsonNode node = fieldLocator.getNode(root);
                if (node.isMissingNode()) {
                    throw new IllegalArgumentException(format("Missing node %s", fieldLocator));
                }
                return new Object[][]{{func.apply(node)}};
            });
            return this;
        }

        public ArgumentJsonParser addBoolean(String key) {
            fieldParsers.add(locator -> {
                Locator fieldLocator = locator.path(key);
                JsonNode node = fieldLocator.getNode(root);
                if (node.isMissingNode()) {
                    throw new IllegalArgumentException(format("Missing node %s", fieldLocator));
                }
                return new Object[][]{{node.asBoolean()}};
            });
            return this;
        }

        public ArgumentJsonParser addDouble(String key) {
            fieldParsers.add(locator -> {
                Locator fieldLocator = locator.path(key);
                JsonNode node = fieldLocator.getNode(root);
                if (node.isMissingNode()) {
                    throw new IllegalArgumentException(format("Missing node %s", fieldLocator));
                }
                return new Object[][]{{node.asDouble()}};
            });
            return this;
        }

        public ArgumentJsonParser addInt(String key) {
            fieldParsers.add(locator -> {
                Locator fieldLocator = locator.path(key);
                JsonNode node = fieldLocator.getNode(root);
                if (node.isMissingNode()) {
                    throw new IllegalArgumentException(format("Missing node %s", fieldLocator));
                }
                return new Object[][]{{node.asInt()}};
            });
            return this;
        }

        public ArgumentJsonParser addMap(String key, Function<Stream<Tuple2<Point, String>>, Stream<Object>> processor) {
            fieldParsers.add(locator -> {
                Locator fieldLocator = locator.path(key);
                JsonNode node = fieldLocator.getNode(root);
                if (node.isMissingNode()) {
                    throw new IllegalArgumentException(format("Missing node %s", fieldLocator));
                }
                return new Object[][]{processor.apply(parseMap(node.asText().split(" ")))
                        .toArray(Object[]::new)};
            });
            return this;
        }

        public ArgumentJsonParser addPoint(String key) {
            return add(key, node -> new Point2D.Double(
                    node.path("x").asDouble(),
                    node.path("y").asDouble()
            ));
        }

        public ArgumentJsonParser addPoints(String key) {
            return add(key, (loc, root) ->
                    loc.elements(root)
                            .<Point2D>map(pLoc ->
                                    new Point2D.Double(
                                            pLoc.path("x").getNode(root).asDouble(),
                                            pLoc.path("y").getNode(root).asDouble()
                                    ))
                            .toList());
        }

        public ArgumentJsonParser addString(String key) {
            fieldParsers.add(locator -> {
                Locator fieldLocator = locator.path(key);
                JsonNode node = fieldLocator.getNode(root);
                if (node.isMissingNode()) {
                    throw new IllegalArgumentException(format("Missing node %s", fieldLocator));
                }
                return new Object[][]{{node.asText()}};
            });
            return this;
        }

        public ArgumentJsonParser forEachCell(String key,
                                              Function<Stream<Tuple2<Point, String>>, Stream<Object[]>> processor) {
            fieldParsers.add(locator -> {
                Locator fieldLocator = locator.path(key);
                JsonNode node = fieldLocator.getNode(root);
                if (node.isMissingNode()) {
                    throw new IllegalArgumentException(format("Missing node %s", fieldLocator));
                }
                return processor.apply(parseMap(node.asText().split(" ")))
                        .toArray(Object[][]::new);
            });
            return this;
        }

        public Stream<Arguments> parse() {
            return Locator.root().elements(root)
                    .flatMap(itemLocator -> {
                        // Generate all values
                        List<Object[][]> values = fieldParsers.stream().map(
                                parser -> parser.apply(itemLocator)
                        ).toList();
                        return new ArgumentsTraverse(values).traverse();
                    });
        }

        static class ArgumentsTraverse {
            private final Stream.Builder<Arguments> builder;
            private final List<Object[][]> data;
            private final Object[] args;

            ArgumentsTraverse(List<Object[][]> data) {
                this.data = data;
                this.builder = Stream.builder();
                if (data.stream().anyMatch(args -> args.length == 0)) {
                    throw new IllegalArgumentException(format("Missing data for fields %s",
                            Arrays.toString(zipWithIndex(data)
                                    .filter(t -> t._2.length == 0)
                                    .mapToInt(Tuple2::getV1)
                                    .toArray())
                    ));
                }
                int noArgs = data.stream().mapToInt(args -> args.length > 0 ? args[0].length : 0).sum();
                this.args = new Object[noArgs];
            }

            public Stream<Arguments> traverse() {
                traverse(0, 0);
                return builder.build();
            }

            private void traverse(int index, int offset) {
                if (index >= data.size()) {
                    builder.add(Arguments.of(Arrays.copyOf(args, args.length)));
                } else {
                    Object[][] partialArgs = data.get(index);
                    for (Object[] partialArg : partialArgs) {
                        System.arraycopy(partialArg, 0, args, offset, partialArg.length);
                        traverse(index + 1, offset + partialArg.length);
                    }
                }
            }
        }
    }
}
