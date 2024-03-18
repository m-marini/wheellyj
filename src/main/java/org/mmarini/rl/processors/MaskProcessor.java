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

package org.mmarini.rl.processors;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.NotImplementedException;
import org.mmarini.rl.envs.*;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.String.format;
import static org.mmarini.rl.processors.InputProcessor.validateAlreadyDefinedName;
import static org.mmarini.rl.processors.InputProcessor.validateExistingNames;

/**
 * The processor creates the output masked from mask signal
 */
public interface MaskProcessor {
    /**
     * Returns the input processor from document
     *
     * @param root    the root document
     * @param locator the spec locator
     * @param spec    the input spec
     */
    static InputProcessor create(JsonNode root, Locator locator, Map<String, SignalSpec> spec) {
        String outName = locator.path("name").getNode(root).asText();
        String inputName = locator.path("input").getNode(root).asText();
        String maskName = locator.path("mask").getNode(root).asText();
        validate(spec, outName, inputName, maskName);
        return new InputProcessor(
                createSignalEncoder(outName, inputName, maskName),
                createSpec(spec, outName, inputName),
                locator.getNode(root)
        );
    }

    /**
     * Returns the full mask matching the output shape
     *
     * @param mask      the input mask
     * @param fullShape the full mask shape
     */
    static INDArray createFullMask(INDArray mask, long[] fullShape) {
        INDArray out = Nd4j.zeros(fullShape);
        long[] indices = new long[fullShape.length];
        try (INDArray fMask = mask.neq(0).castTo(DataType.FLOAT)) {
            traverse(out, fMask, indices, 0);
        }
        return out;
    }

    /**
     * Returns the signal encoder
     *
     * @param outName   the output name
     * @param inputName the input name
     * @param maskName  the mask name
     */
    static UnaryOperator<Map<String, Signal>> createSignalEncoder(String outName, String inputName, String maskName) {
        return in -> {
            Map<String, Signal> result = new HashMap<>(in);
            INDArray mask = in.get(maskName).toINDArray();
            INDArray x = in.get(inputName).toINDArray();
            try (INDArray fullMask = createFullMask(mask, x.shape())) {
                INDArray y = x.mul(fullMask);
                result.put(outName, new ArraySignal(y));
            }
            return result;
        };
    }

    /**
     * Returns the signal specification of processor result
     *
     * @param spec      the input specification
     * @param outName   the output name
     * @param inputName the input name
     */
    static Map<String, SignalSpec> createSpec(Map<String, SignalSpec> spec, String outName, String inputName) {
        SignalSpec inputSpec = spec.get(inputName);
        Map<String, SignalSpec> result = new HashMap<>(spec);
        SignalSpec outSpec = switch (inputSpec) {
            case IntSignalSpec iSpec -> iSpec;
            case FloatSignalSpec fSpec -> new FloatSignalSpec(fSpec.shape(),
                    min(0, fSpec.minValue()),
                    max(0, fSpec.maxValue()));
            default -> throw new NotImplementedException();
        };
        result.put(outName, outSpec);
        return result;
    }

    /**
     * Traverse the full mask setting the values from input mask
     *
     * @param out     the full mask
     * @param mask    the input mask
     * @param indices the indices of full mask array
     * @param dim     the current traversing dimension
     */
    private static void traverse(INDArray out, INDArray mask, long[] indices, int dim) {
        if (dim >= out.rank()) {
            long[] maskIndices = Arrays.copyOf(indices, 2);
            out.putScalar(indices, mask.getFloat(maskIndices));
        } else {
            for (int i = 0; i < out.size(dim); i++) {
                indices[dim] = i;
                traverse(out, mask, indices, dim + 1);
            }
        }
    }

    /**
     * Validate the processor arguments
     *
     * @param spec      the input spec
     * @param outName   the output name
     * @param inputName the input name
     * @param maskName  the mask name
     */
    static void validate(Map<String, SignalSpec> spec, String outName, String inputName, String maskName) {
        validateAlreadyDefinedName(spec, outName);
        validateExistingNames(spec, inputName, maskName);
        long[] inputShape = spec.get(inputName).shape();
        long[] maskShape = spec.get(maskName).shape();
        // Verify mask and input ranks
        if (maskShape.length > inputShape.length) {
            throw new IllegalArgumentException(format(
                    "Rank of signal \"%s\" (%d) must be lower or equal than rank of signal \"%s\" (%d)",
                    maskName, maskShape.length,
                    inputName, inputShape.length));
        }
        // Verify mask and input shape
        long[] maskShape1 = Arrays.copyOf(inputShape, maskShape.length);
        if (!Arrays.equals(maskShape1, maskShape)) {
            throw new IllegalArgumentException(format(
                    "Shape of signal \"%s\" %s must be %s",
                    maskName, Arrays.toString(maskShape),
                    Arrays.toString(maskShape1)));
        }
    }
}
