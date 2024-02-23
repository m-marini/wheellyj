/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.apps;

import io.reactivex.rxjava3.functions.Supplier;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mmarini.ParallelProcess;
import org.mmarini.Tuple2;
import org.mmarini.rl.agents.BinArrayFile;
import org.mmarini.rl.agents.BinArrayFileMap;
import org.mmarini.rl.agents.CSVWriter;
import org.mmarini.wheelly.swing.Messages;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Runs the process to produce report data about learning kpis
 */
public class ToCsv {
    public static final long DEFAULT_BATCH_SIZE = 300L;
    private static final Logger logger = LoggerFactory.getLogger(ToCsv.class);

    static {
        Nd4j.zeros(1);
    }

    /**
     * Returns the argument parser
     */
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(ToCsv.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Run a session of batch training.");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("-b", "--batchSize")
                .setDefault(DEFAULT_BATCH_SIZE)
                .type(Long.class)
                .help("batch size");
        parser.addArgument("sourcePath")
                .required(true)
                .help("specify the source path");
        parser.addArgument("destPath")
                .required(true)
                .help("specify the destination path");
        return parser;
    }

    /**
     * @param args command line arguments
     */
    public static void main(String[] args) {
        ArgumentParser parser = createParser();
        try {
            new ToCsv(parser.parseArgs(args)).start();
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        } catch (IOException e) {
            logger.atError().setCause(e).log("Error generating report");
            System.exit(2);
        }
    }

    private final long batchSize;
    protected Namespace args;
    private File destPath;

    /**
     * Creates the report application
     *
     * @param args the parsed arguments
     */
    protected ToCsv(Namespace args) {
        this.args = args;
        this.batchSize = args.getLong("batchSize");
    }

    protected void start() throws IOException {
        this.destPath = new File(args.getString("destPath"));
        BinArrayFileMap sources = BinArrayFileMap.create(new File(args.getString("sourcePath")), "");
        try {
            ParallelProcess.collector(
                            Tuple2.stream(sources.files())
                                    .map(t -> t.setV2((toCsv(t._1, t._2))))
                                    .collect(Tuple2.toMap()))
                    .run();
        } finally {
            sources.close();
        }
    }

    private Supplier<Object> toCsv(String key, BinArrayFile binFile) {
        return () -> {
            try {
                CSVWriter out = CSVWriter.createByKey(destPath, key);
                try {
                    logger.atInfo().log("Converting {} to {}",
                            binFile.file(),
                            out.file());
                    for (; ; ) {
                        INDArray data = binFile.read(batchSize);
                        if (data == null) {
                            break;
                        }
                        out.write(data);
                    }
                    logger.atInfo().log("Converted {} to {}",
                            binFile.file(),
                            out.file());
                } finally {
                    out.close();
                }
                return this;
            } finally {
                binFile.close();
            }
        };
    }
}