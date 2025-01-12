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
import org.mmarini.Tuple2;
import org.mmarini.yaml.Utils;
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
 * Load and cache json schemas
 */
public class JsonSchemas {
    private static final Logger logger = LoggerFactory.getLogger(JsonSchemas.class);

    private static final JsonSchemas singleton = create(
            "/ppo-agent-schema.yml",
            "/agent-single-nn-schema.yml",
            "/ppo-agent-spec-schema.yml",
            "/tdagent-spec-schema.yml",
            "/checkup-schema.yml",
            "/objective-avoid-contact-schema.yml",
            "/objective-cautious-schema.yml",
            "/objective-nomove-schema.yml",
            "/objective-explore-schema.yml",
            "/objective-stuck-schema.yml",
            "/objective-constant-schema.yml",
            "/objective-label-schema.yml",
            "/objective-moveToLabel-schema.yml",
            "/objective-sensor-label-schema.yml",
            "/objective-action-set-schema.yml",
            "/controller-schema.yml",
            "/env-polar-schema.yml",
            "/executor-schema.yml",
            "/monitor-schema.yml",
            "/network-list-schema.yml",
            "/network-schema.yml",
            "/robot-schema.yml",
            "/sim-robot-schema.yml",
            "/signal-schema.yml",
            "/state-agent-schema.yml",
            "/wheelly-schema.yml",
            "/batch-schema.yml"
    );

    private static JsonSchemas create(String... schemas) {
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
    protected JsonSchemas(Map<String, JsonSchema> cache) {
        this.cache = cache;
    }

    /**
     * Returns the schema from resource
     *
     * @param id the resource schema
     */
    public JsonSchema get(String id) {
        JsonSchema jsonSchema = cache.get(id);
        if (jsonSchema == null) {
            throw new IllegalArgumentException(format("Schema %s not found", id));
        }
        return jsonSchema;
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
