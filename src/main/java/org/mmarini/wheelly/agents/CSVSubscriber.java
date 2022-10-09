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
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * The data collector consumer accumulates data and returns the kpi of data
 */
public class CSVSubscriber implements Subscriber<INDArray> {
    private static final Logger logger = LoggerFactory.getLogger(CSVSubscriber.class);
    private static final int BUFFER_SIZE = 1000;

    /**
     * Returns the csv subscriber that writes csv file
     * Creates the shape file [file]_shape.csv
     * and the data file [file]_data.csv
     *
     * @param file the base file name
     */
    public static CSVSubscriber create(File file) {
        return new CSVSubscriber(CSVConsumer.create(file));
    }

    private CSVConsumer consumer;
    private Subscription subscription;

    /**
     *
     */
    public CSVSubscriber(CSVConsumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public void onComplete() {
        consumer.close();
    }

    @Override
    public void onError(Throwable throwable) {
        consumer.close();
    }

    @Override
    public void onNext(INDArray indArray) {
        consumer.accept(indArray);
        subscription.request(1);
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }
}

