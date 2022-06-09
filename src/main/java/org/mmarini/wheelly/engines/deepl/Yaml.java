/*
 *
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
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

package org.mmarini.wheelly.engines.deepl;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.Utils;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static org.mmarini.yaml.schema.Validator.*;

public interface Yaml {

    String FULL_STATUS_ENCODER = "FullStatus";
    String SIMPLE_STATUS_ENCODER = "SimpleStatus";
    String MAP_STATUS_ENCODER = "MapStatus";

    static Validator actor() {
        return object(
                objectPropertiesRequired(Map.of(
                                "type", values("DiscreteActor")),
                        List.of("type")),
                deferred((root, locator) -> {
                    String type = locator.path("type").getNode(root).asText();
                    if ("DiscreteActor".equals(type)) {
                        return discreteActor();
                    }
                    throw new IllegalArgumentException(format("Wrong type \"%s\"", type));
                }));
    }

    static Validator actors() {
        return array(
                arrayItems(actor()),
                minItems(4),
                maxItems(4)
        );
    }

    static Validator agentConf() {
        return objectPropertiesRequired(Map.of(
                        "rewardDecay", nonNegativeNumber(),
                        "valueDecay", nonNegativeNumber(),
                        "rewardRange", rangeDef(),
                        "averageReward", number(),
                        "actors", actors(),
                        "saveFile", string(minLength(1)),
                        "saveInterval", positiveInteger()
                ),
                List.of("rewardDecay", "valueDecay", "rewardRange", "actors")
        );
    }

    static Validator discreteActor() {
        return objectPropertiesRequired(Map.of(
                        "noValues", integer(positiveInteger(), minimum(2)),
                        "outputRange", rangeDef(),
                        "alpha", nonNegativeNumber(),
                        "alphaDecay", nonNegativeNumber(),
                        "epsilon", nonNegativeNumber(),
                        "preferenceRange", rangeDef()),
                List.of(
                        "noValues",
                        "outputRange",
                        "alpha",
                        "alphaDecay",
                        "preferenceRange"));
    }

    static Validator engineConf() {
        return objectPropertiesRequired(Map.of(
                        "stateEncoder", stateEncoder(),
                        "agentFile", string(minLength(1))
                ),
                List.of("stateEncoder", "agentFile")
        );
    }

    static Validator network() {
        return object(
                objectPropertiesRequired(Map.ofEntries(
                                Map.entry("version", string(values("0.1"))),
                                Map.entry("learningRate", positiveNumber()),
                                Map.entry("activation", string(values(
                                        "SOFTPLUS",
                                        "TANH",
                                        "RELU",
                                        "HARDTANH",
                                        "SIGMOID",
                                        "HARDSIGMOID"
                                ))),
                                Map.entry("numInputs", positiveInteger()),
                                Map.entry("numOutputs", arrayItems(positiveInteger())),
                                Map.entry("numHiddens", arrayItems(nonNegativeInteger())),
                                Map.entry("shortcuts", shortcuts()),
                                Map.entry("updater", string(values("Sgd"))),
                                Map.entry("maxAbsGradient", positiveNumber()),
                                Map.entry("maxAbsParameters", positiveNumber()),
                                Map.entry("file", string()),
                                Map.entry("dropOut", positiveNumber())),
                        List.of("version",
                                "numInputs",
                                "numOutputs",
                                "learningRate",
                                "maxAbsParameters",
                                "maxAbsGradient",
                                "file"
                        )),
                deferred((root, locator) -> {
                    int noHiddens = locator.path("numHiddens").size(root);
                    return objectProperties(Map.of("shortcuts", postShortcuts(noHiddens)));
                })
        );
    }

    static Validator postShortcuts(int noHiddens) {
        return arrayItems(
                arrayPrefixItems(List.of(
                        integer(
                                minimum(0),
                                maximum(noHiddens)),
                        integer(
                                minimum(1),
                                maximum(noHiddens + 1))
                ))
        );
    }

    /**
     * Returns the range array (2x1)
     *
     * @param node the json node
     */
    static INDArray range(JsonNode node) {
        double[] values = Utils.stream(node.elements())
                .mapToDouble(JsonNode::asDouble)
                .toArray();
        return Nd4j.create(values).reshape(2, 1);
    }

    static Validator rangeDef() {
        return array(prefixItems(
                        number(),
                        number()
                ),
                deferred((root, locator) -> {
                    INDArray range = range(locator.getNode(root));
                    return array(prefixItems(
                            number(),
                            number(minimum(range.getDouble(0))))
                    );
                })
        );
    }

    static Validator shortcut() {
        return array(
                arrayItems(nonNegativeInteger()),
                minItems(2),
                maxItems(2)
        );
    }

    static Validator shortcuts() {
        return array(arrayItems(shortcut()));
    }

    static SignalEncoder stateEncoder(JsonNode root, Locator locator) {
        Locator typeLoc = locator.path("type");
        String type = typeLoc.getNode(root).asText();
        switch (type) {
            case FULL_STATUS_ENCODER:
                return FullFeaturesSignalEncoder.create();
            case MAP_STATUS_ENCODER:
                return MapFeaturesSignalEncoder.create();
            case SIMPLE_STATUS_ENCODER:
                return SimpleFeaturesSignalEncoder.create();
            default:
                throw new IllegalArgumentException(format("Wrong type \"%s\" at %s", type, typeLoc));
        }
    }


    static Validator stateEncoder() {
        return object(
                objectPropertiesRequired(Map.of(
                                "type", values("Tiles", FULL_STATUS_ENCODER, MAP_STATUS_ENCODER, SIMPLE_STATUS_ENCODER)),
                        List.of("type")),
                deferred((root, locator) -> {
                    String type = locator.path("type").getNode(root).asText();
                    switch (type) {
                        case "Tiles":
                            return tiles();
                        case FULL_STATUS_ENCODER:
                        case SIMPLE_STATUS_ENCODER:
                        case MAP_STATUS_ENCODER:
                            return object();
                        default:
                            throw new IllegalArgumentException(format("Wrong type \"%s\"", type));
                    }
                }));
    }

    static Validator tiles() {
        return object(
                objectPropertiesRequired(Map.of(
                        "noTiles", arrayItems(integer(
                                minimum(1)
                        )),
                        "ranges", arrayItems(rangeDef())
                ), List.of(
                        "noTiles"
                ))
        );
    }
}
