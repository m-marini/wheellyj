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

import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;

import static java.lang.Math.sqrt;
import static java.lang.String.format;
import static org.mmarini.wheelly.engines.deepl.FunctionBuilder.*;
import static org.nd4j.linalg.factory.Nd4j.scalar;
import static org.nd4j.linalg.ops.transforms.Transforms.pow;
import static org.nd4j.linalg.ops.transforms.Transforms.softmax;

/**
 * Discrete actor generates discrete output action value by computing the probability for each finite value
 * and than randomly selecting a value based on the probabilities.
 * The neural network outputs encodes the normalized probabilities
 * The denormalizer function is used to denormalize the nn outputs to produce the preferences (input for softmax function).
 * The denormalizer function is the inverse denormalize function used to produce the output errors.
 * The decode function converts the integer action to a output value
 * The encode function converts ... // TODO
 */
public class DiscreteActor implements Actor {

    public static final double DELTAHRMS_EPSILON = 1e-3;
    public static final double DEFAULT_EPSILON = 0.1;

    public static DiscreteActor fromJson(JsonNode root, Locator locator, int dimension) {
        int noValues = locator.path("noValues").getNode(root).asInt();
        INDArray outputRange = Yaml.range(locator.path("outputRange").getNode(root));
        INDArray preferenceRange = Yaml.range(locator.path("preferenceRange").getNode(root));
        INDArray broadPrefRange = preferenceRange.broadcast(2, noValues);
        UnaryOperator<INDArray> denormalize = clipDenormalizeAndCenter(broadPrefRange);
        UnaryOperator<INDArray> normalize = clipAndNormalize(broadPrefRange);
        IntFunction<INDArray> decode = linearEncode(noValues, outputRange);
        UnaryOperator<INDArray> encode = linearDecode(noValues, outputRange);
        double alphaDecay = locator.path("alphaDecay").getNode(root).asDouble();
        double epsilonH = locator.path("epsilon").getNode(root).asDouble(DEFAULT_EPSILON);
        return new DiscreteActor(dimension, noValues, alphaDecay, epsilonH, denormalize, normalize, decode, encode);
    }

    private final int dimension;
    private final UnaryOperator<INDArray> denormalize;
    private final UnaryOperator<INDArray> normalize;
    private final IntFunction<INDArray> decode;
    private final UnaryOperator<INDArray> encode;
    private final int numOutputs;
    private final double epsilonH;
    private final double alphaDecay;

    protected DiscreteActor(int dimension, int numOutputs,
                            double alphaDecay, double epsilonH, UnaryOperator<INDArray> denormalize, UnaryOperator<INDArray> normalize,
                            IntFunction<INDArray> decode, UnaryOperator<INDArray> encode) {
        this.dimension = dimension;
        this.numOutputs = numOutputs;
        this.alphaDecay = alphaDecay;
        this.epsilonH = epsilonH;
        this.denormalize = denormalize;
        this.normalize = normalize;
        this.decode = decode;
        this.encode = encode;
    }

    @Override
    public INDArray chooseAction(INDArray[] netOutputs, Random random) {
        INDArray pi = softmax(preferences(netOutputs));
        int actionIndex = randomInt(pi).applyAsInt(random);
        INDArray action = decode.apply(actionIndex);
        return action;
    }

    @Override
    public Map<String, Object> computeLabels(INDArray[] outputs, INDArray actions, INDArray delta, INDArray alpha, double dt) {
        INDArray h = preferences(outputs);
        INDArray pi = softmax(h);

        INDArray features = encode.apply(actions.getScalar(dimension));

        INDArray z = features.subi(pi);
        // deltaH = z * delta * alpha
        INDArray alpha0 = alpha.getScalar(getDimension());
        INDArray deltaH = z.mul(delta).muli(alpha0);

        // hStar = h + deltaH
        INDArray hStar = h.add(deltaH);
        INDArray actorLabels = normalize.apply(hStar);
        INDArray dhSqr = pow(deltaH, 2);
        INDArray j = dhSqr.sum();
        double deltaHRMS = sqrt(dhSqr.mean().getDouble(0));
        INDArray alphaStar = alpha0;
        if (deltaHRMS > DELTAHRMS_EPSILON) {
            INDArray alpha1 = scalar(epsilonH / deltaHRMS);
            alphaStar = decay(alphaStar, alpha1, dt / alphaDecay);
        }

        return Map.of(
                format("pi(%d)", dimension), pi,
                format("h(%d)", dimension), h,
                format("J(%d)", dimension), j,
                format("deltaH(%d)", dimension), deltaH,
                format("h*(%d)", dimension), hStar,
                format("labels(%d)", dimension), actorLabels,
                format("alpha*(%d)", dimension), alphaStar
        );
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public int getNumOutputs() {
        return numOutputs;
    }

    private INDArray preferences(INDArray[] outputs) {
        INDArray outs = outputs[dimension + 1];
        INDArray result = denormalize.apply(outs);
        return result;
    }
}
