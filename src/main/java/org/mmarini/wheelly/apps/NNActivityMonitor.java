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
import org.mmarini.rl.agents.PPOAgent;
import org.mmarini.rl.envs.WithSignalsSpec;
import org.mmarini.rl.nets.TDNetwork;
import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.swing.Messages;
import org.mmarini.swing.SwingUtils;
import org.mmarini.wheelly.apis.RobotApi;
import org.mmarini.wheelly.apis.RobotControllerApi;
import org.mmarini.wheelly.apis.WheellyJsonSchemas;
import org.mmarini.wheelly.apis.WorldModeller;
import org.mmarini.wheelly.envs.EnvironmentApi;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.wheelly.swing.NNActivityPanel;
import org.mmarini.wheelly.swing.Utils;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.DoublePredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.lang.Math.min;
import static java.lang.String.format;
import static org.mmarini.wheelly.apps.Wheelly.WHEELLY_SCHEMA_YML;
import static org.mmarini.wheelly.swing.Utils.createFrame;
import static org.mmarini.yaml.Utils.fromFile;

/**
 * Application to monitor neural network activity
 */
public class NNActivityMonitor {
    public static final int SIM_PERIOD = 100;
    public static final int PLAY_DIVIDER = 3;
    public static final int BUFFER_SIZE = 1024;
    private static final Logger logger = LoggerFactory.getLogger(NNActivityMonitor.class);

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
    private final JButton forwardStep;
    private final JButton backStep;
    private final JToggleButton play;
    private final JToggleButton fastPlay;
    private final JButton stop;
    private final JButton findRewardBtn;
    private final JButton findPunishmentBtn;
    private final JFormattedTextField step;
    private final JTable dataTable;
    private final AbstractTableModel dataModel;
    private final JList<String> layers;
    private final JFormattedTextField reward;
    private final JFormattedTextField avgReward;
    private TDNetwork network;
    private Map<String, BinArrayFile> signalFiles;
    private int timeDivider;
    private long pos;
    private BinArrayFile rewardFile;
    private Map<String, INDArray> activity;
    private long size;
    private INDArray detailData;

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
        this.step = new JFormattedTextField();
        this.reward = new JFormattedTextField();
        this.avgReward = new JFormattedTextField();
        this.forwardStep = SwingUtils.createButton("NNActivityMonitor.forward");
        this.backStep = SwingUtils.createButton("NNActivityMonitor.backward");
        this.findRewardBtn = SwingUtils.createButton("NNActivityMonitor.findReward");
        this.findPunishmentBtn = SwingUtils.createButton("NNActivityMonitor.findPunishment");
        this.layers = new JList<>();
        this.dataModel = new AbstractTableModel() {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 1 ? Float.class : String.class;
            }

            @Override
            public int getColumnCount() {
                return 2;
            }

            @Override
            public String getColumnName(int column) {
                return Messages.getString(column == 0
                        ? "NNActivityMonitor.detailIndex.columnName"
                        : "NNActivityMonitor.detailValue.name");
            }

            @Override
            public int getRowCount() {
                return detailData != null ? (int) detailData.size(1) : 0;
            }

            @Override
            public Object getValueAt(int rowIndex, int columnIndex) {
                return columnIndex == 0
                        ? Messages.format("NNActivityMonitor.detailIndex.name", rowIndex)
                        : detailData != null
                        ? detailData.getFloat(0L, (long) rowIndex)
                        : Float.NaN;
            }
        };
        dataTable = new JTable(dataModel);

        step.setValue(0);
        step.setColumns(5);
        step.setEditable(false);
        step.setHorizontalAlignment(JTextField.RIGHT);

        reward.setValue(0F);
        reward.setColumns(10);
        reward.setEditable(false);
        reward.setHorizontalAlignment(JTextField.RIGHT);

        avgReward.setValue(0F);
        avgReward.setColumns(10);
        avgReward.setEditable(false);
        avgReward.setHorizontalAlignment(JTextField.RIGHT);

        timeLine.setPaintTicks(true);
        timeLine.setPaintLabels(true);
        timeLine.setPaintTrack(true);
        timeLine.setValue(0);

        layers.setVisibleRowCount(5);
        rewind.addActionListener(ev -> rewind());
        stop.addActionListener(ev -> stop());
        play.addActionListener(ev -> play());
        fastPlay.addActionListener(ev -> fastPlay());
        backStep.addActionListener(ev -> moveBackStep());
        findRewardBtn.addActionListener(ev -> findForReward(x -> x > 0));
        findPunishmentBtn.addActionListener(ev -> findForReward(x -> x < 0));
        forwardStep.addActionListener(ev -> moveForwardStep());
        layers.addListSelectionListener(this::selectedLayer);
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
                .modify("at,4,0").add(backStep)
                .modify("at,5,0").add(forwardStep)
                .modify("at,4,1").add(findPunishmentBtn)
                .modify("at,5,1").add(findRewardBtn)
                .modify("at,7,0 hfill weight,1,0").add(timeLine)
                .modify("at,7,1 nofill").add(step)
                .getContainer();
    }

    /**
     * Returns the content panel
     */
    private Component createContentPanel() {
        return new GridLayoutHelper<>(new JPanel()).modify("insets,5 center fill")
                .modify("at,0,0 weight,1,1").add(activityPanel)
                .modify("at,0,1 weight,1,0").add(createCommandBar())
                .modify("at,1,0 vspan,2 weight,1,1").add(createDetailPanel())
                .getContainer();
    }

    /**
     * Creates and returns the detail panel
     */
    private Component createDetailPanel() {
        Box layersPanel = Box.createHorizontalBox();
        layersPanel.add(new JScrollPane(layers));
        layersPanel.setBorder(BorderFactory.createTitledBorder(Messages.getString("NNActivityMonitor.layers.name")));
        return new GridLayoutHelper<>(Messages.messages(),
                new JPanel()).modify("insets,5")
                .modify("at,0,0 e").add("NNActivityMonitor.reward.name")
                .modify("at,1,0 w").add(reward)
                .modify("at,0,1 e").add("NNActivityMonitor.avgReward.name")
                .modify("at,1,1 w").add(avgReward)
                .modify("at,0,2 center hspan,2 noweight fill").add(layersPanel)
                .modify("at,0,3 weight,1,1").add(new JScrollPane(dataTable))
                .getContainer();
    }

    /**
     * Runs player at fast power
     */
    private void fastPlay() {
        if (fastPlay.isSelected()) {
            timeDivider = 1;
            play.setSelected(false);
            updatePlayer();
        } else {
            stop();
        }
    }

    /**
     * Finds next reward
     */
    private void findForReward(DoublePredicate criterion) {
        try {
            long p = pos + 1;
            while (p < size - 1) {
                long n = min(size - p, BUFFER_SIZE);
                rewardFile.seek(p);
                INDArray data = rewardFile.read(n);
                for (long i = 0; i < n; i++) {
                    if (criterion.test(data.getDouble(i, 1))) {
                        pos = i + p;
                        updatePlayer();
                        break;
                    }
                }
                p += n;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isPlaying() {
        return fastPlay.isSelected() || play.isSelected();
    }

    /**
     * Loads the network
     *
     * @throws IOException in case of error
     */
    private void loadNetwork() throws Throwable {
        JsonNode config = fromFile(args.getString("config"));
        WheellyJsonSchemas.instance().validateOrThrow(config, WHEELLY_SCHEMA_YML);

        logger.atInfo().log("Creating robot ...");
        RobotApi robot = AppYaml.robotFromJson(config);

        logger.atInfo().log("Creating controller ...");
        RobotControllerApi controller = AppYaml.controllerFromJson(config);
        controller.connectRobot(robot);

        logger.atInfo().log("Creating world modeller ...");
        WorldModeller worldModeller = AppYaml.modellerFromJson(config);
        worldModeller.setRobotSpec(robot.robotSpec());
        worldModeller.connectController(controller);

        logger.atInfo().log("Creating RL environment ...");
        EnvironmentApi environment = AppYaml.envFromJson(config);
        environment.connect(worldModeller);

        logger.atInfo().log("Create reward function ...");
        RewardFunction rewardFunc = AppYaml.rewardFromJson(config);
        environment.setRewardFunc(rewardFunc);

        logger.atInfo().log("Creating agent ...");
        Function<WithSignalsSpec, Agent> agentBuilder = Agent.fromFile(
                new File(Locator.locate("agent").getNode(config).asText()));
        try (Agent agent = agentBuilder.apply(environment)) {
            environment.connect(agent);
            if (agent instanceof PPOAgent ppoAgent) {
                this.network = ppoAgent.network();
                this.avgReward.setValue(ppoAgent.avgReward());
            } else {
                throw new IllegalArgumentException(
                        format("Wrong agent type %s", agent.getClass().getName()));
            }
        }
        this.layers.setListData(network.forwardSequence().reversed().toArray(String[]::new));

    }

    /**
     * Loads the signals
     */
    private void loadSignals() throws IOException {
        // Load reward file
        String source = args.getString("kpis");
        File sourcePath = new File(source);
        rewardFile = BinArrayFile.createByKey(sourcePath, "reward");
        size = rewardFile.size();

        // Extracts source keys
        String[] sourceKeys = network.sourceLayers().stream()
                .map(name -> "s0." + name)
                .toArray(String[]::new);
        // Creates the source files by source keys
        Map<String, BinArrayFile> signalFiles =
                KeyFileMap.create(sourcePath, sourceKeys);
        // Validates for the missing files
        List<String> missingFiles = Arrays.stream(sourceKeys)
                .filter(Predicate.not(signalFiles::containsKey))
                .toList();
        if (!missingFiles.isEmpty()) {
            throw new RuntimeException(format("Missing kpis files %s in %s",
                    String.join(", ", missingFiles),
                    sourcePath));
        }
        // Validates for number of records
        Collection<BinArrayFile> files = signalFiles.values();
        KeyFileMap.validateSizes(files);
        this.signalFiles = KeyFileMap.children(signalFiles, "s0");
    }

    private void moveBackStep() {
        pos--;
        updatePlayer();
    }

    private void moveForwardStep() {
        pos++;
        updatePlayer();
    }

    /**
     * Move the timeline
     */
    private void moveTimeline() {
        pos = timeLine.getValue();
        updatePlayer();
    }

    /**
     * Runs player at normal power
     */
    private void play() {
        if (play.isSelected()) {
            timeDivider = PLAY_DIVIDER;
            fastPlay.setSelected(false);
            updatePlayer();
        } else {
            stop();
        }
    }

    /**
     * Rewind the player
     */
    private void rewind() {
        pos = 0;
        stop();
        updatePlayer();
    }

    /**
     * Runs the application
     */
    private void run() throws Throwable {
        loadNetwork();

        // Prepare the input data
        loadSignals();

        long size = rewardFile.size();
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
                .filter(ignored -> isPlaying())
                .filter(t -> t % timeDivider == 0)
                .doOnNext(ignored -> {
                    pos++;
                    updatePlayer();
                })
                .subscribe();
    }

    private void selectedLayer(ListSelectionEvent event) {
        if (!event.getValueIsAdjusting()) {
            updateDetails();
        }
    }

    /**
     * Stop the player
     */
    private void stop() {
        play.setSelected(false);
        fastPlay.setSelected(false);
        updatePlayer();
    }

    /**
     * Updates the detail panel with current activity if any
     */
    private void updateDetails() {
        String key = layers.getSelectedValue();
        detailData = activity != null && key != null ? activity.get(key + ".values") : null;
        dataModel.fireTableDataChanged();
    }

    /**
     * Processes signal from player
     */
    void updatePlayer() {
        if (pos < size) {
            try {
                rewardFile.seek(pos);
                INDArray rewardRecord = rewardFile.read(1);
                reward.setValue(rewardRecord.getFloat(0, 0));
                KeyFileMap.seek(signalFiles, pos);
                Map<String, INDArray> signals = KeyFileMap.read(signalFiles, 1);
                if (signals != null) {
                    this.activity = network.forward(signals).state().values();
                    if (activity != null) {
                        activityPanel.setActivity(activity);
                    }
                } else {
                    activity = null;
                }
                rewind.setEnabled(pos > 0);
            } catch (IOException ex) {
                // EOF
                play.setSelected(false);
                fastPlay.setSelected(false);
            }
        } else {
            // EOF
            play.setSelected(false);
            fastPlay.setSelected(false);
        }
        updateDetails();
        step.setValue((int) pos);
        timeLine.setValue((int) pos);
        rewind.setEnabled(pos > 0);
        stop.setEnabled(isPlaying());
        play.setEnabled(pos <= size - 1);
        fastPlay.setEnabled(pos <= size - 1);
        backStep.setEnabled(!isPlaying() && pos > 0);
        forwardStep.setEnabled(!isPlaying() && pos <= size - 1);
        timeLine.setEnabled(!isPlaying());
    }
}
