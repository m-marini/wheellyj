/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.swing;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.swing.Messages;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Configures the motion parameters
 */
public class MotionConfigPanel extends JPanel {
    public static final Pattern ccParser = Pattern.compile("^cc,(\\d+),(\\d+),(\\d+)$");
    public static final Pattern csParser = Pattern.compile("^cs,(\\d+)$");
    public static final Pattern tcslParser = Pattern.compile("^tcsl,([-+]?\\d+),([-+]?\\d+),([-+]?\\d+),([-+]?\\d+),([-+]?\\d+),([-+]?\\d+),([-+]?\\d+),([-+]?\\d+),([-+]?\\d+),([-+]?\\d+),([-+]?\\d+),([-+]?\\d+)$");
    public static final Pattern tcsrParser = Pattern.compile("^tcsr,([-+]?\\d+),([-+]?\\d+),([-+]?\\d+),([-+]?\\d+),([-+]?\\d+),([-+]?\\d+),([-+]?\\d+),([-+]?\\d+),([-+]?\\d+),([-+]?\\d+),([-+]?\\d+),([-+]?\\d+)$");

    /**
     * Returns the new commands list with a single command changed or null if not changed
     *
     * @param commands the command list
     * @param cmd      the command
     */
    private static String[] changeCommands(String[] commands, String cmd) {
        String prefix = cmd.split(",")[0] + ",";
        boolean set = false;
        boolean changed = false;
        for (int i = 0; i < commands.length; i++) {
            String command = commands[i];
            if (command.startsWith(prefix)) {
                set = true;
                if (!command.equals(cmd)) {
                    changed = true;
                    commands[i] = cmd;
                }
            }
        }
        if (!set) {
            // command unset, append it
            changed = true;
            commands = Arrays.copyOf(commands, commands.length + 1);
            commands[commands.length - 1] = cmd;
        }
        return changed ? commands : null;
    }

    private final JSpinner motionDecayFilter;
    private final JSpinner maxRotSpeed;
    private final JSpinner minRotRange;
    private final JSpinner maxRotRange;
    private final JSpinner leftFeedbackForward;
    private final JSpinner leftFeedbackBackward;
    private final JSpinner rightFeedbackForward;
    private final JSpinner rightFeedbackBackward;
    private final JSpinner lif0;
    private final JSpinner lifx;
    private final JSpinner ldf0;
    private final JSpinner ldfx;
    private final JSpinner lib0;
    private final JSpinner libx;
    private final JSpinner ldb0;
    private final JSpinner ldbx;
    private final JSpinner leftAx;
    private final JSpinner leftAlpha;
    private final JSpinner rif0;
    private final JSpinner rifx;
    private final JSpinner rdf0;
    private final JSpinner rdfx;
    private final JSpinner rib0;
    private final JSpinner ribx;
    private final JSpinner rdb0;
    private final JSpinner rdbx;
    private final JSpinner rightAx;
    private final JSpinner rightAlpha;
    private final PublishProcessor<String[]> commandsFlow;
    private final JSpinner[] allFields;
    private final JTextArea commandsArea;
    private String[] commands;
    private boolean lockChanges;

    /**
     * Creates the panel
     */
    public MotionConfigPanel() {
        this.motionDecayFilter = new JSpinner();

        this.maxRotSpeed = new JSpinner();
        this.minRotRange = new JSpinner();
        this.maxRotRange = new JSpinner();

        this.leftFeedbackForward = new JSpinner();
        this.leftFeedbackBackward = new JSpinner();

        this.rightFeedbackForward = new JSpinner();
        this.rightFeedbackBackward = new JSpinner();

        this.lif0 = new JSpinner();
        this.lifx = new JSpinner();
        this.ldf0 = new JSpinner();
        this.ldfx = new JSpinner();
        this.lib0 = new JSpinner();
        this.libx = new JSpinner();
        this.ldb0 = new JSpinner();
        this.ldbx = new JSpinner();
        this.leftAx = new JSpinner();
        this.leftAlpha = new JSpinner();

        this.rif0 = new JSpinner();
        this.rifx = new JSpinner();
        this.rdf0 = new JSpinner();
        this.rdfx = new JSpinner();
        this.rib0 = new JSpinner();
        this.ribx = new JSpinner();
        this.rdb0 = new JSpinner();
        this.rdbx = new JSpinner();
        this.rightAx = new JSpinner();
        this.rightAlpha = new JSpinner();

        this.commandsFlow = PublishProcessor.create();

        this.commandsArea = new JTextArea();

        this.allFields = new JSpinner[]{lif0, lifx, ldf0, ldfx,
                lib0, libx, ldb0, ldbx,
                leftAx, leftAlpha,
                rif0, rifx, rdf0, rdfx,
                rib0, ribx, rdb0, rdbx,
                rightAx, rightAlpha,
                leftFeedbackForward, leftFeedbackBackward,
                rightFeedbackForward, rightFeedbackBackward,
                minRotRange, maxRotRange, maxRotSpeed,
                motionDecayFilter};

        init();
        createContent();
        createFlow();
    }

    /**
     * Sets the configuration parameters from text lines
     *
     * @param commands the test lines
     */
    public void commands(String[] commands) {
        this.lockChanges = true;
        for (String command : commands) {
            Matcher matcher = ccParser.matcher(command);
            if (matcher.matches()) {
                minRotRange.setValue(Integer.parseInt(matcher.group(1)));
                maxRotRange.setValue(Integer.parseInt(matcher.group(2)));
                maxRotSpeed.setValue(Integer.parseInt(matcher.group(3)));
                continue;
            }
            matcher = csParser.matcher(command);
            if (matcher.matches()) {
                motionDecayFilter.setValue(Integer.parseInt(matcher.group(1)));
                continue;
            }
            matcher = tcslParser.matcher(command);
            if (matcher.matches()) {
                lif0.setValue(Integer.parseInt(matcher.group(1)));
                lifx.setValue(Integer.parseInt(matcher.group(2)));
                ldf0.setValue(Integer.parseInt(matcher.group(3)));
                ldfx.setValue(Integer.parseInt(matcher.group(4)));
                lib0.setValue(Integer.parseInt(matcher.group(5)));
                libx.setValue(Integer.parseInt(matcher.group(6)));
                ldb0.setValue(Integer.parseInt(matcher.group(7)));
                ldbx.setValue(Integer.parseInt(matcher.group(8)));
                leftFeedbackForward.setValue(Long.parseLong(matcher.group(9)));
                leftFeedbackBackward.setValue(Long.parseLong(matcher.group(10)));
                leftAlpha.setValue(Integer.parseInt(matcher.group(11)));
                leftAx.setValue(Integer.parseInt(matcher.group(12)));
                continue;
            }
            matcher = tcsrParser.matcher(command);
            if (matcher.matches()) {
                rif0.setValue(Integer.parseInt(matcher.group(1)));
                rifx.setValue(Integer.parseInt(matcher.group(2)));
                rdf0.setValue(Integer.parseInt(matcher.group(3)));
                rdfx.setValue(Integer.parseInt(matcher.group(4)));
                rib0.setValue(Integer.parseInt(matcher.group(5)));
                ribx.setValue(Integer.parseInt(matcher.group(6)));
                rdb0.setValue(Integer.parseInt(matcher.group(7)));
                rdbx.setValue(Integer.parseInt(matcher.group(8)));
                rightFeedbackForward.setValue(Long.parseLong(matcher.group(9)));
                rightFeedbackBackward.setValue(Long.parseLong(matcher.group(10)));
                rightAlpha.setValue(Integer.parseInt(matcher.group(11)));
                rightAx.setValue(Integer.parseInt(matcher.group(12)));
            }
        }
        this.lockChanges = false;
        this.commands = commands;
        this.commandsArea.setText(String.join("\n", commands));
        onValuesChanged(null);
    }

    /**
     * Returns the commands from current panel values
     */
    private String[] configs() {
        return new String[]{
                "cs," + motionDecayFilter.getValue(),
                "tcsl," + lif0.getValue()
                        + "," + lifx.getValue()
                        + "," + ldf0.getValue()
                        + "," + ldfx.getValue()
                        + "," + lib0.getValue()
                        + "," + libx.getValue()
                        + "," + ldb0.getValue()
                        + "," + ldbx.getValue()
                        + "," + leftFeedbackForward.getValue()
                        + "," + leftFeedbackBackward.getValue()
                        + "," + leftAlpha.getValue()
                        + "," + leftAx.getValue(),
                "tcsr," + rif0.getValue()
                        + "," + rifx.getValue()
                        + "," + rdf0.getValue()
                        + "," + rdfx.getValue()
                        + "," + rib0.getValue()
                        + "," + ribx.getValue()
                        + "," + rdb0.getValue()
                        + "," + rdbx.getValue()
                        + "," + rightFeedbackForward.getValue()
                        + "," + rightFeedbackBackward.getValue()
                        + "," + rightAlpha.getValue()
                        + "," + rightAx.getValue(),
                "cc," + minRotRange.getValue()
                        + "," + maxRotRange.getValue()
                        + "," + maxRotSpeed.getValue()};
    }

    /**
     * Creates the content
     */
    private void createContent() {
        JPanel motorController = new GridLayoutHelper<>(new JPanel()).modify("insets,2")
                .modify("at,1,0").add("MotionConfigPanel.left")
                .modify("at,2,0").add("MotionConfigPanel.right")
                .modify("at,0,1 w").add("MotionConfigPanel.fi0")
                .modify("at,0,2").add("MotionConfigPanel.fix")
                .modify("at,0,3").add("MotionConfigPanel.fd0")
                .modify("at,0,4").add("MotionConfigPanel.fdx")
                .modify("at,0,5").add("MotionConfigPanel.bi0")
                .modify("at,0,6").add("MotionConfigPanel.bix")
                .modify("at,0,7").add("MotionConfigPanel.bd0")
                .modify("at,0,8").add("MotionConfigPanel.bdx")
                .modify("at,0,9").add("MotionConfigPanel.muForward")
                .modify("at,0,10").add("MotionConfigPanel.muBackward")
                .modify("at,0,11").add("MotionConfigPanel.alpha")
                .modify("at,0,12").add("MotionConfigPanel.ax")
                .modify("at,1,1 e hw,1").add(lif0)
                .modify("at,1,2").add(lifx)
                .modify("at,1,3").add(ldf0)
                .modify("at,1,4").add(ldfx)
                .modify("at,1,5").add(lib0)
                .modify("at,1,6").add(libx)
                .modify("at,1,7").add(ldb0)
                .modify("at,1,8").add(ldbx)
                .modify("at,1,9").add(leftFeedbackForward)
                .modify("at,1,10").add(leftFeedbackBackward)
                .modify("at,1,11").add(leftAlpha)
                .modify("at,1,12").add(leftAx)
                .modify("at,2,1").add(rif0)
                .modify("at,2,2").add(rifx)
                .modify("at,2,3").add(rdf0)
                .modify("at,2,4").add(rdfx)
                .modify("at,2,5").add(rib0)
                .modify("at,2,6").add(ribx)
                .modify("at,2,7").add(rdb0)
                .modify("at,2,8").add(rdbx)
                .modify("at,2,9").add(rightFeedbackForward)
                .modify("at,2,10").add(rightFeedbackBackward)
                .modify("at,2,11").add(rightAlpha)
                .modify("at,2,12").add(rightAx)
                .getContainer();
        motorController.setBorder(BorderFactory.createTitledBorder(Messages.getString("MotionConfigPanel.motorController.title")));

        JPanel motionController = new GridLayoutHelper<>(new JPanel()).modify("insets,2")
                .modify("at,0,0 w").add("MotionConfigPanel.minRotRange")
                .modify("at,0,1").add("MotionConfigPanel.maxRotRange")
                .modify("at,0,2").add("MotionConfigPanel.maxRotSpeed")
                .modify("at,0,3").add("MotionConfigPanel.motionDecayFilter")
                .modify("at,1,0 e hw,1").add(minRotRange)
                .modify("at,1,1").add(maxRotRange)
                .modify("at,1,2").add(maxRotSpeed)
                .modify("at,1,3").add(motionDecayFilter)
                .getContainer();
        motionController.setBorder(BorderFactory.createTitledBorder(Messages.getString("MotionConfigPanel.motionController.title")));

        //JScrollPane commandsPanel = new JScrollPane(commandsArea);

        new GridLayoutHelper<>(this).modify("insets,2 nw")
                .modify("at,0,0").add(motorController)
                .modify("at,1,0").add(motionController)
                .modify("at,0,1 hspan hw,1 fill").add(commandsArea);
    }

    /**
     * Creates the event flow
     */
    private void createFlow() {
        for (JSpinner f : allFields) {
            f.addChangeListener(this::onValuesChanged);
        }
    }

    /**
     * Returns the frame for the panel
     */
    public JFrame createFrame() {
        JScrollPane scroll = new JScrollPane(this);
        JFrame frame = new JFrame(Messages.getString("MotionConfigPanel.title"));
        Container contentPane = frame.getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(scroll, BorderLayout.CENTER);
        frame.pack();
        return frame;
    }

    /**
     * Initialises the field properties
     */
    private void init() {
        minRotRange.setModel(new SpinnerNumberModel(0, 0, 180, 1));
        maxRotRange.setModel(new SpinnerNumberModel(0, 0, 180, 1));
        maxRotSpeed.setModel(new SpinnerNumberModel(1, 0, 20, 1));
        motionDecayFilter.setModel(new SpinnerNumberModel(1, 1, 10000, 1));
        lif0.setModel(new SpinnerNumberModel(0, 0, 4095, 1));
        lifx.setModel(new SpinnerNumberModel(0, 0, 4095, 1));
        ldf0.setModel(new SpinnerNumberModel(0, 0, 4095, 1));
        ldfx.setModel(new SpinnerNumberModel(0, 0, 4095, 1));
        lib0.setModel(new SpinnerNumberModel(0, -4095, 0, 1));
        libx.setModel(new SpinnerNumberModel(0, 0, 4095, 1));
        ldb0.setModel(new SpinnerNumberModel(0, -4095, 0, 1));
        ldbx.setModel(new SpinnerNumberModel(0, 0, 4095, 1));
        leftFeedbackForward.setModel(new SpinnerNumberModel(0, 0, 2000000, 1));
        leftFeedbackBackward.setModel(new SpinnerNumberModel(0, 0, 2000000, 1));
        leftAlpha.setModel(new SpinnerNumberModel(0, 0, 100, 1));
        leftAx.setModel(new SpinnerNumberModel(0, 0, 16383, 1));
        rif0.setModel(new SpinnerNumberModel(0, 0, 4095, 1));
        rifx.setModel(new SpinnerNumberModel(0, 0, 4095, 1));
        rdf0.setModel(new SpinnerNumberModel(0, 0, 4095, 1));
        rdfx.setModel(new SpinnerNumberModel(0, 0, 4095, 1));
        rib0.setModel(new SpinnerNumberModel(0, -4095, 0, 1));
        ribx.setModel(new SpinnerNumberModel(0, 0, 4095, 1));
        rdb0.setModel(new SpinnerNumberModel(0, -4095, 0, 1));
        rdbx.setModel(new SpinnerNumberModel(0, 0, 4095, 1));
        rightFeedbackForward.setModel(new SpinnerNumberModel(0, 0, 2000000, 1));
        rightFeedbackBackward.setModel(new SpinnerNumberModel(0, 0, 2000000, 1));
        rightAlpha.setModel(new SpinnerNumberModel(0, 0, 100, 1));
        rightAx.setModel(new SpinnerNumberModel(0, 0, 16383, 1));
        commandsArea.setRows(8);
        commandsArea.setEditable(false);
        commandsArea.setFont(Font.decode(Font.MONOSPACED));
    }

    /**
     * Handles the change value  changed
     *
     * @param event the event
     */
    private void onValuesChanged(ChangeEvent event) {
        if (!lockChanges) {
            boolean changed = false;
            String[] commands1 = Arrays.copyOf(commands, commands.length);
            for (String cmd : configs()) {
                String[] cmds = changeCommands(commands1, cmd);
                if (cmds != null) {
                    commands1 = cmds;
                    changed = true;
                }
            }
            if (changed) {
                commands = commands1;
                this.commandsArea.setText(String.join("\n", commands));
                commandsFlow.onNext(commands);
            }
        }
    }

    /**
     * Returns the commands flow
     */
    public Flowable<String[]> readCommands() {
        return commandsFlow;
    }
}
