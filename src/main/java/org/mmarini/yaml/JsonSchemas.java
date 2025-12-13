/*
 * Copyright (c) 2023-2025 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;

/**
 * Load and cache JSON schemas
 */
public class JsonSchemas {
    private static final Logger logger = LoggerFactory.getLogger(JsonSchemas.class);

    /**
     * Returns the JSON schema loaded from the file list
     *
     * @param schemas schema file list
     */
    public static JsonSchemas load(String... schemas) {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

        Map<String, JsonSchema> schemaMap = Arrays.stream(schemas)
                .flatMap(id -> {
                    try {
                        return Stream.of(Utils.fromResource(id));
                    } catch (IOException e) {
                        logger.atError().setCause(e).log("Error loading schema {}", id);
                        return Stream.of();
                    }
                })
                .map(factory::getSchema)
                .filter(schema -> {
                    boolean hasId = schema.getSchemaNode().has("$id");
                    if (!hasId) {
                        logger.atError().log("Missing $id in schema {}", schema.getSchemaPath());
                    }
                    return hasId;
                })
                .map(schema -> Tuple2.of(
                        schema.getSchemaNode().get("$id").asText(),
                        schema
                ))
                .collect(Tuple2.toMap());
        return new JsonSchemas(schemaMap);
    }

    private final Map<String, JsonSchema> cache;

    /**
     * Creates the JSON schemas
     */
    protected JsonSchemas(Map<String, JsonSchema> cache) {
        this.cache = cache;
    }

    /**
     * Returns the schema from resource
     *
     * @param id       the resource schema
     * @param filename the document filename
     */
    public JsonSchema get(String id, String filename) {
        JsonSchema jsonSchema = cache.get(id);
        if (jsonSchema == null) {
            throw new IllegalArgumentException(format("%s: Schema %s not found",
                    filename,
                    id));
        }
        return jsonSchema;
    }

    /**
     * Returns the validation error set for a JSON instance against the schema
     *
     * @param node     the instance
     * @param schema   the schema
     * @param filename the document filename
     */
    public Set<ValidationMessage> validate(JsonNode node, String schema, String filename) throws IOException {
        return get(schema, filename).validate(node);
    }

    /**
     * Throws exception in case of validation error
     *
     * @param node   the instance
     * @param schema the schema
     */
    public void validateOrThrow(JsonNode node, String schema) {
        validateOrThrow(node, schema, null);
    }

    /**
     * Throws exception in case of validation error
     *
     * @param node     the document instance
     * @param schema   the schema name
     * @param filename the filename
     */
    public void validateOrThrow(JsonNode node, String schema, String filename) {
        if (filename == null) {
            filename = "";
        }
        Set<ValidationMessage> errors;
        try {
            errors = validate(node, schema, filename);
        } catch (IOException ex) {
            throw new RuntimeException(format("%s: %s", filename, ex.getMessage()), ex);
        }
        if (!errors.isEmpty()) {
            String text = errors.stream()
                    .map(ValidationMessage::toString)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(format("%s: %s", filename, text));
        }
    }
}
