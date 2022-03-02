/*
 *
 * Copyright (c) )2022 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.swing;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 *
 */
public class LevelMeter extends JComponent {
    public static final int LENGTH = 100;
    public static final int WIDTH = 10;

    private int minValue;
    private int maxValue;
    private int value;
    private Color[] colorMap;
    private int[] colorLevels;
    private final boolean horizontal;

    /**
     *
     */
    public LevelMeter() {
        this(false);
    }

    /**
     * @param horizontal
     */
    public LevelMeter(boolean horizontal) {
        this.horizontal = horizontal;
        minValue = 0;
        maxValue = 100;
        value = 0;
        colorMap = new Color[]{Color.GREEN};
        colorLevels = new int[0];
        setBorder(BorderFactory.createEtchedBorder());
        Dimension preferredSize = horizontal ? new Dimension(LENGTH, WIDTH) : new Dimension(WIDTH, LENGTH);
        setPreferredSize(preferredSize);
        setSize(preferredSize);
    }

    /**
     * @return
     */
    public int getMinValue() {
        return minValue;
    }

    /**
     * @param minValue
     */
    public void setMinValue(int minValue) {
        this.minValue = minValue;
        repaint();
    }

    /**
     *
     */
    public int getMaxValue() {
        return maxValue;
    }

    /**
     * @param maxValue
     */
    public void setMaxValue(int maxValue) {
        this.maxValue = maxValue;
        repaint();
    }

    /**
     *
     */
    public int getValue() {
        return value;
    }

    /**
     * @param value
     */
    public void setValue(int value) {
        this.value = value;
        repaint();
    }

    /**
     *
     */
    public Color[] getColorMap() {
        return colorMap;
    }

    /**
     * @param colorMap
     */
    public void setColorMap(int[] colorLevels, Color[] colorMap) {
        requireNonNull(colorLevels);
        requireNonNull(colorMap);
        if (colorLevels.length != colorMap.length - 1) {
            throw new IllegalArgumentException();
        }
        this.colorLevels = colorLevels;
        this.colorMap = colorMap;
        repaint();
    }

    /**
     *
     */
    public int[] getColorLevels() {
        return colorLevels;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Dimension size = getSize();
        g.setColor(getBackground());
        g.fillRect(0, 0, size.width, size.height);
        Rectangle rect = getBoundRect();
        g.setColor(Color.BLACK);
        g.fillRect(rect.x, rect.y, rect.width, rect.height);
        List<Rectangle> rects = getFillRects();
        for (int i = 0; i < rects.size(); i++) {
            g.setColor(colorMap[i]);
            Rectangle r = rects.get(i);
            g.fillRect(r.x, r.y, r.width, r.height);
        }
    }

    /**
     *
     */
    private Rectangle getBoundRect() {
        Dimension size = getSize();
        return horizontal
                ? new Rectangle((size.width - LENGTH) / 2, (size.height - WIDTH) / 2, LENGTH, WIDTH)
                : new Rectangle((size.width - WIDTH) / 2, (size.height - LENGTH) / 2, WIDTH, LENGTH);
    }

    /**
     *
     */
    private List<Rectangle> getFillRects() {
        Rectangle bound = getBoundRect();
        int x = 0;
        int prevValue = minValue;
        int v = min(value, maxValue);
        List<Rectangle> rec = new ArrayList<>();
        int range = maxValue - minValue;
        for (int i = 0; i < colorLevels.length; i++) {
            int lev = colorLevels[i];
            if (v < lev) {
                int len = (v - prevValue) * LENGTH / range;
                rec.add(
                        horizontal
                                ? new Rectangle(bound.x + x, bound.y, len, bound.height)
                                : new Rectangle(bound.x, bound.y + LENGTH - x, bound.width, len));
                prevValue = lev;
                x += len;
                break;
            } else {
                int len = (lev - prevValue) * LENGTH / range;
                rec.add(
                        horizontal
                                ? new Rectangle(bound.x + x, bound.y, len, bound.height)
                                : new Rectangle(bound.x, bound.y + LENGTH - x, bound.width, len));
                prevValue = lev;
                x += len;
            }
        }
        if (value >= prevValue) {
            int len = (v - prevValue) * LENGTH / range;
            rec.add(
                    horizontal
                            ? new Rectangle(bound.x + x, bound.y, len, bound.height)
                            : new Rectangle(bound.x, bound.y + LENGTH - x + len, bound.width, len));
        }
        return rec;
    }
}
