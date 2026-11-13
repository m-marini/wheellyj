/*
 * Copyright (c) 2024-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.storage.serde;

import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Collects a set of file by key
 */
public interface FileArrayReaderMap {
    Logger logger = LoggerFactory.getLogger(FileArrayReaderMap.class);

    static void close(Map<String, ? extends ArrayReader>... files) throws IOException {
        for (Map<String, ? extends ArrayReader> file : files) {
            for (ArrayReader value : file.values()) {
                value.close();
            }
        }
    }

    static Map<String, FileArrayReader> create(Path folder, String... keys) {
        return streamOfKeyReader(folder, keys)
                .collect(Tuple2.toMap());
    }

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

    private static Stream<Tuple2<String, Path>> stream(Path folder, String... keys) {
        return stream(folder, matchesKey(keys));
    }

    private static Stream<Tuple2<String, Path>> stream(Path folder, Predicate<String> filter) {
        return streamFolders(folder, "")
                .filter(t -> filter.test(t.v1()))
                .map(Tuple2.map2(p ->
                        p.resolve("data.bin")));
    }

    private static Stream<Tuple2<String, Path>> streamFolders(Path folder, String key) {
        try {
            return Files.list(folder)
                    .flatMap(f -> {
                        String filename = f.getFileName().toString();
                        if (Files.isRegularFile(f)
                                && Files.isReadable(f)
                                && filename.endsWith(".bin")) {
                            String key1 = filename.substring(0, filename.length() - 4);
                            String key2 = key.isEmpty()
                                    ? key1
                                    : key + "." + key1;
                            return Stream.of(Tuple2.of(key2, f));
                        }
                        if (Files.isDirectory(f) && !filename.contains(".")) {
                            String key2 = key.isEmpty()
                                    ? filename
                                    : key + "." + filename;
                            return streamFolders(f, key2);
                        }
                        return Stream.empty();
                    });

        } catch (IOException e) {
            logger.atError().setCause(e).log("Error while listing folders");
            return Stream.empty();
        }
    }

    private static Stream<Tuple2<String, FileArrayReader>> streamOfKeyReader(Path folder, String... keys) {
        return stream(folder, keys)
                .map(Tuple2.map2(FileArrayReader::new));
    }
}
