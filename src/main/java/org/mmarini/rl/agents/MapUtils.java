/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
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

import org.mmarini.Tuple2;

import java.util.Map;
import java.util.function.Function;

/**
 * Map utilities
 */
public interface MapUtils {
    /**
     * Returns the map with key prefix
     *
     * @param map    the map
     * @param prefix the prefix
     * @param <T>    the map type
     */
    static <T> Map<String, T> keyPrefix(Map<String, T> map, String prefix) {
        return mapKey(map, k -> prefix + k);
    }

    /**
     * Return the map with keys mapped by mapper function
     *
     * @param map    the map
     * @param mapper the mapper function
     * @param <K1>   the type of input map key
     * @param <K2>   the type of output map key
     * @param <V>    the type of map value
     */
    static <K1, K2, V> Map<K2, V> mapKey(Map<K1, V> map, Function<K1, K2> mapper) {
        return Tuple2.stream(map)
                .map(Tuple2.map1(mapper))
                .collect(Tuple2.toMap());
    }
}
