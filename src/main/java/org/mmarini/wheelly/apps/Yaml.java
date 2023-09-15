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
import org.mmarini.yaml.schema.Locator;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;

public interface Yaml {


    /**
     * Returns an object instance from configuration file
     *
     * @param <T>        the returned object class
     * @param file       the filename
     * @param schema     the validation schema
     * @param args       the builder additional arguments
     * @param argClasses the builder additional argument classes
     */
    static <T> T fromConfig(String file, String schema, Object[] args, Class<?>[] argClasses) {
        try {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            JsonNode jsonSchemeNode = Utils.fromResource(schema);
            JsonSchema jsonSchema = factory.getSchema(jsonSchemeNode);
            JsonNode config = org.mmarini.yaml.Utils.fromFile(file);
            Set<ValidationMessage> errors = jsonSchema.validate(config);
            if (!errors.isEmpty()) {
                String text = errors.stream()
                        .map(ValidationMessage::toString)
                        .collect(Collectors.joining(", "));
                throw new RuntimeException(format("Errors: %s", text));
            }
            String active = Locator.locate("active").getNode(config).asText();
            Locator baseLocator = Locator.locate("configurations").path(active);
            return Utils.createObject(config, baseLocator, args, argClasses);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static int[] loadIntArray(JsonNode root, Locator locator) {
        return locator.elements(root)
                .mapToInt(l -> l.getNode(root).asInt())
                .toArray();
    }
}
