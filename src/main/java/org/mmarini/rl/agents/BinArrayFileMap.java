/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org.
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

package org.mmarini.rl.agents;

import io.reactivex.rxjava3.functions.Supplier;
import org.mmarini.ParallelProcess;
import org.mmarini.Tuple2;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Access a set of binary array file
 * The read function runs in parallel threads
 *
 * @param files the map of files
 */
public record BinArrayFileMap(Map<String, BinArrayFile> files) implements Closeable {
    private static final BinArrayFileMap EMPTY = new BinArrayFileMap(Map.of());
    private static final Logger logger = LoggerFactory.getLogger(BinArrayFileMap.class);

    public static <T> Map<String, T> children(Map<String, T> map, String key) {
        return Tuple2.stream(map)
                .filter(matchesTuple(key))
                .map(mapChildren(key))
                .collect(Tuple2.toMap());
    }

    /**
     * Returns the reader map with the entries by key path
     *
     * @param path the path
     * @param key  the key path
     */
    public static BinArrayFileMap create(File path, String key) {
        return EMPTY.addRead(path, key);
    }

    /**
     * Returns the empty record reader
     */
    public static BinArrayFileMap empty() {
        return EMPTY;
    }

    /**
     * Returns the list of kpis and folders by deep traversing the filesystem tree
     *
     * @param acc    the accumulator
     * @param path   the path to traverse
     * @param prefix the key prefix
     */
    private static Stream.Builder<Tuple2<String, File>> listFolders(Stream.Builder<Tuple2<String, File>> acc, File path, String prefix) {
        File[] paths = path.listFiles(File::isDirectory);
        if (paths != null) {
            for (File file : paths) {
                listFolders(acc, file,
                        prefix.isEmpty()
                                ? file.getName()
                                : prefix + "." + file.getName());
            }
            acc.add(Tuple2.of(prefix, path));
        }
        return acc;
    }

    /**
     * Returns the list of kpis and folders by deep traversing the filesystem tree
     *
     * @param path the path to traverse
     */
    private static Stream<Tuple2<String, File>> listFolders(File path) {
        return listFolders(Stream.builder(), path, "").build();
    }

    /**
     * Returns the function that map the children key pattern of tuple
     *
     * @param key the key pattern
     * @param <T> The type of second value of tuple
     */
    public static <T> UnaryOperator<Tuple2<String, T>> mapChildren(String key) {
        String prefix = key.endsWith(".") ? key : key + ".";
        int prefixLen = prefix.length();
        return t -> t.setV1(t._1.startsWith(prefix)
                ? t._1.substring(prefixLen) : ".");
    }

    /**
     * Returns the predicate that match keys
     * Return true if key is equals to pattern of key start with pattern + "."
     *
     * @param pattern the pattern to match
     */
    private static Predicate<String> matchesKey(String... pattern) {
        return Arrays.stream(pattern)
                .map(p -> {
                    if (p.isEmpty()) {
                        return (Predicate<String>) (String key) -> true;
                    } else {
                        String prefix = p.endsWith(".") ? p : p + ".";
                        return (Predicate<String>) (String key) -> key.startsWith(prefix) || key.equals(p);
                    }
                })
                .reduce(Predicate::or)
                .orElse(ignored -> false);
    }

    /**
     * Returns the predicate that match keys
     * Return true if key is equals to pattern of key start with pattern + "."
     *
     * @param pattern the pattern to match
     */
    public static <T> Predicate<Tuple2<String, T>> matchesTuple(String pattern) {
        Predicate<String> matches = matchesKey(pattern);
        return t -> matches.test(t._1);
    }

    /**
     * Create the readr map
     *
     * @param files the map of readers
     */
    public BinArrayFileMap(Map<String, BinArrayFile> files) {
        this.files = requireNonNull(files);
    }

    /**
     * Returns the reader map with the entries by key path
     *
     * @param path   the path
     * @param filter the key filter
     */
    public BinArrayFileMap addRead(File path, Predicate<String> filter) {
        Map<String, BinArrayFile> newReaders = Stream.concat(Tuple2.stream(files),
                        listFolders(path)
                                .filter(t ->
                                        filter.test(t._1) && !files.containsKey(t._1))
                                .map(Tuple2.map2(t -> new File(t, "data.bin")))
                                .filter(t ->
                                        t._2.isFile() && t._2.canRead())
                                .map(Tuple2.map2(BinArrayFile::new)))
                .collect(Tuple2.toMap());
        return new BinArrayFileMap(newReaders);
    }

    /**
     * Returns the reader map with the entries by key path
     *
     * @param path the path
     * @param key  the key path
     */
    public BinArrayFileMap addRead(File path, String key) {
        return addRead(path, matchesKey(key));
    }

    /**
     * Returns the reader map with the entries by keys
     *
     * @param path the base path
     * @param keys the keys
     */
    public BinArrayFileMap addWrite(File path, String... keys) throws IOException {
        Stream<Tuple2<String, BinArrayFile>> addedFiles = Arrays.stream(keys)
                .filter(Predicate.not(files::containsKey))
                .map(key -> Tuple2.of(key, BinArrayFile.createBykey(path, key)));
        Map<String, BinArrayFile> newFiles = Stream.concat(
                        Tuple2.stream(files),
                        addedFiles)
                .collect(Tuple2.toMap());
        return new BinArrayFileMap(newFiles);
    }

    /**
     * Returns the children with key
     *
     * @param key the key
     */
    public BinArrayFileMap children(String key) {
        Map<String, BinArrayFile> newFiles = children(files, key);
        return new BinArrayFileMap(newFiles);
    }

    /**
     * Clears all the files
     *
     * @throws IOException in case of error
     */
    public void clear() throws IOException {
        for (BinArrayFile file : files.values()) {
            file.clear();
        }
    }

    @Override
    public void close() throws IOException {
        for (BinArrayFile file : files.values()) {
            file.close();
        }
    }

    /**
     * Returns the readers after close and remove the key children
     *
     * @param key the key
     * @throws IOException in case of error
     */
    public BinArrayFileMap close(String key) throws IOException {
        Map<String, BinArrayFile> closingReaders = filter(key).files;
        for (BinArrayFile reader : closingReaders.values()) {
            reader.close();
        }
        Map<String, BinArrayFile> newReaders = Tuple2.stream(files)
                .filter(t -> !closingReaders.containsKey(t._1))
                .collect(Tuple2.toMap());
        return new BinArrayFileMap(newReaders);
    }

    /**
     * Returns true if the file map contains the key
     *
     * @param key the key
     */
    public boolean contains(String key) {
        return files.containsKey(key);
    }

    /**
     * Returns the duplication of readers
     */
    public BinArrayFileMap dup() {
        return new BinArrayFileMap(
                Tuple2.stream(files)
                        .map(Tuple2.map2(BinArrayFile::dup))
                        .collect(Tuple2.toMap())
        );
    }

    /**
     * Returns the map filtered by keys
     *
     * @param keys the keys
     */
    public BinArrayFileMap filter(String... keys) {
        return filter(matchesKey(keys));
    }

    /**
     * Returns the map filtered by key predicate
     *
     * @param filter the predicate filter
     */
    public BinArrayFileMap filter(Predicate<String> filter) {
        Map<String, BinArrayFile> newMap = Tuple2.stream(files)
                .filter(t -> filter.test(t._1))
                .collect(Tuple2.toMap());
        return new BinArrayFileMap(newMap);
    }

    /**
     * Flushes all the files
     *
     * @throws IOException in case of error
     */
    public void flush() throws IOException {
        for (BinArrayFile file : files.values()) {
            file.flush();
        }
    }

    /**
     * Returns the binary array file
     *
     * @param key the key
     */
    public BinArrayFile get(String key) {
        return files.get(key);
    }

    /**
     * Returns true if the ap is empty
     */
    public boolean isEmpty() {
        return files.isEmpty();
    }

    /**
     * Returns the map of arrays (key, n, ...)
     *
     * @param size the number of records to read
     */
    public Map<String, INDArray> read(long size) {
        // Create tasks
        Map<String, Supplier<INDArray>> tasks = Tuple2.stream(files).map(t -> {
                    BinArrayFile file = t._2;
                    Supplier<INDArray> task = () -> {
                        logger.atDebug().log("Reading {} records ...", size);
                        INDArray read = file.read(size);
                        return read != null ? read : Nd4j.zeros(0);
                    };
                    return t.setV2(task);
                })
                .collect(Tuple2.toMap());
        Map<String, INDArray> map = ParallelProcess.scheduler(tasks).run();
        return map.values().stream().allMatch(v -> v.size(0) > 0)
                ? map : null;
    }

    /**
     * Resets all files
     *
     * @throws IOException in case of error
     */
    public void reset() throws IOException {
        seek(0);
    }

    /**
     * Resets all files
     *
     * @throws IOException in case of error
     */
    public void seek(long record) throws IOException {
        for (BinArrayFile reader : files.values()) {
            reader.seek(record);
        }
    }
}
