/*
 *
 * Copyright (c) )2022 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.model;

import java.time.Instant;
import java.util.StringJoiner;
import java.util.function.Supplier;

/**
 * @param <T>
 */
public class InstantValue<T> implements Supplier<T> {
    /**
     * @param instant
     * @param value
     * @param <T>
     */
    public static <T> InstantValue<T> of(Instant instant, T value) {
        return new InstantValue<>(instant, value);
    }

    public final Instant instant;
    public final T value;

    /**
     * @param instant
     * @param value
     */
    protected InstantValue(Instant instant, T value) {
        this.instant = instant;
        this.value = value;
    }

    @Override
    public T get() {
        return value;
    }

    /**
     *
     */
    public Instant getInstant() {
        return instant;
    }

    /**
     *
     */
    public T getValue() {
        return value;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", InstantValue.class.getSimpleName() + "[", "]")
                .add("instant=" + instant)
                .add("value=" + value)
                .toString();
    }
}
