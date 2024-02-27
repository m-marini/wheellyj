/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.rl.nets;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;

import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Contains the values, gradient and masks of each layer output
 */
public interface TDNetworkState {
    Predicate<String> PARAMETERS_PREDICATE = Pattern.compile("^.*\\.(weights|bias)$").asMatchPredicate();
    Predicate<String> VALUES_PREDICATE = Pattern.compile("^.*\\.(values)$").asMatchPredicate();
    Predicate<String> GRADIENTS_PREDICATE = Pattern.compile("^.*\\.(grads)$").asMatchPredicate();

    /**
     * Returns the state with added variables values
     *
     * @param key    the variables key
     * @param values the values to add
     */
    TDNetworkState add(String key, INDArray values);

    /**
     * Returns the state with added gradients values
     *
     * @param layer  the layer key
     * @param values the values
     */
    default TDNetworkState addGradients(String layer, INDArray values) {
        return add(layer + ".grads", values);
    }

    /**
     * Returns the duplication of the state
     */
    TDNetworkState dup();

    /**
     * Returns the variables filterer by key
     *
     * @param predicate the filter predicate
     */
    Map<String, INDArray> filterKeys(Predicate<String> predicate);

    /**
     * Returns the copy of variables filterer by key
     *
     * @param predicate the filter predicate
     */
    Map<String, INDArray> filterKeysAndDup(Predicate<String> predicate);

    /**
     * Returns the variables
     *
     * @param key the variables key
     */
    INDArray get(String key);

    /**
     * Returns the bias of the layer
     *
     * @param layer the layer key
     */
    default INDArray getBias(String layer) {
        return get(layer + ".bias");
    }

    /**
     * Returns the bias trace of the layer
     *
     * @param layer the layer key
     */
    default INDArray getBiasTrace(String layer) {
        return get(layer + ".bias.trace");
    }

    /**
     * Returns the gradients of the layer
     *
     * @param layer the layer key
     */
    default INDArray getGradients(String layer) {
        return get(layer + ".grads");
    }

    /**
     * Returns the mask of the layer
     *
     * @param layer the layer key
     */
    default INDArray getMask(String layer) {
        return get(layer + ".mask");
    }

    /**
     * Returns the size of the layer
     *
     * @param layer the layer name
     */
    long getSize(String layer);

    /**
     * Returns the values of layer
     *
     * @param layer the layer key
     */
    default INDArray getValues(String layer) {
        return get(layer + ".values");
    }

    /**
     * Returns the weights of the layer
     *
     * @param layer the layer key
     */
    default INDArray getWeights(String layer) {
        return get(layer + ".weights");
    }

    /**
     * Returns the weights trace of the layer
     *
     * @param layer the layer key
     */
    default INDArray getWeightsTrace(String layer) {
        return get(layer + ".weights.trace");
    }

    /**
     * Returns the copy of all gradients
     */
    default Map<String, INDArray> gradients() {
        return filterKeys(GRADIENTS_PREDICATE);
    }

    /**
     * Returns the parameters of the network
     */
    default Map<String, INDArray> parameters() {
        return filterKeysAndDup(PARAMETERS_PREDICATE);
    }

    /**
     * Returns the state with variables changed
     *
     * @param key    the variables key
     * @param values the values
     */
    TDNetworkState put(String key, INDArray values);

    /**
     * Returns the state with bias of the layer changed
     *
     * @param layer the layer key
     * @param bias  the bias
     */
    default TDNetworkState putBias(String layer, INDArray bias) {
        return put(layer + ".bias", bias);
    }

    /**
     * Returns the state with bias trace of the layer changed
     *
     * @param layer the layer key
     * @param trace the bias trace
     */
    default TDNetworkState putBiasTrace(String layer, INDArray trace) {
        return put(layer + ".bias.trace", trace);
    }

    /**
     * Returns the state with mask of the layer changed
     *
     * @param layer the layer key
     * @param mask  the mask
     */
    default TDNetworkState putMask(String layer, INDArray mask) {
        return put(layer + ".mask", mask);
    }

    /**
     * Returns the state with value of layer changed
     *
     * @param layer  the layer key
     * @param values the values
     */
    default TDNetworkState putValues(String layer, INDArray values) {
        return put(layer + ".values", values);
    }

    /**
     * Returns the state with weights of the layer changed
     *
     * @param layer   the layer key
     * @param weights the weights
     */
    default TDNetworkState putWeights(String layer, INDArray weights) {
        return put(layer + ".weights", weights);
    }

    /**
     * Returns the state with weights trace of the layer changed
     *
     * @param layer the layer key
     * @param trace the weights trace
     */
    default TDNetworkState putWeightsTrace(String layer, INDArray trace) {
        return put(layer + ".weights.trace", trace);
    }

    /**
     * Returns the randomizer
     */
    Random random();

    /**
     * Returns the state without the filtered key variables
     *
     * @param filter the key filter
     */
    TDNetworkState remove(Predicate<String> filter);

    /**
     * Returns the state without the gradients
     */
    default TDNetworkState removeGradients() {
        return remove(GRADIENTS_PREDICATE);
    }

    /**
     * Returns the state with set sizes
     *
     * @param sizes the sizes by layer
     */
    TDNetworkState setSizes(Map<String, Long> sizes);

    /**
     * Returns a copy of values the values
     */
    default Map<String, INDArray> values() {
        return filterKeys(VALUES_PREDICATE);
    }
}
