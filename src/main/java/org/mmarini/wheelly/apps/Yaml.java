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

package org.mmarini.wheelly.apps;

import org.mmarini.yaml.schema.Validator;

import java.util.List;
import java.util.Map;

import static org.mmarini.wheelly.engines.deepl.Yaml.engineConf;
import static org.mmarini.yaml.schema.Validator.*;

public interface Yaml {

    static Validator analysis() {
        return objectPropertiesRequired(Map.of(
                        "version", string(values("0.1")),
                        "inputFile", string(),
                        "outputFile", string(),
                        "kpiFile", string(),
                        "agent", engineConf(),
                        "numEpochs", positiveInteger()
                ), List.of(
                        "version",
                        "inputFile",
                        "outputFile",
                        "kpiFile",
                        "agent",
                        "numEpochs"
                )
        );
    }

    static Validator createFile() {
        return objectPropertiesRequired(Map.of(
                        "version", string(values("0.1")),
                        "inputFile", string(),
                        "outputFile", string(),
                        "agent", engineConf()
                ), List.of(
                        "version",
                        "inputFile",
                        "outputFile",
                        "agent"
                )
        );
    }

    static Validator trainer() {
        return objectPropertiesRequired(Map.of(
                "version", string(values("0.1")),
                "inputFile", string(minLength(1)),
                "modelFile", string(minLength(1)),
                "numEpochs", positiveInteger(),
                "batchSize", positiveInteger(),
                "inputSize", positiveInteger()), List.of(
                "version",
                "inputFile",
                "modelFile",
                "numEpochs",
                "batchSize",
                "inputSize")
        );
    }
}
