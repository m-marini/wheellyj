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

import org.mmarini.MapStream;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.lang.String.format;

/**
 * Collects a set of file by key
 */
public interface KeyFileMap {

    static <T> Map<String, T> children(Map<String, T> map, String parent) {
        return MapStream.of(map)
                .flatMap((key1, value) -> {
                    String key = children(key1, parent);
                    return key != null ? Map.of(key, value) : Map.of();
                })
                .toMap();
    }

    /**
     * Returns the children key
     *
     * @param key    the parent key
     * @param prefix the parent prefix
     */
    static String children(String key, String prefix) {
        prefix = prefix.endsWith(".") ? prefix : prefix + ".";
        int prefixLen = prefix.length();
        return key.startsWith(prefix)
                ? key.substring(prefixLen) : null;
    }

    /**
     * Closes all resources
     *
     * @param resources the resources
     * @throws Exception first thrown exception
     */
    static void close(AutoCloseable... resources) throws Exception {
        Exception ex = null;
        for (AutoCloseable resource : resources) {
            try {
                resource.close();
            } catch (Exception e) {
                ex = ex == null ? e : ex;
            }
        }
        if (ex != null) {
            throw ex;
        }
    }

    /**
     * Returns the files after closing them
     *
     * @param files the file
     */
    static void close(Map<String, BinArrayFile>... files) throws Exception {
        BinArrayFile[] resources = Arrays.stream(files)
                .flatMap(m -> m.values().stream())
                .toArray(BinArrayFile[]::new);
        close(resources);
    }

    /**
     * Close all the resources
     *
     * @param resources the file
     * @throws Exception in case of errors
     */
    static void close(Collection<? extends AutoCloseable> resources) throws Exception {
        close(resources.toArray(AutoCloseable[]::new));
    }

    /**
     * Returns the key file map with the existing file by key path
     *
     * @param path the path
     * @param keys the keys
     */
    static Map<String, BinArrayFile> create(File path, String... keys) {
        return streamBinArrayFile(path, keys).toMap();
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
     * Returns the batch data read from files
     *
     * @param files     the file
     * @param batchSize the size
     */
    static Map<String, INDArray> read(Map<String, BinArrayFile> files, long batchSize) {
        Map<String, INDArray> result = MapStream.of(files)
                .flatMap((key, value) -> {
                    try {
                        INDArray data = value.read(batchSize);
                        return data != null ? Map.of(key, data) : Map.of();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toMap();
        return result.size() == files.size() ? result : null;
    }

    /**
     * Returns the files after seeks them to position
     *
     * @param files    the files
     * @param position the  position
     * @throws IOException in case of error
     */
    static Map<String, BinArrayFile> seek(Map<String, BinArrayFile> files, long position) throws IOException {
        for (BinArrayFile file : files.values()) {
            file.seek(position);
        }
        return files;
    }

    /**
     * Returns the key file map with the existing file by key predicate
     *
     * @param path   the path
     * @param filter the filter
     */
    static MapStream<String, File> stream(File path, Predicate<String> filter) {
        return streamFolders(path)
                .filterKeys(filter)
                .mapValues(path1 -> new File(path1, "data.bin"))
                .filterValues(file -> file.isFile() && file.canRead());
    }

    /**
     * Returns the reader map with the entries by keys
     *
     * @param path the base path
     * @param keys the keys
     */
    static MapStream<String, File> stream(File path, String... keys) {
        return stream(path, matchesKey(keys));
    }

    /**
     * Returns the stream of binary files
     *
     * @param path the path
     * @param keys the keys
     */
    static MapStream<String, BinArrayFile> streamBinArrayFile(File path, String... keys) {
        return stream(path, keys).mapValues(file -> new BinArrayFile(file));
    }

    /**
     * Returns the list of kpis and folders by deep traversing the filesystem tree
     *
     * @param acc    the accumulator
     * @param path   the path to traverse
     * @param prefix the key prefix
     */
    private static Stream.Builder<Map.Entry<String, File>> streamFolders(Stream.Builder<Map.Entry<String, File>> acc, File path, String prefix) {
        File[] paths = path.listFiles(File::isDirectory);
        if (paths != null) {
            for (File file : paths) {
                streamFolders(acc, file,
                        prefix.isEmpty()
                                ? file.getName()
                                : prefix + "." + file.getName());
            }
            acc.add(Map.entry(prefix, path));
        }
        return acc;
    }

    /**
     * Returns the list of kpis and folders by deep traversing the filesystem tree
     *
     * @param path the path to traverse
     */
    private static MapStream<String, File> streamFolders(File path) {
        return new MapStream<>(streamFolders(Stream.builder(), path, "").build());
    }

    /**
     * Validate for same shape
     *
     * @param files the files
     * @throws IOException in case of error
     */
    static void validateShapes(Collection<BinArrayFile> files) throws IOException {
        if (!files.isEmpty()) {
            BinArrayFile refFile = files.iterator().next();
            long[] shape = refFile.shape();
            List<String> wrongSizes = files.stream()
                    .filter(f -> {
                        try {
                            return !Arrays.equals(shape, f.shape());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .map(f -> {
                        try {
                            return format("%s %s",
                                    f.file(),
                                    Arrays.toString(f.shape()));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();
            if (!wrongSizes.isEmpty()) {
                throw new RuntimeException(format("Wrong file shapes %s referred to %s %s",
                        String.join(", ", wrongSizes),
                        refFile.file(), Arrays.toString(shape)));
            }
        }
    }

    /**
     * Validate for same size
     *
     * @param files the files
     * @throws IOException in case of error
     */
    static void validateSizes(Collection<BinArrayFile> files) throws IOException {
        if (!files.isEmpty()) {
            BinArrayFile refFile = files.iterator().next();
            long size = refFile.size();
            List<String> wrongSizes = files.stream()
                    .filter(f -> {
                        try {
                            return f.size() != size;
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .map(f -> {
                        try {
                            return format("%s (%d)",
                                    f.file(),
                                    f.size());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();
            if (!wrongSizes.isEmpty()) {
                throw new IllegalArgumentException(format("Wrong files size %s referred to %s (%d)",
                        String.join(", ", wrongSizes),
                        refFile.file(), size));
            }
        }
    }
}
