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

package org.mmarini.wheelly.engines.deepl;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.wheelly.model.MapStatus;
import org.mmarini.yaml.schema.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.ToDoubleBiFunction;

public class RLEngineConf {
    private static final Logger logger = LoggerFactory.getLogger(RLEngineConf.class);

    public static RLEngineConf fromJson(JsonNode root, Locator locator) {
        Yaml.engineConf().apply(locator).accept(root);
        SignalEncoder encoder = Yaml.stateEncoder(root, locator.path("stateEncoder"));
        ToDoubleBiFunction<Timed<MapStatus>, Timed<MapStatus>> rewardFunc = FunctionBuilder::testReward;
        return new RLEngineConf(encoder, rewardFunc);
    }

    private final SignalEncoder encoder;
    private final ToDoubleBiFunction<Timed<MapStatus>, Timed<MapStatus>> rewardFunc;

    protected RLEngineConf(SignalEncoder encode,
                           ToDoubleBiFunction<Timed<MapStatus>, Timed<MapStatus>> rewardFunc) {
        this.encoder = encode;
        this.rewardFunc = rewardFunc;
    }

    public INDArray encode(Timed<MapStatus> status) {
        return encoder.encode(status);
    }

    public int getNumInputs() {
        return encoder.getNumSignals();
    }

    public double reward(Timed<MapStatus> s0, Timed<MapStatus> s1) {
        return rewardFunc.applyAsDouble(s0, s1);
    }
}
