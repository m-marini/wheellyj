/*
 * Copyright (c) 2025-2026 Marco Marini, marco.marini@mmarini.org
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

import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;

/**
 * A {@link MultiDataSetIterator} for reinforcement-learning training data
 * that also provides the average reward associated with the current training
 * data and supports requesting termination of the iteration.
 *
 * <p>The average reward can be used by reinforcement-learning algorithms to
 * monitor the quality of the generated training data.</p>
 *
 * @see MultiDataSetIterator
 */

public interface RLDatasetIterator extends MultiDataSetIterator {
    /**
     * Returns the current average reward.
     *
     * @return the average reward associated with the current training data
     */
    float avgReward();

    /**
     * Requests the iterator to stop processing.
     *
     * <p>The implementation should terminate processing as soon as reasonably
     * possible after receiving this request.</p>
     */
    void stop();
}
