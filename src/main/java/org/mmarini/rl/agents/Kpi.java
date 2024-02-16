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

package org.mmarini.rl.agents;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.Arrays;
import java.util.StringJoiner;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class Kpi {
    static Kpi create(INDArray data) {
        requireNonNull(data);
        long[] shape = data.shape();
        if (!(shape.length == 2)) {
            throw new IllegalArgumentException(format("Data rank must be 2 (%s)", Arrays.toString(shape)));
        }
        if (!(shape[1] == 1)) {
            throw new IllegalArgumentException(format("Data shape must be [n x 1] (%s)", Arrays.toString(shape)));
        }
        int numSamples = (int) shape[0];
        float mean = data.meanNumber().floatValue();
        float std = data.stdNumber(false).floatValue();
        float[] linPoly = linearPolynomial(data);
        float linRms = rms(data, linearRegression(numSamples, linPoly));
        float minValue = data.minNumber().floatValue();
        float maxValue = data.maxNumber().floatValue();
        float[] expPoly = expPolynomial(data);
        float expRms = (expPoly != null)
                ? rms(data, expRegression(numSamples, expPoly))
                : 0;

        return new Kpi(numSamples, mean, std, minValue, maxValue, linPoly, linRms, expPoly, expRms);

    }

    public static float[] expPolynomial(INDArray y) {
        return (y.minNumber().floatValue() > 0)
                ? linearPolynomial(Transforms.log(y))
                : null;
    }

    public static INDArray expRegression(int numSamples, float[] expPoly) {
        return Transforms.exp(linearRegression(numSamples, expPoly));
    }

    public static float[] linearPolynomial(INDArray data) {
        long n = data.shape()[0];
        return linearPolynomial(Nd4j.linspace(0, n - 1, n).reshape(n, 1), data);
    }

    public static float[] linearPolynomial(INDArray x, INDArray y) {
        float xm = x.meanNumber().floatValue();
        float ym = y.meanNumber().floatValue();
        INDArray dx = x.sub(xm);
        INDArray dy = y.sub(ym);
        float sxx = x.var(false).getFloat(0);
        //dx.mul(dx).mean().getFloat(0, 0);
        float sxy = dx.mul(dy).meanNumber().floatValue();
        float c1 = sxy / sxx;
        float c0 = ym - c1 * xm;
        return new float[]{c0, c1};
    }

    public static INDArray linearRegression(int numSamples, float[] linPoly) {
        return linearRegression(Nd4j.linspace(0, numSamples - 1, numSamples).reshape(numSamples, 1), linPoly);
    }

    public static INDArray linearRegression(INDArray x, float[] linPoly) {
        return x.mul(linPoly[1]).addi(linPoly[0]);
    }

    public static float rms(INDArray x, INDArray y) {
        return Transforms.sqrt(
                Transforms.pow(x.sub(y), 2).mean()
        ).getFloat(0, 0);
    }

    public final float[] expPoly;
    public final float expRms;
    public final float[] linPoly;
    public final float linRms;
    public final float max;
    public final float mean;
    public final float min;
    public final int numSamples;
    public final float std;


    /**
     * Create a Kpi
     *
     * @param numSamples the number of samples
     * @param mean       the mean
     * @param std        the standard deviation
     * @param min        the minimum value
     * @param max        maximum value
     * @param linPoly    the linear regression polynomial coefficients
     * @param linRms     the root-mean-square error of linear regression
     * @param expPoly    the exponential regression polynomial coefficients
     * @param expRms     the root-mean-square error of exponential regression
     */
    public Kpi(int numSamples, float mean, float std, float min, float max, float[] linPoly, float linRms, float[] expPoly, float expRms) {
        this.numSamples = numSamples;
        this.mean = mean;
        this.std = std;
        this.min = min;
        this.max = max;
        this.linPoly = requireNonNull(linPoly);
        this.linRms = linRms;
        this.expPoly = expPoly;
        this.expRms = expRms;
    }

    public float[] getExpPoly() {
        return expPoly;
    }

    public float getExpRms() {
        return expRms;
    }

    public float[] getLinPoly() {
        return linPoly;
    }

    public float getLinRms() {
        return linRms;
    }

    public float getMean() {
        return mean;
    }

    public int getNumSamples() {
        return numSamples;
    }

    public float getStd() {
        return std;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Kpi.class.getSimpleName() + "[", "]")
                .add("numSamples=" + numSamples)
                .add("mean=" + mean)
                .add("std=" + std)
                .add("linPoly=" + Arrays.toString(linPoly))
                .add("linRms=" + linRms)
                .add("expPoly=" + Arrays.toString(expPoly))
                .add("expRms=" + expRms)
                .toString();
    }
}
