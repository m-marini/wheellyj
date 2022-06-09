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

import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.wheelly.model.MapStatus;
import org.mmarini.wheelly.model.WheellyStatus;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.nd4j.linalg.factory.Nd4j.*;
import static org.nd4j.linalg.ops.transforms.Transforms.round;

public interface FunctionBuilder {

    /**
     * Returns the cumulative distribution function for the actions
     *
     * @param x the distribution function
     */
    static INDArray cdf(INDArray x) {
        long n = x.length();
        INDArray cdf = x.dup();
        for (long i = 1; i < n; i++) {
            double v = cdf.getDouble(i - 1) + cdf.getDouble(i);
            cdf.putScalar(i, v);
        }
        return cdf;
    }

    /**
     * Returns random integer for a cdf
     *
     * @param x the cumulative distribution function
     */
    static ToIntFunction<Random> cdfRandomInt(INDArray x) {
        return random -> {
            long n = x.length();
            double y = random.nextDouble();
            for (int i = 0; i < n - 1; i++) {
                if (y < x.getDouble(i)) {
                    return i;
                }
            }
            return (int) (n - 1);
        };
    }

    /**
     * Returns the function the map the input value to a clip value in (min, max) range
     *
     * @param range range values (2xn) row(0) = min, row(1) = max
     */
    static UnaryOperator<INDArray> clip(INDArray range) {
        INDArray min = range.getRow(0);
        INDArray max = range.getRow(1);
        return x -> Transforms.min(Transforms.max(x, min, true), max, false);
    }

    /**
     * Returns the denormalize function of row vector after clipping
     * The function returns ranges for (-1, 1) ranges
     *
     * @param ranges the range of transformation the first row contains minimum values
     *               and the second row contains the maximum values
     */
    static UnaryOperator<INDArray> clipAndDenormalize(INDArray ranges) {
        INDArray fromRanges = vstack(ones(1, ranges.size(1)).negi(), ones(1, ranges.size(1)));
        return clipAndTransform(fromRanges, ranges);
    }

    static UnaryOperator<INDArray> clipAndDenormalize(double min, double max) {
        double m = (max - min) / 2;
        double q = (min + max) / 2;
        return (INDArray x) ->
                Transforms.min(Transforms.max(x, min), max).muli(m).addi(q);
    }

    static UnaryOperator<INDArray> clipAndNormalize(double min, double max) {
        double m = 2 / (max - min);
        double q = -min * m - 1;

        return (INDArray x) ->
                Transforms.min(Transforms.max(x, min), max).muli(m).addi(q);
    }

    /**
     * Returns the normalizer function of row vector after clipping
     * The function returns ranges -1, 1 for defined ranges
     *
     * @param ranges the range of transformation (2xn) the first row contains minimum values
     *               and the second row contains the maximum values
     */
    static UnaryOperator<INDArray> clipAndNormalize(INDArray ranges) {
        INDArray toRanges = vstack(ones(1, ranges.size(1)).negi(), ones(1, ranges.size(1)));
        return clipAndTransform(ranges, toRanges);
    }

    /**
     * Returns the normalizer function of row vector after clipping
     * The function returns ranges -1, 1 for defined ranges
     *
     * @param fromRanges the range of inputs (2xn) the first row contains minimum values
     *                   and the second row contains the maximum values
     * @param toRanges   the range of outputs (2xn) the first row contains minimum values
     *                   and the second row contains the maximum values
     */
    static UnaryOperator<INDArray> clipAndTransform(INDArray fromRanges, INDArray toRanges) {
        INDArray m = toRanges.getRow(1).sub(toRanges.getRow(0)).divi(fromRanges.getRow(1).sub(fromRanges.getRow(0)));
        INDArray q = m.mul(fromRanges.getRow(0)).subi(toRanges.getRow(0)).negi();
        UnaryOperator<INDArray> cl = clip(fromRanges);
        return x -> cl.apply(x).muli(m).addi(q);
    }

    /**
     * Returns the function that clip, denormalize and center the values
     *
     * @param range the output range (2xn)
     */
    static UnaryOperator<INDArray> clipDenormalizeAndCenter(INDArray range) {
        UnaryOperator<INDArray> clip = clipAndDenormalize(range);
        return (x) -> {
            INDArray pr = clip.apply(x);
            INDArray mean = pr.mean();
            return pr.subi(mean);
        };
    }

    static double decay(double x) {
        //return exp(-x);
        return max(1 - x, 0);
    }

    static double decay(double from, double to, double x) {
        double alpha = decay(x);
        return from * alpha + to * (1 - alpha);
    }

    static INDArray decay(INDArray from, INDArray to, double x) {
        double alpha = decay(x);
        return from.mul(alpha).addi(to.mul(1 - alpha));
    }

    /**
     * Returns the function that map a scalar value in (min-max) range
     * to a vector (1xn) with the features values.
     * Only the feature matching the input value in the bucket range is set to value 1
     *
     * @param noValues the numbers of features in the output vector
     * @param range    the vector 2x1 with the range (min, max)
     */
    static UnaryOperator<INDArray> linearDecode(int noValues, INDArray range) {
        double min = range.getDouble(0);
        double max = range.getDouble(1);
        double scale = (noValues - 1) / (max - min);
        UnaryOperator<INDArray> fClip = clip(range);
        return (INDArray x) -> {
            INDArray fc = fClip.apply(x);
            long actionIndex = round(fc.subi(min).muli(scale)).getLong(0);
            INDArray result = zeros(1, noValues);
            result.getScalar(actionIndex).assign(1);
            return result;
        };
    }

    /**
     * Returns the function that linear maps the integer value in (0, n-1) range
     * to a scalar value in the (min, max) range.
     *
     * @param noValues numbers of values
     * @param range    the vector 1x2 with the range (min, max)
     */
    static IntFunction<INDArray> linearEncode(int noValues, INDArray range) {
        double min = range.getDouble(0);
        double max = range.getDouble(1);
        double scale = (max - min) / (noValues - 1);
        INDArray[] actions = IntStream.range(0, noValues)
                .mapToObj(i -> ones(1).muli(i * scale + min))
                .toArray(INDArray[]::new);
        return (int action) -> actions[min(max(action, 0), noValues - 1)];
    }

    static ToIntFunction<Random> randomInt(INDArray x) {
        return cdfRandomInt(cdf(x));
    }

   static double testReward(Timed<MapStatus> s0, Timed<MapStatus> s1) {
        WheellyStatus w = s1.value().getWheelly();
        if (w.isBlocked()
                || w.getCannotMoveForward()
                || w.getCannotMoveBackward()
                || (w.getSampleDistance() > 0 && w.getSampleDistance() < 0.5)) {
            return -4;
        } else if (w.getSampleDistance() >= 0.5 && w.getSampleDistance() < 0.7) {
            return 1;
        } else if (w.getRightSpeed() != 0 || w.getLeftSpeed() != 0 || w.getSensorRelativeDeg() != 0) {
            /*
             * Avoid movement
             */
            return -2;
        } else {
            return -1;
        }
    }
}
