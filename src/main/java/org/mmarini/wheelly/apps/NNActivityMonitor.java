/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
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
import hu.akarnokd.rxjava3.swing.SwingObservable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mmarini.rl.agents.Agent;
import org.mmarini.rl.agents.BinArrayFile;
import org.mmarini.rl.agents.KeyFileMap;
import org.mmarini.rl.agents.TDAgentSingleNN;
import org.mmarini.rl.nets.TDNetwork;
import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.swing.SwingUtils;
import org.mmarini.wheelly.envs.RobotEnvironment;
import org.mmarini.wheelly.swing.Messages;
import org.mmarini.wheelly.swing.NNActivityPanel;
import org.mmarini.wheelly.swing.Utils;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static java.lang.String.format;
import static org.mmarini.wheelly.apps.Wheelly.WHEELLY_SCHEMA_YML;
import static org.mmarini.wheelly.swing.Utils.createFrame;
import static org.mmarini.yaml.Utils.fromFile;

/**
 * Application to monitor neural network activity
 */
public class NNActivityMonitor {
    private static final Logger logger = LoggerFactory.getLogger(NNActivityMonitor.class);
    public static final int SIM_PERIOD = 100;
    public static final int PLAY_DIVIDER = 3;

    static {
        Nd4j.zeros(1);
    }

    /**
     * Returns the argument parser
     */
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(NNActivityMonitor.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Run a session of batch training.");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("-c", "--config")
                .setDefault("wheelly.yml")
                .help("specify yaml configuration file");
        parser.addArgument("kpis")
                .type(String.class)
                .help("the kpis path");
        return parser;
    }

    /**
     * Runs the app
     *
     * @param args the command line argument s
     */
    public static void main(String[] args) {
        ArgumentParser parser = createParser();
        try {
            Namespace arguments = parser.parseArgs(args);
            new NNActivityMonitor(arguments).run();
        } catch (ArgumentParserException ex) {
            parser.handleError(ex);
            System.exit(1);
        } catch (Throwable ex) {
            logger.atError().setCause(ex).log("Error running application");
            System.exit(8);
        }
    }

    private final Namespace args;
    private final NNActivityPanel activityPanel;
    private final JSlider timeLine;
    private final JButton rewind;
    private final JToggleButton play;
    private final JToggleButton fastPlay;
    private final JButton stop;

    private TDNetwork network;
    private Map<String, BinArrayFile> signalFiles;
    private boolean playing;
    private int timeDivider;
    private BinArrayFile refFile;

    /**
     * Loads the network
     *
     * @throws IOException in case of error
     */
    private void loadNetwork() throws IOException {
        JsonNode config = fromFile(args.getString("config"));
        RobotEnvironment environment = AppYaml.envFromJson(config, Locator.root(), WHEELLY_SCHEMA_YML);
        Locator agentLocator = Locator.locate(Locator.locate("agent").getNode(config).asText());
        if (agentLocator.getNode(config).isMissingNode()) {
            throw new IllegalArgumentException(format("Missing node %s", agentLocator));
        }
        Agent agent = Agent.fromConfig(config, agentLocator, environment);
        if (agent instanceof TDAgentSingleNN tdagent) {
            this.network = tdagent.network();
        } else {
            throw new IllegalArgumentException(
                    format("Wrong agent type %s", agent.getClass().getName()));
        }
    }

    /**
     * Creates the application
     *
     * @param args the parsed command line arguments
     */
    protected NNActivityMonitor(Namespace args) {
        this.args = args;
        this.activityPanel = new NNActivityPanel();
        this.timeLine = new JSlider(JSlider.HORIZONTAL);
        this.rewind = SwingUtils.createButton("NNActivityMonitor.rewind");
        this.play = SwingUtils.createToolBarToggleButton("NNActivityMonitor.play");
        this.fastPlay = SwingUtils.createToolBarToggleButton("NNActivityMonitor.fastPlay");
        this.stop = SwingUtils.createButton("NNActivityMonitor.stop");

        timeLine.setPaintTicks(true);
        timeLine.setPaintLabels(true);
        timeLine.setPaintTrack(true);
        timeLine.setValue(0);

        rewind.addActionListener(ev -> rewind());
        stop.addActionListener(ev -> stop());
        play.addActionListener(ev -> play());
        fastPlay.addActionListener(ev -> fastPlay());
        SwingObservable.change(timeLine)
                .filter(ignored -> !timeLine.getValueIsAdjusting())
                .doOnNext(ev -> moveTimeline())
                .subscribe();
    }

    /**
     * Returns the command bar
     */
    private Container createCommandBar() {
        return new GridLayoutHelper<>(new JPanel()).modify("insets,2 center")
                .modify("at,0,0").add(rewind)
                .modify("at,1,0").add(stop)
                .modify("at,2,0").add(play)
                .modify("at,3,0").add(fastPlay)
                .modify("at,4,0 hfill weight,1,0").add(timeLine)
                .getContainer();
    }

    /**
     * Returns the content panel
     */
    private Component createContentPanel() {
        return new GridLayoutHelper<>(new JPanel()).modify("insets,5 center fill")
                .modify("at,0,0 weight,1,1").add(activityPanel)
                .modify("at,0,1 weight,1,0").add(createCommandBar())
                .getContainer();
    }

    /**
     * Runs player at fast speed
     */
    private void fastPlay() {
        if (fastPlay.isSelected()) {
            playing = true;
            timeDivider = 1;
            play.setSelected(false);
            timeLine.setEnabled(false);
        } else {
            stop();
        }
    }

    /**
     * Loads the signals
     */
    private void loadSignals() throws IOException {
        String source = args.getString("kpis");
        String[] sourceKeys = network.sourceLayers().stream()
                .map(name -> "s0." + name)
                .toArray(String[]::new);
        File sourcePath = new File(source);
        Map<String, BinArrayFile> signalFiles =
                KeyFileMap.create(sourcePath, sourceKeys);
        List<String> missingFiles = Arrays.stream(sourceKeys)
                .filter(Predicate.not(signalFiles::containsKey))
                .toList();
        if (!missingFiles.isEmpty()) {
            throw new RuntimeException(format("Missing kpis files %s in %s",
                    String.join(", ", missingFiles),
                    sourcePath));
        }
        KeyFileMap.validateSize(signalFiles.values());
        this.signalFiles = KeyFileMap.children(signalFiles, "s0");
    }

    /**
     * Move the timeline
     */
    private void moveTimeline() {
        try {
            int pos = timeLine.getValue();
            if (refFile.position() != pos) {
                stop();
                KeyFileMap.seek(this.signalFiles, pos);
                Map<String, INDArray> activity = processNextSignal();
                if (activity != null) {
                    activityPanel.setActivity(activity);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Runs player at normal speed
     */
    private void play() {
        if (play.isSelected()) {
            playing = true;
            timeDivider = PLAY_DIVIDER;
            fastPlay.setSelected(false);
            timeLine.setEnabled(false);
        } else {
            stop();
        }
    }

    /**
     * Returns the next signal from player
     */
    private Map<String, INDArray> processNextSignal() throws IOException {
        Map<String, INDArray> signals = KeyFileMap.read(signalFiles, 1);
        if (signals == null) {
            stop();
            return null;
        } else {
            long pos = refFile.position();
            timeLine.setValue((int) pos);
            return network.forward(signals).values();
        }
    }

    /**
     * Rewind the player
     */
    private void rewind() {
        try {
            KeyFileMap.seek(signalFiles, 0);
            Map<String, INDArray> activity = processNextSignal();
            if (activity != null) {
                activityPanel.setActivity(activity);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        timeLine.setValue(0);
        playing = false;
        play.setSelected(false);
        fastPlay.setSelected(false);
    }

    /**
     * Runs the application
     */
    private void run() throws IOException {
        loadNetwork();

        // Prepare the input data
        loadSignals();

        this.refFile = this.signalFiles.values().stream().findAny().orElseThrow();
        long size = refFile.size();
        timeLine.setMaximum((int) size);
        timeLine.setMajorTickSpacing((int) (size / 5));
        timeLine.setMinorTickSpacing((int) (size / 20));

        activityPanel.configure(network);

        JFrame frame = createFrame(Messages.getString("NNActivityMonitor.title"), createContentPanel());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Utils.center(frame);
        frame.setVisible(true);

        rewind();

        Flowable.interval(SIM_PERIOD, TimeUnit.MILLISECONDS).
                observeOn(Schedulers.computation())
                .filter(ignoerd -> playing)
                .filter(t -> t % timeDivider == 0)
                .doOnNext(ignored -> {
                    Map<String, INDArray> activity = processNextSignal();
                    if (activity != null) {
                        activityPanel.setActivity(activity);
                    }
                })
                .subscribe();
    }

    /**
     * Stop the player
     */
    private void stop() {
        playing = false;
        play.setSelected(false);
        fastPlay.setSelected(false);
        timeLine.setEnabled(true);
    }
}
