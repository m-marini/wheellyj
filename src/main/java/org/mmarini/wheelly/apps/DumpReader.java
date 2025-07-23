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

import hu.akarnokd.rxjava3.swing.SwingObservable;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mmarini.swing.SwingUtils;
import org.mmarini.wheelly.apis.DumpRecord;
import org.mmarini.wheelly.swing.DumpRecordPanel;
import org.mmarini.wheelly.swing.DumpRecordsTable;
import org.mmarini.wheelly.swing.Messages;
import org.mmarini.wheelly.swing.RecordFilterPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.io.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.Math.max;
import static org.mmarini.wheelly.swing.Utils.center;
import static org.mmarini.wheelly.swing.Utils.createFrame;


public class DumpReader {
    //    private static final Dimension COMMAND_FRAME_SIZE = new Dimension(400, 800);
    private static final Logger logger = LoggerFactory.getLogger(DumpReader.class);

    /**
     * Returns the command line arguments parser
     */
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(DumpReader.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Show dump file.");
        parser.addArgument("--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("file")
                .nargs("?")
                .help("specify dump signal file");
        return parser;
    }

    /**
     * Runs the checks
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        DumpReader init;
        try {
            init = new DumpReader().init(args);
        } catch (ArgumentParserException ignored) {
            System.exit(4);
            return;
        } catch (Throwable e) {
            logger.atError().setCause(e).log("Error initializing dumper");
            System.exit(4);
            return;
        }
        try {
            init.run();
        } catch (Throwable e) {
            logger.atError().setCause(e).log("Error running dumper");
            System.exit(4);
        }
    }

    private final List<DumpRecord> lineRecords;
    private final DumpRecordsTable recordsTable;
    private final DumpRecordPanel detailPanel;
    private final JSplitPane splitPanel1;
    private final JSplitPane splitPanel2;
    private final JScrollPane scrollPanel;
    private final JMenuItem loadMenuItem;
    private final JMenuItem exitMenuItem;
    //    private final RecordFilterMenu filterMenu;
    private final RecordFilterPanel filterPanel;
    private final JFileChooser openFileChooser;
    private JFrame dumpReaderFrame;

    /**
     * Creates the check
     */
    public DumpReader() {
        this.lineRecords = new ArrayList<>();
        this.recordsTable = new DumpRecordsTable();
        this.detailPanel = new DumpRecordPanel();
        this.splitPanel1 = new JSplitPane();
        this.splitPanel2 = new JSplitPane();
        this.scrollPanel = new JScrollPane(recordsTable);
        this.loadMenuItem = SwingUtils.createMenuItem("DumpReader.loadMenuItem");
        this.exitMenuItem = SwingUtils.createMenuItem("DumpReader.exitMenuItem");
        //this.filterMenu = new RecordFilterMenu();
        this.filterPanel = new RecordFilterPanel();
        this.openFileChooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "Dump files", "log");
        openFileChooser.setFileFilter(filter);
        openFileChooser.setCurrentDirectory(new File("./"));
        createFlows();
    }

    /**
     * Returns the content panel
     */
    private Component createContentPane() {
        scrollPanel.setBorder(BorderFactory.createTitledBorder(Messages.getString("DumpReader.recordTable.title")));
        splitPanel2.setLeftComponent(scrollPanel);
        splitPanel2.setRightComponent(detailPanel);
        splitPanel1.setLeftComponent(filterPanel);
        splitPanel1.setRightComponent(splitPanel2);
        return splitPanel1;
    }

    /**
     * Creates the flows
     */
    private void createFlows() {
        SwingObservable.listSelection(recordsTable.getSelectionModel())
                .toFlowable(BackpressureStrategy.LATEST)
                .filter(ev -> !ev.getValueIsAdjusting())
                .doOnNext(this::handleRecordSelection)
                .subscribe();
        detailPanel.readOffset()
                .doOnNext(this::handleSetOffset)
                .subscribe();
        filterPanel.readFilters()
                .doOnNext(this::handleFilters)
                .subscribe();
        SwingObservable.actions(exitMenuItem)
                .toFlowable(BackpressureStrategy.LATEST)
                .doOnNext(ev -> dumpReaderFrame.dispose())
                .subscribe();
        SwingObservable.actions(loadMenuItem)
                .toFlowable(BackpressureStrategy.LATEST)
                .doOnNext(this::handleLoad)
                .subscribe();
    }

    /**
     * Handles windows close event
     *
     * @param windowEvent the event
     */
    private void handleClose(WindowEvent windowEvent) {
        dumpReaderFrame.dispose();
        logger.atInfo().log("Dump reader completed");
    }

    /**
     * Handles the filters
     *
     * @param filter the filter
     */
    private void handleFilters(Predicate<DumpRecord> filter) {
        logger.atDebug().log("{}", filter);
        List<DumpRecord> records = lineRecords.stream()
                .filter(filter)
                .collect(Collectors.toList());
        recordsTable.setRecords(records);
        detailPanel.setRecord(null);
    }

    /**
     * Handles the load file event
     *
     * @param event the event
     */
    private void handleLoad(ActionEvent event) {
        if (openFileChooser.showOpenDialog(dumpReaderFrame) == JFileChooser.APPROVE_OPTION) {
            File file = openFileChooser.getSelectedFile();
            try {
                loadFile(file);
            } catch (Throwable e) {
                logger.atError().setCause(e).log("Error loading file {}", file);
            }
        }
    }

    /**
     * Handles the window opened event
     *
     * @param event the event
     */
    private void handleOpened(WindowEvent event) {
        splitPanel2.setDividerLocation(
                max(scrollPanel.getPreferredSize().width,
                        recordsTable.getPreferredSize().width));
    }

    /**
     * Handles the record selection
     *
     * @param listSelectionEvent the event
     */
    private void handleRecordSelection(ListSelectionEvent listSelectionEvent) {
        recordsTable.getSelectedRecord()
                .ifPresent(record -> {
                    detailPanel.setRecord(record);
                    detailPanel.setOffset(recordsTable.getTimestampOffset());
                });
    }

    /**
     * Handles the set offset event
     *
     * @param offset the offset
     */
    private void handleSetOffset(Instant offset) {
        recordsTable.setTimestampOffset(offset);
        filterPanel.setOffset(offset);
    }

    /**
     * Initializes the reader
     *
     * @param args the command line arguments
     * @throws ArgumentParserException in case of error
     */
    private DumpReader init(String[] args) throws ArgumentParserException, IOException {
        detailPanel.setBorder(BorderFactory.createTitledBorder(Messages.getString("DumpReader.detailPanel.title")));
        splitPanel2.setOneTouchExpandable(true);

        ArgumentParser parser = createParser();
        try {
            Namespace parseArgs = parser.parseArgs(args);
            String filename = parseArgs.getString("file");
            if (filename != null) {
                File file = new File(filename);
                loadFile(file);
                openFileChooser.setSelectedFile(file);
            }
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            throw e;
        }
        return this;
    }

    /**
     * Loads the dump from the file
     *
     * @param file the file
     */
    private void loadFile(File file) throws IOException {
        loadFile(new FileReader(file));
    }

    /**
     * Loads the dump from reader
     *
     * @param reader the reader
     */
    private void loadFile(Reader reader) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(reader);
        int lineNumber = 1;
        for (; ; ) {
            String line = bufferedReader.readLine();
            if (line == null) {
                break;
            }
            try {
                DumpRecord record = DumpRecord.create(line);
                lineRecords.add(record);
            } catch (RuntimeException ex) {
                logger.atError().setCause(ex).log("Error loading record @{} {}", lineNumber, line);
            }
            lineNumber++;
        }
        bufferedReader.close();
        if (!lineRecords.isEmpty()) {
            Instant offset = lineRecords.getFirst().instant();
            recordsTable.setTimestampOffset(offset);
            detailPanel.setOffset(offset);
            filterPanel.setOffset(offset);
        }
        recordsTable.setRecords(lineRecords);
    }

    /**
     * Runs the check
     */
    private void run() {
        logger.info("Dump reader started.");

        this.dumpReaderFrame = center(createFrame(Messages.getString("DumpReader.title"),
                createContentPane()));

        JMenu fileMenu = SwingUtils.createMenu("DumpReader.fileMenu");
        fileMenu.add(loadMenuItem);
        fileMenu.add(new JSeparator());
        fileMenu.add(exitMenuItem);


        JMenuBar menuBar = new JMenuBar();
        menuBar.add(fileMenu);

        dumpReaderFrame.setJMenuBar(menuBar);

        Flowable<WindowEvent> windowsEvents = Observable.mergeArray(
                SwingObservable.window(dumpReaderFrame, SwingObservable.WINDOW_ACTIVE)
        ).toFlowable(BackpressureStrategy.LATEST);

        windowsEvents.filter(ev -> ev.getID() == WindowEvent.WINDOW_CLOSING)
                .doOnNext(this::handleClose)
                .subscribe();

        windowsEvents.filter(ev -> ev.getID() == WindowEvent.WINDOW_OPENED)
                .doOnNext(this::handleOpened)
                .subscribe();

        dumpReaderFrame.setVisible(true);
    }
}
