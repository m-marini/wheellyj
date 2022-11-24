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
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The data collector consumer accumulates data and returns the kpi of data
 */
public class DataCollectorSubscriber implements Subscriber<INDArray> {
    private final List<INDArray> buffer;
    private Subscription subscription;

    /**
     *
     */
    public DataCollectorSubscriber() {
        this.buffer = new ArrayList<>();
    }

    public List<INDArray> getBuffer() {
        return buffer;
    }

    public Kpi getKpi() {
        return Kpi.create(toINDArray());
    }

    @Override
    public void onComplete() {
    }

    @Override
    public void onError(Throwable throwable) {
    }

    @Override
    public void onNext(INDArray indArray) {
        buffer.add(indArray);
        subscription.request(1);
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }

    public void toCsv(File file) throws IOException {
        Serde.toCsv(file, toINDArray());
    }

    public INDArray toINDArray() {
        return Nd4j.vstack(buffer);
    }

}
