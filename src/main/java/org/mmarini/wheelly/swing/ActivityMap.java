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

import org.nd4j.linalg.api.ndarray.INDArray;

import javax.swing.*;
import java.awt.*;

import static java.lang.Math.*;
import static org.mmarini.wheelly.apis.Utils.linear;

/**
 * Display the activity of a layer
 */
public class ActivityMap extends JComponent {

    public static final int PREFERRED_SIZE = 30;
    public static final int MIN_DOT_SIZE = 3;
    public static final float MIN_RANGE = 1e-6F;
    public static final float DECAY_1 = 100e-3F;

    /**
     * Returns the color for a give temperature
     *
     * @param temp the temperature
     */
    private static Color colorTemperature(float temp) {
        float hue = temp * 5F / 6;
        float bright = (1 - temp) * 0.6F + 0.4F;
        return Color.getHSBColor(hue, 1, bright);
        //return Color.getHSBColor(0, 0, 1-temp);
    }

    private int activityWidth;
    private int activityHeight;
    private Color[] activity;
    private INDArray avg;

    /**
     * Creates the activity map
     */
    public ActivityMap() {
        this.activity = new Color[0];
        setBackground(Color.BLACK);
    }

    @Override
    protected void paintComponent(Graphics gr) {
        Insets insets = getInsets();
        if (insets == null) {
            insets = new Insets(0, 0, 0, 0);
        }
        int width = getWidth() - insets.left - insets.right;
        int height = getHeight() - insets.top - insets.bottom;
        Graphics g = gr.create(insets.left, insets.top, width, height);
        g.setColor(getBackground());
        g.clipRect(0, 0, width, height);
        g.fillRect(0, 0, width, height);
        Color[] activity = this.activity;
        for (int i = 0; i < activity.length; i++) {
            int xi = i % activityWidth;
            int yi = i / activityWidth;
            int x = xi * width / activityWidth;
            int w = (xi + 1) * width / activityWidth - x;
            int y = yi * height / activityHeight;
            int h = (yi + 1) * height / activityHeight - y;
            g.setColor(activity[i]);
            g.fillRect(x, y, w, h);
        }
    }

    /**
     * Sets the activity values
     *
     * @param activity the activity
     */
    void setActivity(INDArray activity) {
        if (avg == null) {
            avg = activity.dup();
        } else {
            // avg = avg * decay + act * (1 - decay)
            // avg = avg * decay + act - act * decay
            // avg = (avg - act) * decay + act

            // avg = avg * decay + avg * (1-decay) - avg * (1-decay) + act * (1 - decay)
            // avg = avg * decay + avg * (1-decay) + (act-avg) * (1-decay)
            // avg =  avg + (act-avg) * (1-decay)
            // avg =  avg + (act-avg) * (1-decay)
            try (INDArray delta = activity.sub(avg).muli(DECAY_1)) {
                avg.addi(delta);
            }
        }
        int size = activityHeight * activityWidth;
        Color[] activityColors = new Color[(int) min(size, avg.size(1))];
        float min = avg.minNumber().floatValue();
        float max = avg.maxNumber().floatValue();
        float range = max - min;
        if (range < MIN_RANGE) {
            float h = max(abs(max) + MIN_RANGE, abs(min) + MIN_RANGE);
            min = -h;
            max = h;
        }
        for (int i = 0; i < avg.size(1) && i < size; i++) {
            float temp = linear(avg.getFloat(0, i), min, max, 0, 1);
            activityColors[i] = colorTemperature(1 - temp);
        }
        this.activity = activityColors;
        repaint();
    }

    /**
     * Set the layer size
     *
     * @param width  the width
     * @param height the height
     */
    void setLayerSize(int width, int height) {
        Insets insets = getInsets();
        if (insets == null) {
            insets = new Insets(0, 0, 0, 0);
        }
        this.activityWidth = width;
        this.activityHeight = height;
        int pWidth = max(activityWidth * MIN_DOT_SIZE, PREFERRED_SIZE);
        int pHeight = max(activityHeight * MIN_DOT_SIZE, PREFERRED_SIZE);
        //dotSize = max(PREFERRED_SIZE / max(activityWidth, activityHeight), MIN_DOT_SIZE);
        Dimension size = new Dimension(
                pWidth + insets.left + insets.right,
                pHeight + insets.top + insets.bottom);
        setPreferredSize(size);
        doLayout();
        repaint();
    }
}
