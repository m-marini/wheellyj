/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.optimize.api.TrainingListener;
import org.mmarini.wheelly.batch.ProgressInfo;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.List;
import java.util.Map;

/**
 * Deep learning training model listener
 */
public class DLListener implements TrainingListener {
    private final PublishProcessor<ProgressInfo> progressInfo;
    private final int numIterations;

    /**
     * Creates the listener
     *
     * @param numIterations the total number of iteration
     */
    public DLListener(int numIterations) {
        this.numIterations = numIterations;
        this.progressInfo = PublishProcessor.create();
    }

    @Override
    public void iterationDone(Model model, int iteration, int epoch) {
        progressInfo.onNext(new ProgressInfo("Iteration", iteration, epoch));
    }

    @Override
    public void onBackwardPass(Model model) {

    }

    @Override
    public void onEpochEnd(Model model) {
    }

    @Override
    public void onEpochStart(Model model) {

    }

    @Override
    public void onForwardPass(Model model, Map<String, INDArray> map) {
    }

    @Override
    public void onForwardPass(Model model, List<INDArray> list) {

    }

    @Override
    public void onGradientCalculation(Model model) {

    }

    public Flowable<ProgressInfo> readProgressInfo() {
        return progressInfo;
    }
}
