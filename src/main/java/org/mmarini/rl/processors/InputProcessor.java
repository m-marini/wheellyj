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

package org.mmarini.rl.processors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * The processor processes the signals to produce new coding signals
 */
public record InputProcessor(UnaryOperator<Map<String, Signal>> encode,
                             Map<String, SignalSpec> spec,
                             JsonNode json) implements UnaryOperator<Map<String, Signal>> {

    public static InputProcessor concat(List<InputProcessor> list) {
        requireNonNull(list);
        if (list.isEmpty()) {
            throw new IllegalArgumentException("list must not be empty");
        }
        UnaryOperator<Map<String, Signal>> encode1 = x -> {
            Map<String, Signal> in = x;
            for (InputProcessor e : list) {
                in = e.apply(in);
            }
            return in;
        };
        ArrayNode json = Utils.objectMapper.createArrayNode();
        for (InputProcessor processor : list) {
            json.add(processor.json());
        }
        Map<String, SignalSpec> spec1 = list.getLast().spec();

        return new InputProcessor(encode1, spec1, json);
    }

    /**
     * Returns the processor that concatenate the processor list from specification
     *
     * @param root    the root document
     * @param locator the processor array locator
     * @param spec    the input specification
     */
    public static InputProcessor create(JsonNode root, Locator locator, Map<String, SignalSpec> spec) {
        List<InputProcessor> processors = new ArrayList<>();
        Class<?>[] classes = new Class[]{Map.class};
        List<Locator> processorLocators = locator.elements(root).toList();
        for (Locator loc : processorLocators) {
            InputProcessor processor = Utils.createObject(root, loc, new Object[]{spec}, classes);
            spec = processor.spec();
            processors.add(processor);
        }
        return InputProcessor.concat(processors);
    }

    /**
     * Validates the names for exisiting input
     *
     * @param spec the input specification
     * @param name the name
     */
    public static void validateAlreadyDefinedName(Map<String, SignalSpec> spec, String name) {
        // Verify for output name overlap
        if (spec.containsKey(name)) {
            throw new IllegalArgumentException(format("Signal \"%s\" already defined in signal specification",
                    name));
        }
    }

    /**
     * Validates the names for exisiting input
     *
     * @param spec  the input specification
     * @param names the names
     */
    public static void validateExistingNames(Map<String, SignalSpec> spec, Collection<String> names) {
        validateExistingNames(spec, names.toArray(String[]::new));
    }

    /**
     * Validates the names for exisiting input
     *
     * @param inSpec the input specification
     * @param names  the names
     */
    public static void validateExistingNames(Map<String, SignalSpec> inSpec, String... names) {
        // Verify for missing input names
        List<String> missingNames = Arrays.stream(names)
                .filter(Predicate.not(inSpec::containsKey))
                .map(key -> "\"" + key + "\"")
                .toList();
        if (!missingNames.isEmpty()) {
            throw new IllegalArgumentException(format("Missing signals %s in signal specification",
                    String.join(", ", missingNames)));
        }
    }

    /**
     * Validates inputs for same shape
     *
     * @param spec  inpus specification
     * @param names the names
     */
    static void validateSameShape(Map<String, SignalSpec> spec, List<String> names) {
        long[] shape = spec.get(names.getFirst()).shape();
        List<String> wrongShapes = names.stream()
                .filter(name -> !Arrays.equals(shape, spec.get(name).shape()))
                .map(name -> format("\"%s\"=%s",
                        name,
                        Arrays.toString(spec.get(name).shape())))
                .toList();
        if (!wrongShapes.isEmpty()) {
            throw new IllegalArgumentException(format("Signal shapes must be %s (%s)",
                    Arrays.toString(shape),
                    String.join(", ", wrongShapes)));
        }
    }

    /**
     * Validates inputs for same shape
     *
     * @param spec  inpus specification
     * @param clazz the signal specificatin class
     * @param names the names
     */
    static void validateTypes(Map<String, SignalSpec> spec, Class<? extends SignalSpec> clazz, List<String> names) {
        validateTypes(spec, clazz, names.stream());
    }

    /**
     * Validates inputs for same shape
     *
     * @param spec  inpus specification
     * @param clazz the signal specificatin class
     * @param names the names
     */
    static void validateTypes(Map<String, SignalSpec> spec, Class<? extends SignalSpec> clazz, String... names) {
        // Verify for wrong input types
        validateTypes(spec, clazz, Arrays.stream(names));
    }

    /**
     * Validates inputs for same shape
     *
     * @param spec  inpus specification
     * @param clazz the signal specificatin class
     * @param names the names
     */
    static void validateTypes(Map<String, SignalSpec> spec, Class<? extends SignalSpec> clazz, Stream<String> names) {
        // Verify for wrong input types
        List<String> wrongTypes = names.filter(name ->
                        !(spec.get(name).getClass().equals(clazz)))
                .map(name ->
                        format("\"%s\"=%s",
                                name,
                                spec.get(name).getClass().getSimpleName()))
                .toList();
        if (!wrongTypes.isEmpty()) {
            throw new IllegalArgumentException(format("Signal spec must be IntSignalSpec (%s)",
                    String.join(", ", wrongTypes)));
        }
    }

    /**
     * Creates the tile processor
     *
     * @param encode the encode function
     * @param spec   the output spec
     * @param json   the json node
     */
    public InputProcessor(UnaryOperator<Map<String, Signal>> encode, Map<String, SignalSpec> spec, JsonNode json) {
        this.spec = requireNonNull(spec);
        this.encode = requireNonNull(encode);
        this.json = json;
    }

    @Override
    public Map<String, Signal> apply(Map<String, Signal> signals) {
        return encode.apply(signals);
    }

}
