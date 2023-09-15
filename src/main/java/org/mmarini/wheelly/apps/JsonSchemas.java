/*
 * Copyright (c) 2023 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.apps;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.mmarini.yaml.Utils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Load and cache json schemas
 */
public class JsonSchemas {
    private static final JsonSchemas singleton = new JsonSchemas();

    /**
     * Returns the singleton instance
     */
    public static JsonSchemas instance() {
        return singleton;
    }

    private final Map<String, JsonSchema> cache;

    /**
     * Creates the json schemas
     */
    protected JsonSchemas() {
        this.cache = new HashMap<>();
    }


    /**
     * Returns the schema from resource
     *
     * @param id the resource schema
     * @throws IOException in case of error
     */
    public JsonSchema get(String id) throws IOException {
        JsonSchema schema = cache.get(id);
        if (schema == null) {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            JsonNode jsonSchemeNode = Utils.fromResource(id);
            schema = factory.getSchema(jsonSchemeNode);
            cache.put(id, schema);
        }
        return schema;
    }

    /**
     * Returns the validation error set for a json instance against the schema
     *
     * @param node   the instance
     * @param schema the schema
     */
    public Set<ValidationMessage> validate(JsonNode node, String schema) throws IOException {
        return get(schema).validate(node);
    }

    /**
     * Throws exception in case of validation error
     *
     * @param node   the instance
     * @param schema the schema
     */
    public void validateOrThrow(JsonNode node, String schema) {
        try {
            Set<ValidationMessage> errors = validate(node, schema);
            if (!errors.isEmpty()) {
                String text = errors.stream()
                        .map(ValidationMessage::toString)
                        .collect(Collectors.joining(", "));
                throw new IllegalArgumentException(format("Errors: %s", text));
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
