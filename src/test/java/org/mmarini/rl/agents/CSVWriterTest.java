package org.mmarini.rl.agents;

import org.junit.jupiter.api.Test;
import org.nd4j.linalg.factory.Nd4j;

import java.io.File;
import java.io.IOException;

class CSVWriterTest {
    static {
        Nd4j.zeros(1, 1);
    }

    @Test
    void createByNameTest() throws IOException {
        CSVWriter writer = CSVWriter.createByKey(new File("tmp"), "a.b.c");
        writer.write(Nd4j.zeros(1));
        writer.close();
    }

    @Test
    void writeTest1() throws IOException {
        CSVWriter writer = new CSVWriter(new File("tmp/test/data.csv"));

        writer.write(Nd4j.rand(900));
        writer.write(Nd4j.rand(900));
        writer.flush();

        writer.write(Nd4j.rand(900));
        writer.write(Nd4j.rand(10000));
        writer.write(Nd4j.rand(20000));

        writer.close();
    }

    @Test
    void writeTest100() throws IOException {
        CSVWriter writer = new CSVWriter(new File("tmp/test/data.csv"));

        writer.write(Nd4j.rand(9, 100));
        writer.write(Nd4j.rand(9, 100));
        writer.flush();

        writer.write(Nd4j.rand(9, 100));
        writer.write(Nd4j.rand(10, 100));
        writer.write(Nd4j.rand(20, 100));

        writer.close();
    }

    @Test
    void writeTest3x4() throws IOException {
        CSVWriter writer = new CSVWriter(new File("tmp/test/data.csv"));

        writer.write(Nd4j.rand(10, 3, 4));
        writer.write(Nd4j.rand(10, 3, 4));
        writer.flush();

        writer.write(Nd4j.rand(10, 3, 4));
        writer.write(Nd4j.rand(10000, 3, 4));

        writer.close();
    }

    @Test
    void writeTestnx1() throws IOException {
        CSVWriter writer = new CSVWriter(new File("tmp/test/data.csv"));

        writer.write(Nd4j.rand(900, 1));
        writer.write(Nd4j.rand(900, 1));
        writer.flush();

        writer.write(Nd4j.rand(900, 1));
        writer.write(Nd4j.rand(10000, 1));
        writer.write(Nd4j.rand(20000, 1));

        writer.close();
    }
}