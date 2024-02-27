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

package org.mmarini.rl.nets;

import org.mmarini.Tuple2;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * Contains the values, gradient and masks of each layer output
 */
public class TDNetworkStateImpl implements TDNetworkState {

    /**
     * Returns the default network state
     */
    public static TDNetworkStateImpl create() {
        return create(Nd4j.getRandom());
    }

    /**
     * Returns the default network state
     *
     * @param random the randomizer
     */
    public static TDNetworkStateImpl create(Random random) {
        return new TDNetworkStateImpl(new HashMap<>(), random, Map.of());
    }

    private final Map<String, INDArray> variables;
    private final Map<String, Long> sizes;
    private final Random random;

    /**
     * Creates the network state
     *
     * @param variables the variables map
     * @param random    the randomizer
     * @param sizes     the sizes of network layer
     */
    protected TDNetworkStateImpl(Map<String, INDArray> variables, Random random, Map<String, Long> sizes) {
        this.variables = variables;
        this.random = requireNonNull(random);
        this.sizes = requireNonNull(sizes);
    }

    @Override
    public TDNetworkState add(String key, INDArray values) {
        this.variables.compute(key,
                (ignored, oldValue) -> oldValue == null ? values : oldValue.add(values));
        return this;
    }

    @Override
    public TDNetworkState dup() {
        Map<String, INDArray> newVars = new HashMap<>(variables);
        Random newRnd = Nd4j.getRandomFactory().getNewRandomInstance(random.getSeed());
        HashMap<String, Long> newSizes = new HashMap<>(sizes);
        return new TDNetworkStateImpl(newVars, newRnd, newSizes);
    }

    @Override
    public Map<String, INDArray> filterKeys(Predicate<String> predicate) {
        return Tuple2.stream(variables)
                .filter(t -> predicate.test(t.getV1()))
                .collect(Tuple2.toMap());
    }

    @Override
    public Map<String, INDArray> filterKeysAndDup(Predicate<String> predicate) {
        return Tuple2.stream(variables)
                .filter(t -> predicate.test(t.getV1()))
                .map(Tuple2.map2(INDArray::dup))
                .collect(Tuple2.toMap());
    }

    @Override
    public INDArray get(String key) {
        return variables.get(key);
    }

    @Override
    public long getSize(String layer) {
        return sizes.getOrDefault(layer, 0L);
    }

    @Override
    public TDNetworkState put(String key, INDArray values) {
        this.variables.put(key, values);
        return this;
    }

    @Override
    public Random random() {
        return random;
    }

    @Override
    public TDNetworkState remove(Predicate<String> filter) {
        variables.keySet().stream()
                .filter(filter)
                .toList()
                .forEach(variables::remove);
        return this;
    }

    @Override
    public TDNetworkState setSizes(Map<String, Long> sizes) {
        return new TDNetworkStateImpl(variables, random, sizes);
    }
}
