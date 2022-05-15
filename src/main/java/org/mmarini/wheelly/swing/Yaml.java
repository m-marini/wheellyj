/*
 *
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.swing;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.wheelly.model.ConfigParameters;
import org.mmarini.yaml.schema.Validator;

import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 *
 */
public class Yaml {

    /**
     *
     */
    public static Validator config() {
        return Validator.objectPropertiesRequired(
                Map.ofEntries(
                        Map.entry("host", Validator.string()),
                        Map.entry("port", Validator.positiveInteger()),
                        Map.entry("connectionTimeout", Validator.positiveInteger()),
                        Map.entry("readTimeout", Validator.positiveInteger()),
                        Map.entry("retryConnectionInterval", Validator.positiveInteger()),
                        Map.entry("motorCommandInterval", Validator.positiveInteger()),
                        Map.entry("scanCommandInterval", Validator.positiveInteger()),
                        Map.entry("engine", Validator.string())
                ),
                List.of("host", "port", "connectionTimeout", "readTimeout", "retryConnectionInterval",
                        "motorCommandInterval", "scanCommandInterval",
                        "engine")
        );
    }

    public static ConfigParameters configParams(JsonNode root) {
        requireNonNull(root);
        return ConfigParameters.create(
                root.path("host").asText(),
                root.path("port").asInt(),
                root.path("connectionTimeout").asLong(),
                root.path("retryConnectionInterval").asLong(),
                root.path("readTimeout").asLong(),
                root.path("motorCommandInterval").asLong(),
                root.path("scanCommandInterval").asLong());
    }

    public static String engine(JsonNode root) {
        requireNonNull(root);
        return root.path("engine").asText();
    }
}
