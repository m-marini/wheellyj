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

package org.mmarini.wheelly.swing;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.mmarini.Tuple2;
import org.mmarini.swing.GridLayoutHelper;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;

import static java.lang.Math.abs;
import static org.mmarini.Utils.zipWithIndex;

/**
 * Controls the learning parameters via UI
 */
public class LearnPanel extends JPanel {
    public static final Dimension DEFAULT_PREFERED_SIZE = new Dimension(500, 300);
    private static final double[] VALUES = new double[]{
            1e-6,
            3e-6,
            10e-6,
            30e-6,
            100e-6,
            300e-6,
            1e-3,
            3e-3,
            10e-3,
            30e-3,
            100e-3
    };
    private static final Dictionary<Integer, JComponent> LABELS = createLabels();

    private static Dictionary<Integer, JComponent> createLabels() {
        Dictionary<Integer, JComponent> labels = new Hashtable<>();
        zipWithIndex(List.of(
                "1 ppm",
                "3 ppm",
                "10 ppm",
                "30 ppm",
                "100 ppm",
                "300 ppm",
                "0.001",
                "0.003",
                "0.010",
                "0.030",
                "0.100"
        )).forEach(t ->
                labels.put(t._1, new JLabel(t._2)));
        return labels;
    }

    /**
     * Returns the slider
     */
    private static JSlider createSlider() {
        JSlider slider = new JSlider(JSlider.VERTICAL);
        slider.setMaximum(VALUES.length - 1);
        slider.setPaintLabels(true);
        slider.setPaintTicks(true);
        slider.setMajorTickSpacing(1);
        slider.setSnapToTicks(true);
        slider.setLabelTable(LABELS);
        return slider;
    }

    /**
     * Returns the index of value
     *
     * @param value the value
     */
    private static int indexOf(double value) {
        int idx = 0;
        for (int i = 1; i < VALUES.length; i++) {
            double snap = VALUES[i];
            if (abs(snap - value) < abs(snap - VALUES[idx])) {
                idx = i;
            }
        }
        return idx;
    }

    private final PublishProcessor<Map<String, Float>> learningRatesProcessor;
    private Map<String, Float> learningRates;
    private Map<String, JSlider> learningRateSliders;

    /**
     * Creates the panel
     */
    public LearnPanel() {
        this.learningRatesProcessor = PublishProcessor.create();
        setPreferredSize(DEFAULT_PREFERED_SIZE);
    }

    /**
     * Returns the frame with the panel
     */
    public JFrame createFrame() {
        JFrame frame = new JFrame(Messages.getString("LearnPanel.title"));
        Container contentPane = frame.getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(this, BorderLayout.CENTER);
        frame.pack();
        return frame;
    }

    /**
     * Handles the slider value change
     *
     * @param event the event
     */
    private void handleSlider(ChangeEvent event) {
        if (Objects.requireNonNull(event.getSource()) instanceof JSlider slider) {
            if (!slider.getValueIsAdjusting()) {
                learningRates = Tuple2.stream(learningRates)
                        .map(t -> {
                            JSlider slider1 = learningRateSliders.get(t._1);
                            return slider1 != null ? t.setV2((float) VALUES[slider1.getValue()]) : t;
                        })
                        .collect(Tuple2.toMap());
                learningRatesProcessor.onNext(learningRates);
            }
        }
    }

    /**
     * Returns the learning rate flows
     */
    public Flowable<Map<String, Float>> readLearningRates() {
        return learningRatesProcessor;
    }

    /**
     * Sets the learning rates
     *
     * @param rates the rates
     */
    public void setLearningRates(Map<String, Float> rates) {
        this.learningRates = rates;
        this.learningRateSliders = Tuple2.stream(rates)
                .map(t -> {
                    String key = t._1;
                    JSlider slider = createSlider();
                    slider.setValue(indexOf(t._2));
                    slider.setBorder(BorderFactory.createTitledBorder(
                            Messages.getStringOpt("LearnPanel." + key + ".label").orElse(key)
                    ));
                    slider.addChangeListener(this::handleSlider);
                    return t.setV2(slider);
                })
                .collect(Tuple2.toMap());
        GridLayoutHelper<LearnPanel> layoutBuilder = new GridLayoutHelper<>(this)
                .modify("insets,15,5 fill weight,1,1 center")
                .at(0, 0).add(learningRateSliders.get("critic"));
        List<String> actions = this.learningRateSliders.keySet().stream()
                .filter(Predicate.not("critic"::equals))
                .sorted()
                .toList();
        for (int i = 0; i < actions.size(); i++) {
            layoutBuilder.at(i + 1, 0).add(learningRateSliders.get(actions.get(i)));
        }
        doLayout();
    }


}
