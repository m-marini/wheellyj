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
import org.mmarini.yaml.schema.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.mmarini.Utils.zipWithIndex;

public interface Actor {

    /**
     * Returns the list of actors
     *
     * @param root    the root document
     * @param locator the actors list locator
     */
    static List<Actor> fromArray(JsonNode root, Locator locator) {
        return zipWithIndex(locator.elements(root).collect(Collectors.toList()))
                .map(t -> fromJson(root, t._2, t._1))
                .collect(Collectors.toList());
    }

    /**
     * Returns the actor
     *
     * @param root      the root document
     * @param locator   the actor locator
     * @param dimension the dimension index of actor
     */
    static Actor fromJson(JsonNode root, Locator locator, int dimension) {
        String type = locator.path("type").getNode(root).asText();
        if ("DiscreteActor".equals(type)) {
            return DiscreteActor.fromJson(root, locator, dimension);
        }
        throw new IllegalArgumentException(format("Wrong type \"%s\" at %s", type, locator.path("type")));
    }

    /**
     * Returns the action chosen by the actor
     *
     * @param outputs the network outputs
     * @param random  the random generator
     */
    INDArray chooseAction(INDArray[] outputs, Random random);

    /**
     * Returns the actor labels
     *
     * @param outputs the outputs
     * @param actions the actions
     * @param delta   the td error
     * @param alpha   alpha parameter
     * @param dt      time interval
     */
    Map<String, Object> computeLabels(INDArray[] outputs, INDArray actions, INDArray delta, INDArray alpha, double dt);

    /**
     * Returns the dimension index of action agent
     */
    int getDimension();

    int getNumOutputs();
}
