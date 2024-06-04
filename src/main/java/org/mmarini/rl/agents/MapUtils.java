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

import org.mmarini.MapStream;
import org.mmarini.Tuple2;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    static <T> Map<String, T> addKeyPrefix(Map<String, T> map, String prefix) {
        return MapStream.of(map)
                .mapKeys(k -> prefix + k)
                .toMap();
    }

    /**
     * Returns the flat map with mapped values stream
     *
     * @param stream the input stream
     * @param <K>    the key type
     * @param <V1>   the input value type
     * @param <V2>   the mapped values
     */
    static <K, V1, V2> Map<K, V2> flatMapValues(Stream<Map<K, V1>> stream, BiFunction<K, Stream<V1>, V2> mapper) {
        Map<K, List<Tuple2<K, V1>>> grouped = stream.flatMap(map -> MapStream.of(map).tuples())
                .collect(Collectors.groupingBy(Tuple2::getV1));
        return MapStream.of(grouped).mapValues((k, v) ->
                        mapper.apply(k, v.stream().map(Tuple2::getV2)))
                .toMap();
    }
}
