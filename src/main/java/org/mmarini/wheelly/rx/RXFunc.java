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

package org.mmarini.wheelly.rx;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Predicate;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

public interface RXFunc {

    /**
     * Returns the first element matching the predicate within timeout or none if timed out
     *
     * @param flowable  the flowable
     * @param predicate the predicate to match
     * @param timeout   the timeout
     * @param <T>       the flow items type
     */
    static <T> Maybe<T> findFirst(Flowable<T> flowable, Predicate<T> predicate, long timeout) {
        return flowable.filter(predicate)
                .take(timeout, TimeUnit.MILLISECONDS)

                .firstElement();
    }

    /**
     * Returns the flowable that emitt only the valid item or an error
     *
     * @param flow  the flow
     * @param valid the validation function
     * @param <T>   the flow item type
     */
    static <T> Flowable<T> validate(Flowable<T> flow, Predicate<T> valid) {
        return flow.flatMap(item ->
                valid.test(item)
                        ? Flowable.just(item)
                        : Flowable.error(new IllegalArgumentException("Wrong item")));
    }

    /**
     * Returns the function to log a flow error
     *
     * @param logger  the logger
     * @param message the message
     */
    static Consumer<? super Throwable> logError(Logger logger, String message) {
        return err ->
                logger.atError().setCause(err).log(message);
    }

}
