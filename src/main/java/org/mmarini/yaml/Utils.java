/*
 * Copyright (c) 2019 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.*;

public class Utils {

    static final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    /**
     * @param file the filename
     * @throws IOException in case of error
     */
    public static JsonNode fromFile(String file) throws IOException {
        return objectMapper.readTree(new FileReader(file));
    }

    /**
     * @param file the file
     * @throws IOException in case of error
     */
    public static JsonNode fromFile(File file) throws IOException {
        return objectMapper.readTree(new FileReader(file));
    }

    /**
     * @param resource the resource name
     * @throws IOException in case of error
     */
    public static JsonNode fromResource(String resource) throws IOException {
        InputStream res = Utils.class.getResourceAsStream(resource);
        if (res == null) {
            throw new FileNotFoundException(String.format("Resource \"%s\" not found", resource));
        }
        return objectMapper.readTree(new InputStreamReader(res));
    }

    /**
     * @param text the yaml text
     * @throws IOException in case of error
     */
    public static JsonNode fromText(String text) throws IOException {
        return objectMapper.readTree(new StringReader(text));
    }
}
