package org.mmarini.wheelly.apps;

import org.junit.jupiter.api.Test;
import org.mmarini.rl.agents.BinArrayFile;
import org.mmarini.rl.agents.CSVReader;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.io.File;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class ReportProcessTest {
    public static final int SEED = 1234;
    private static final File KPI_PATH = new File("test/ReportProcessTest/data");
    private static final File REPORT_PATH = new File("test/ReportProcessTest/report");

    @Test
    void deltaRatioReportTest() throws Throwable {
        // Given a random dataset
        Random random = Nd4j.getRandomFactory().getNewRandomInstance(SEED);
        INDArray pi = Nd4j.rand(random, 1000, 4).muli(0.4).addi(0.5);
        INDArray pi0 = Nd4j.rand(random, 1000, 4).muli(0.4).addi(0.5);
        INDArray actions = Transforms.floor(Nd4j.rand(random, 1000, 1).muli(4), false);
        INDArray actionMasks = Nd4j.zeros(1000, 4);
        for (long i = 0; i < 1000; i++) {
            long j = actions.getLong(i);
            actionMasks.putScalar(i, j, 1f);
        }
        // And the input files
        BinArrayFile pi0File = BinArrayFile.createByKey(KPI_PATH, "trainingLayers.deltaRatioReport.values");
        pi0File.clear();
        pi0File.write(pi0);
        pi0File.close();
        BinArrayFile piFile = BinArrayFile.createByKey(KPI_PATH, "trainedLayers.deltaRatioReport.values");
        piFile.clear();
        piFile.write(pi);
        piFile.close();
        BinArrayFile actionFile = BinArrayFile.createByKey(KPI_PATH, "actionMasks.deltaRatioReport");
        actionFile.clear();
        actionFile.write(actionMasks);
        actionFile.close();
        // And the scalar report process
        ReportProcess process = ReportProcess.deltaRatioReport("deltaRatioReport")
                .build(KPI_PATH, REPORT_PATH, 100);

        // When process
        process.process();
        INDArray stats = CSVReader.createByName(REPORT_PATH, "deltaRatioReport.delta.stats").read(100);

        // Then ...
        INDArray prob = pi.mul(actionMasks).sum(true, 1);
        INDArray prob0 = pi0.mul(actionMasks).sum(true, 1);
        INDArray ratio = Transforms.abs(prob.div(prob0).sub(1));
        INDArray expected = Nd4j.createFromArray(
                (float) ratio.size(0),
                ratio.minNumber().floatValue(),
                ratio.maxNumber().floatValue(),
                ratio.meanNumber().floatValue(),
                ratio.stdNumber(true).floatValue(),
                Transforms.log(ratio).meanNumber().floatValue()
        ).reshape(1, 6);
        assertThat(stats, matrixCloseTo(expected, 1e-6));
    }

    @Test
    void maxGMRatioReportTest() throws Throwable {
        // Given a random dataset
        Random random = Nd4j.getRandomFactory().getNewRandomInstance(SEED);
        INDArray data = Nd4j.rand(random, 1000, 4).muli(0.4).addi(0.5);
        // And the input file
        BinArrayFile file = BinArrayFile.createByKey(KPI_PATH, "maxGMRatioReport");
        file.clear();
        file.write(data);
        file.close();
        // And the scalar report process
        ReportProcess process = ReportProcess.maxGMRatioReport("maxGMRatioReport")
                .build(KPI_PATH, REPORT_PATH, 100);

        // When process
        process.process();
        INDArray stats = CSVReader.createByName(REPORT_PATH, "maxGMRatioReport.maxGMRatio.stats").read(100);

        // Then ...
        INDArray gm = Transforms.exp(Transforms.log(data).mean(true, 1));
        INDArray ratio = data.max(true, 1).divi(gm);
        INDArray expected = Nd4j.createFromArray(
                (float) data.size(0),
                ratio.minNumber().floatValue(),
                ratio.maxNumber().floatValue(),
                ratio.meanNumber().floatValue(),
                ratio.stdNumber(true).floatValue(),
                Transforms.log(ratio).meanNumber().floatValue()
        ).reshape(1, 6);
        assertThat(stats, matrixCloseTo(expected, 1e-6));
    }

    @Test
    void maxGmRatioTest() {
        // Given a dataset
        // And a dataset
        INDArray data = Nd4j.createFromArray(
                0.1f, 0.2f, 0.5f, 0.2f,
                0.1f, 0.6f, 0.2f, 0.1f
        ).reshape(2, 4);

        // When reset compute maxMinRatio
        INDArray ratio = ReportProcess.maxGmRatio(data);

        float mean0 = (float) Math.sqrt(Math.sqrt(0.1f * 0.2f * 0.5f * 0.2f));
        float mean1 = (float) Math.sqrt(Math.sqrt(0.1f * 0.6f * 0.2f * 0.1f));
        assertThat(ratio, matrixCloseTo(new float[][]{
                {0.5f / mean0},
                {0.6f / mean1}
        }, 1e-6));
    }

    @Test
    void maxMinRatioReportTest() throws Throwable {
        // Given a random dataset
        Random random = Nd4j.getRandomFactory().getNewRandomInstance(SEED);
        INDArray data = Nd4j.rand(random, 1000, 4).muli(0.4).addi(0.5);
        // And the input file
        BinArrayFile file = BinArrayFile.createByKey(KPI_PATH, "maxMinRatioReportTest");
        file.clear();
        file.write(data);
        file.close();
        // And the scalar report process
        ReportProcess process = ReportProcess.maxMinRatioReport("maxMinRatioReportTest")
                .build(KPI_PATH, REPORT_PATH, 100);

        // When process
        process.process();
        INDArray stats = CSVReader.createByName(REPORT_PATH, "maxMinRatioReportTest.maxMinRatio.stats").read(100);

        // Then ...
        INDArray min = data.min(true, 1);
        INDArray ratio = data.max(true, 1).divi(min);
        INDArray expected = Nd4j.createFromArray(
                (float) data.size(0),
                ratio.minNumber().floatValue(),
                ratio.maxNumber().floatValue(),
                ratio.meanNumber().floatValue(),
                ratio.stdNumber(true).floatValue(),
                Transforms.log(ratio).meanNumber().floatValue()
        ).reshape(1, 6);
        assertThat(stats, matrixCloseTo(expected, 1e-5));
    }

    @Test
    void maxMinRatioTest() {
        // Given a dataset
        // And a dataset
        INDArray data = Nd4j.createFromArray(
                0.1f, 0.2f, 0.5f, 0.2f,
                0.1f, 0.6f, 0.2f, 0.1f
        ).reshape(2, 4);

        // When reset compute maxMinRatio
        INDArray ratio = ReportProcess.maxMinRatio(data);

        assertThat(ratio, matrixCloseTo(new float[][]{
                {0.5f / 0.1f},
                {0.6f / 0.1f}
        }, 1e-6));
    }

    @Test
    void meanAggregatorTest() {
        // Given an aggregator
        ReportProcess.Aggregator agg = ReportProcess.meanAggregator();
        // And a dataset
        INDArray data = Nd4j.createFromArray(-2, -1, 0, 1);

        // When reset and add data twice
        agg.reset();
        agg.add(data);
        agg.add(data);

        assertEquals(8L, agg.numSamples());
        assertEquals(-2f, agg.min());
        assertEquals(1f, agg.max());
        assertEquals(-0.5f, agg.value());
    }

    @Test
    void maxReportTest() throws Throwable {
        // Given a random dataset
        Random random = Nd4j.getRandomFactory().getNewRandomInstance(SEED);
        INDArray data = Nd4j.rand(random, 1000, 4).muli(0.4).addi(0.5);
        // And the input file
        BinArrayFile file = BinArrayFile.createByKey(KPI_PATH, "maxReportTest");
        file.clear();
        file.write(data);
        file.close();
        // And the scalar report process
        ReportProcess process = ReportProcess.maxReport("maxReportTest")
                .build(KPI_PATH, REPORT_PATH, 100);

        // When process
        process.process();
        INDArray stats = CSVReader.createByName(REPORT_PATH, "maxReportTest.max.stats").read(100);

        // Then ...
        INDArray expected = Nd4j.createFromArray(
                (float) data.size(0),
                data.max(true, 1).minNumber().floatValue(),
                data.max(true, 1).maxNumber().floatValue(),
                data.max(true, 1).meanNumber().floatValue(),
                data.max(true, 1).stdNumber(true).floatValue(),
                Transforms.log(data.max(true, 1)).meanNumber().floatValue()
        ).reshape(1, 6);
        assertThat(stats, matrixCloseTo(expected, 1e-6));
    }

    @Test
    void meanReportTest() throws Throwable {
        // Given a random dataset
        Random random = Nd4j.getRandomFactory().getNewRandomInstance(SEED);
        INDArray data = Nd4j.randn(random, 1000, 1);
        // And the input file
        BinArrayFile file = BinArrayFile.createByKey(KPI_PATH, "meanReportTest");
        file.clear();
        file.write(data);
        file.close();
        // And the scalar report process
        ReportProcess process = ReportProcess.meanReport("meanReportTest")
                .build(KPI_PATH, REPORT_PATH, 100);

        // When process
        process.process();
        INDArray stats = CSVReader.createByName(REPORT_PATH, "meanReportTest.stats").read(100);

        // Then ...
        INDArray expected = Nd4j.createFromArray(
                (float) data.size(0),
                data.minNumber().floatValue(),
                data.maxNumber().floatValue(),
                data.meanNumber().floatValue(),
                data.stdNumber(true).floatValue(),
                0
        ).reshape(1, 6);
        assertThat(stats, matrixCloseTo(expected, 1e-6));
    }

    @Test
    void rmsAggregatorTest() {
        // Given an aggregator
        ReportProcess.Aggregator agg = ReportProcess.rmsAggregator();
        // And a dataset
        INDArray data = Nd4j.createFromArray(2f, 1f, 0f, 1f).reshape(4, 1);

        // When reset and add data twice
        agg.reset();
        agg.add(data);
        agg.add(data);

        assertEquals(8L, agg.numSamples());
        assertEquals(0f, agg.min());
        assertEquals(2f, agg.max());
        assertEquals((float) Math.sqrt(12f / 8), agg.value());
    }

    @Test
    void rmsReportTest() throws Throwable {
        // Given a random dataset
        Random random = Nd4j.getRandomFactory().getNewRandomInstance(SEED);
        INDArray data = Nd4j.randn(random, 1000, 1);
        // And the input file
        BinArrayFile file = BinArrayFile.createByKey(KPI_PATH, "rmsReportTest");
        file.clear();
        file.write(data);
        file.close();
        // And the scalar report process
        ReportProcess process = ReportProcess.rmsReport("rmsReportTest")
                .build(KPI_PATH, REPORT_PATH, 100);

        // When process
        process.process();
        INDArray stats = CSVReader.createByName(REPORT_PATH, "rmsReportTest.stats").read(100);

        // Then ...
        INDArray expected = Nd4j.createFromArray(
                (float) data.size(0),
                Transforms.abs(data).minNumber().floatValue(),
                Transforms.abs(data).maxNumber().floatValue(),
                Transforms.abs(data).meanNumber().floatValue(),
                Transforms.abs(data).stdNumber(true).floatValue(),
                Transforms.log(Transforms.abs(data)).meanNumber().floatValue()
        ).reshape(1, 6);
        assertThat(stats, matrixCloseTo(expected, 1e-6));
    }

    @Test
    void sumRmsReportTest() throws Throwable {
        // Given a random dataset
        Random random = Nd4j.getRandomFactory().getNewRandomInstance(SEED);
        INDArray data = Nd4j.randn(random, 1000, 4);
        // And the input file
        BinArrayFile file = BinArrayFile.createByKey(KPI_PATH, "sumRmsReportTest");
        file.clear();
        file.write(data);
        file.close();
        // And the scalar report process
        ReportProcess process = ReportProcess.sumRmsReport("sumRmsReportTest")
                .build(KPI_PATH, REPORT_PATH, 100);

        // When process
        process.process();
        INDArray stats = CSVReader.createByName(REPORT_PATH, "sumRmsReportTest.sum.stats").read(100);

        // Then ...
        INDArray sum = Transforms.abs(data.sum(true, 1));
        INDArray expected = Nd4j.createFromArray(
                (float) data.size(0),
                sum.minNumber().floatValue(),
                sum.maxNumber().floatValue(),
                sum.meanNumber().floatValue(),
                sum.stdNumber(true).floatValue(),
                Transforms.log(sum).meanNumber().floatValue()
        ).reshape(1, 6);
        assertThat(stats, matrixCloseTo(expected, 1e-5));
    }
}