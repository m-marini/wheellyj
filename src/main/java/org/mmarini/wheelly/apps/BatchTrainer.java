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
import org.datavec.api.records.reader.impl.csv.CSVRecordReader;
import org.datavec.api.split.FileSplit;
import org.datavec.api.writable.Writable;
import org.deeplearning4j.core.storage.StatsStorage;
import org.deeplearning4j.datasets.datavec.RecordReaderMultiDataSetIterator;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.ui.api.UIServer;
import org.deeplearning4j.ui.model.stats.StatsListener;
import org.deeplearning4j.ui.model.storage.InMemoryStatsStorage;
import org.deeplearning4j.util.ModelSerializer;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.mmarini.wheelly.apps.Yaml.trainer;
import static org.mmarini.yaml.schema.Locator.locate;
import static org.nd4j.linalg.factory.Nd4j.zeros;

public class BatchTrainer {
    public static final int CRITIC_SIZE = 1;
    public static final int HALT_ACTOR_SIZE = 2;
    public static final int DIRECTION_ACTOR_SIZE = 24;
    public static final int SPEED_ACTOR_SIZE = 21;
    public static final int SENSOR_ACTOR_SIZE = 9;
    private static final Logger logger = LoggerFactory.getLogger(BatchTrainer.class);

    public static void main(String[] args) throws Throwable {
        zeros(1);
        logger.info("BatchTrainer");
        if (args.length < 1) {
            throw new IllegalArgumentException("Missing config file");
        }
        new BatchTrainer(new File(args[0])).run();
        logger.info("Completed.");
    }

    private final File confFile;

    protected BatchTrainer(File file) {
        this.confFile = file;
    }

    private void run() throws IOException, InterruptedException {
        logger.info("Reading configuration {} ...", confFile);
        JsonNode config = Utils.fromFile(confFile);
        trainer().apply(Locator.root()).accept(config);
        File inFile = new File(locate("inputFile").getNode(config).asText());
        logger.info("Reading data file {} ...", inFile);
        CSVRecordReader reader = new CSVRecordReader(0, ',');
        reader.initialize(new FileSplit(inFile));
        List<Writable> record = reader.next();
        int numEpochs = locate("numEpochs").getNode(config).asInt();
        int batchSize = locate("batchSize").getNode(config).asInt();
        int inputSize = locate("inputSize").getNode(config).asInt();
        int haltActorOffset = inputSize + CRITIC_SIZE;
        int directionActorOffset = haltActorOffset + HALT_ACTOR_SIZE;
        int speedActorOffset = directionActorOffset + DIRECTION_ACTOR_SIZE;
        int sensorActorOffset = speedActorOffset + SPEED_ACTOR_SIZE;
        int size = sensorActorOffset + SENSOR_ACTOR_SIZE;
        if (size != record.size()) {
            logger.error("Wrong record size = {}, file record size = {}",
                    size,
                    record.size());
            throw new Error("Wrong record size");
        }
        RecordReaderMultiDataSetIterator.Builder builder = new RecordReaderMultiDataSetIterator.Builder(batchSize)
                .addReader("reader", reader)
                .addInput("reader", 0, inputSize - 1)
                .addOutput("reader", inputSize, inputSize + CRITIC_SIZE - 1)
                .addOutput("reader", haltActorOffset, haltActorOffset + HALT_ACTOR_SIZE - 1)
                .addOutput("reader", directionActorOffset, directionActorOffset + DIRECTION_ACTOR_SIZE - 1)
                .addOutput("reader", speedActorOffset, speedActorOffset + SPEED_ACTOR_SIZE - 1)
                .addOutput("reader", sensorActorOffset, sensorActorOffset + SENSOR_ACTOR_SIZE - 1);
        RecordReaderMultiDataSetIterator dsi = builder.build();
        File modelFile = new File(locate("modelFile").getNode(config).asText());

        UIServer uiServer = UIServer.getInstance();
        StatsStorage statsStorage = new InMemoryStatsStorage();         //Alternative: new FileStatsStorage(File), for saving and loading later

        //Attach the StatsStorage instance to the UI: this allows the contents of the StatsStorage to be visualized
        uiServer.attach(statsStorage);

        // Then add the StatsListener to collect this information from the network, as it trains
        logger.info("Loading model {} ...", modelFile);
        ComputationGraph net = ModelSerializer.restoreComputationGraph(modelFile, true);
        net.setListeners(new StatsListener(statsStorage));
        net.fit(dsi, numEpochs);
        logger.info("Saving model {} ...", modelFile);
        ModelSerializer.writeModel(net, modelFile, false);
        uiServer.stop();
    }
}
