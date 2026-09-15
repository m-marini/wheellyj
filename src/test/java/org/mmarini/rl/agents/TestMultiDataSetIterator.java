/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.rl.agents;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static java.lang.Math.min;

public class TestMultiDataSetIterator implements MultiDataSetIterator {
    private static final Logger logger = LoggerFactory.getLogger(TestMultiDataSetIterator.class);
    private final INDArray[] labels;
    private final INDArray[] features;
    private final int batchSize;
    private int cursor;

    public TestMultiDataSetIterator(INDArray[] features, INDArray[] labels, int batchSize) {
        this.features = features;
        this.labels = labels;
        this.batchSize = batchSize;
    }

    @Override
    public MultiDataSet next(int num) {
        long n = features[0].size(0);
        long m = min(num, n - cursor);
        if (m <= 0) {
            return null;
        }
        logger.atInfo().log("next({}) from {}", m, cursor);
        INDArrayIndex interval = NDArrayIndex.interval(cursor, cursor + m);
        INDArray[] features1 = Arrays.stream(features)
                .map(v -> v.get(interval, NDArrayIndex.all()))
                .toArray(INDArray[]::new);
        INDArray[] labels1 = Arrays.stream(labels)
                .map(v -> v.get(interval, NDArrayIndex.all()))
                .toArray(INDArray[]::new);
        cursor += (int) m;
        return new org.nd4j.linalg.dataset.MultiDataSet(features1, labels1);
    }

    @Override
    public MultiDataSetPreProcessor getPreProcessor() {
        return null;
    }

    @Override
    public void setPreProcessor(MultiDataSetPreProcessor preProcessor) {

    }

    @Override
    public boolean resetSupported() {
        return true;
    }

    @Override
    public boolean asyncSupported() {
        return false;
    }

    @Override
    public void reset() {
        this.cursor = 0;
    }

    @Override
    public boolean hasNext() {
        return cursor < features[0].size(0);
    }

    @Override
    public MultiDataSet next() {
        return next(batchSize);
    }
}
