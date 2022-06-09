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


import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.schedulers.Timed;
import org.deeplearning4j.core.storage.StatsStorage;
import org.deeplearning4j.ui.api.UIServer;
import org.deeplearning4j.ui.model.stats.StatsListener;
import org.deeplearning4j.ui.model.storage.InMemoryStatsStorage;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.engines.deepl.ActorCriticAgent;
import org.mmarini.wheelly.engines.deepl.Feedback;
import org.mmarini.wheelly.engines.deepl.RLEngine;
import org.mmarini.wheelly.model.FileFunctions;
import org.mmarini.wheelly.model.MapStatus;
import org.mmarini.wheelly.model.MotionCommand;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import static org.mmarini.wheelly.apps.Yaml.analysis;
import static org.mmarini.wheelly.engines.deepl.RLEngine.createKpi;
import static org.mmarini.wheelly.model.FileFunctions.*;
import static org.nd4j.linalg.factory.Nd4j.hstack;
import static org.nd4j.linalg.factory.Nd4j.zeros;

public class Simulate {
    private static final Logger logger = LoggerFactory.getLogger(Simulate.class);

    public static void main(String[] args) throws Throwable {
        try (INDArray x = zeros(1)) {
        }
        logger.info("Create analysis");
        if (args.length < 1) {
            throw new IllegalArgumentException("Missing config file");
        }
        new Simulate(new File(args[0])).start();
        logger.info("Completed.");
    }

    private final File confFile;
    private JsonNode config;
    private RLEngine engine;

    protected Simulate(File confFile) {
        this.confFile = confFile;
    }

    private INDArray[] generateData(Timed<MapStatus> s0, Tuple2<MotionCommand, Integer> action, Timed<MapStatus> s1) {
        // Generare i segnali di input, gli indicatori di qualità per ogni interazione:
        // indicator: reward, v0*, delta, alpha*
        ActorCriticAgent agent = engine.getAgent();
        Feedback feedback = engine.createFeedback(s0, action, s1);
        Map<String, Object> map = agent.directLearn(feedback, engine.getRandom());
        INDArray[] labels = (INDArray[]) map.get("labels");
        INDArray labArray = hstack(labels);
        INDArray data = hstack(feedback.getS0(), labArray);
        INDArray kpi = createKpi(map, feedback.getReward());
        return new INDArray[]{data, kpi};
    }

    private void start() throws IOException, InterruptedException {
        logger.info("Reading configuaration {} ...", confFile);
        config = Utils.fromFile(confFile);
        analysis().apply(Locator.root()).accept(config);
        File inFilename = new File(Locator.locate("inputFile").getNode(config).asText());
        logger.info("Reading data file {} ...", inFilename);
        File outFile = new File(Locator.locate("outputFile").getNode(config).asText());
        logger.info("Writing data file {} ...", outFile);
        File kpiFile = new File(Locator.locate("kpiFile").getNode(config).asText());
        logger.info("Writing kpi file {} ...", kpiFile);
        engine = RLEngine.fromJson(config, Locator.locate("agent"));
        int numEpochs = Locator.locate("numEpochs").getNode(config).asInt();

        UIServer uiServer = UIServer.getInstance();
        StatsStorage statsStorage = new InMemoryStatsStorage();         //Alternative: new FileStatsStorage(File), for saving and loading later

        //Attach the StatsStorage instance to the UI: this allows the contents of the StatsStorage to be visualized
        uiServer.attach(statsStorage);

        engine.getAgent().getAgentModel().setListeners(new StatsListener(statsStorage));
        File[] files = new File[]{outFile, kpiFile};
        kpiFile.delete();
        for (int ephoc = 0; ephoc < numEpochs; ephoc++) {
            logger.info("Epoch {}.", ephoc + 1);
            outFile.delete();
            readDumpFile(inFilename)
                    .buffer(2, 1)
                    .filter(t -> t.size() >= 2)
                    .map(data -> this.generateData(data.get(0)._1, data.get(0)._2, data.get(1)._1))
                    .map(data -> Arrays.stream(data)
                            .map(FileFunctions::toCSVRaw)
                            .toArray(String[]::new))
                    .blockingSubscribe(writeFiles(files));
            logger.info("Written data file {}.", outFile);
        }
        logger.info("Written kpi file {}.", kpiFile);
        uiServer.stop();
    }
}
