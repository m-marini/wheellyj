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

package org.mmarini;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Map stream
 *
 * @param <K> key type
 * @param <V> value type
 */
public record MapStream<K, V>(Stream<Map.Entry<K, V>> entries) {

    /**
     * Returns the concatenated stream
     *
     * @param streams the streams
     * @param <K>     the key type
     * @param <V>     the value type
     */
    public static <K, V> MapStream<K, V> concat(MapStream<K, V>... streams) {
        return new MapStream<>(
                Arrays.stream(streams)
                        .flatMap(MapStream::entries)
        );
    }

    /**
     * Returns the concatenated maps stream
     *
     * @param maps the streams
     * @param <K>  the key type
     * @param <V>  the value type
     */
    public static <K, V> MapStream<K, V> concatMaps(Map<K, V>... maps) {
        return new MapStream<>(
                Arrays.stream(maps)
                        .flatMap(map -> of(map).entries()));
    }

    /**
     * Returns the entries stream of a tuple2 stream
     *
     * @param stream the map
     * @param <K>    the key type
     * @param <V>    the value type
     */
    public static <K, V> MapStream<K, V> fromTuple2Stream(Stream<Tuple2<K, V>> stream) {
        return new MapStream<>(stream.map(t -> Map.entry(t._1, t._2)));
    }

    /**
     * Returns the entries stream of a map
     *
     * @param map the map
     * @param <K> the key type
     * @param <V> the value type
     */
    public static <K, V> MapStream<K, V> of(Map<K, V> map) {
        return new MapStream<>(map.entrySet().stream());
    }

    /**
     * Creates the map stream
     *
     * @param entries the entries
     */
    public MapStream(Stream<Map.Entry<K, V>> entries) {
        this.entries = requireNonNull(entries);
    }

    /**
     * Returns the stream with key filtered
     *
     * @param predicate the key predicate
     */
    public MapStream<K, V> filter(BiPredicate<K, V> predicate) {
        return new MapStream<>(entries.filter(t -> predicate.test(t.getKey(), t.getValue())));
    }

    /**
     * Returns the stream with key filtered
     *
     * @param predicate the key predicate
     */
    public MapStream<K, V> filterKeys(Predicate<K> predicate) {
        return new MapStream<>(entries.filter(t -> predicate.test(t.getKey())));
    }

    /**
     * Returns the stream with key filtered
     *
     * @param predicate the key predicate
     */
    public MapStream<K, V> filterValues(Predicate<V> predicate) {
        return new MapStream<>(entries.filter(t -> predicate.test(t.getValue())));
    }

    /**
     * Returns the flat map
     *
     * @param mapper the mapper
     * @param <K1>   the result key type
     * @param <V1>   the result value type
     */
    public <K1, V1> MapStream<K1, V1> flatMap(BiFunction<K, V, Map<K1, V1>> mapper) {
        return new MapStream<>(
                entries.flatMap(entry ->
                        of(mapper.apply(entry.getKey(),
                                entry.getValue()))
                                .entries()));
    }

    /**
     * Iterates over all entries
     *
     * @param consumer the consumer
     */
    public void forEach(BiConsumer<K, V> consumer) {
        entries.forEach(entry -> consumer.accept(entry.getKey(), entry.getValue()));
    }


    /**
     * Iterates over all entries in ordered way
     *
     * @param consumer the consumer
     */
    public void forEachOrdered(BiConsumer<K, V> consumer) {
        entries.forEachOrdered(entry -> consumer.accept(entry.getKey(), entry.getValue()));
    }

    /**
     * Returns the map stream grouped by key
     */
    public MapStream<K, List<K>> groupByKey() {
        Map<K, List<Map.Entry<K, V>>> grouped = entries.collect(Collectors.groupingBy(Map.Entry::getKey));
        return MapStream.of(grouped)
                .mapValues(list ->
                        list.stream()
                                .map(Map.Entry::getKey)
                                .toList());
    }

    /**
     * Returns the key stream
     */
    public Stream<K> keys() {
        return entries.map(Map.Entry::getKey);
    }

    /**
     * Returns the mapped keys
     *
     * @param mapper the key mapper
     * @param <K1>   the return key type
     */
    public <K1> MapStream<K1, V> mapKeys(Function<K, K1> mapper) {
        return new MapStream<>(entries.map(entry ->
                Map.entry(mapper.apply(entry.getKey()),
                        entry.getValue())));
    }

    /**
     * Returns the mapped keys
     *
     * @param mapper the key mapper
     * @param <K1>   the return key type
     */
    public <K1> MapStream<K1, V> mapKeys(BiFunction<K, V, K1> mapper) {
        return new MapStream<>(entries.map(entry ->
                Map.entry(
                        mapper.apply(entry.getKey(),
                                entry.getValue()),
                        entry.getValue())));
    }

    /**
     * Returns the stream of mapped entries
     *
     * @param mapper the mapper
     * @param <V1>   the result value type
     */
    public <V1> Stream<V1> mapToObj(BiFunction<K, V, V1> mapper) {
        return entries.map(entry -> mapper.apply(entry.getKey(), entry.getValue()));
    }

    /**
     * Returns the mapped values
     *
     * @param mapper the mapper
     * @param <V1>   the return value type
     */
    public <V1> MapStream<K, V1> mapValues(Function<V, V1> mapper) {
        return new MapStream<>(entries.map(entry ->
                Map.entry(
                        entry.getKey(),
                        mapper.apply(entry.getValue()))));
    }

    /**
     * Returns the mapped values
     *
     * @param mapper the mapper
     * @param <V1>   the return value type
     */
    public <V1> MapStream<K, V1> mapValues(BiFunction<K, V, V1> mapper) {
        return new MapStream<>(entries.map(entry ->
                Map.entry(
                        entry.getKey(),
                        mapper.apply(entry.getKey(),
                                entry.getValue()))));
    }

    /**
     * Returns the map
     */
    public Map<K, V> toMap() {
        return entries.collect(
                Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    /**
     * Returns the stream of tuples
     */
    public Stream<Tuple2<K, V>> tuples() {
        return entries.map(entry -> Tuple2.of(entry.getKey(), entry.getValue()));
    }

    /**
     * Returns the stream of values
     */
    public Stream<V> values() {
        return entries.map(Map.Entry::getValue);
    }

}
