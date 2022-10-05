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

package org.mmarini.wheelly.agents;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * The data collector consumer accumulates data and returns the kpi of data
 */
public class CSVSubscriber implements Subscriber<INDArray> {
    private static final Logger logger = LoggerFactory.getLogger(CSVSubscriber.class);
    private static final int BUFFER_SIZE = 1000;
    private final File file;
    private INDArray buffer;
    private int size;
    private Subscription subscription;

    /**
     *
     */
    public CSVSubscriber(File file) {
        this.file = file;
    }

    private void flush() {
        if (size > 0) {
            try {
                Serde.toCsv(file, buffer.get(NDArrayIndex.interval(0, size), NDArrayIndex.all()), true);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
        size = 0;
    }

    @Override
    public void onComplete() {
        flush();
    }

    @Override
    public void onError(Throwable throwable) {
        flush();
    }

    @Override
    public void onNext(INDArray indArray) {
        if (buffer == null) {
            long length = indArray.length();
            buffer = Nd4j.zeros(BUFFER_SIZE, length);
        }
        buffer.getRow(size++).assign(Nd4j.toFlattened(indArray));
        if (size >= buffer.shape()[0]) {
            flush();
        }
        subscription.request(1);
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;
        file.delete();
        subscription.request(1);
    }
}

