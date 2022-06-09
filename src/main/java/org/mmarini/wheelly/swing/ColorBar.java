/*
 *
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
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
import java.util.stream.IntStream;

import static java.awt.Color.*;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class ColorBar extends JComponent {
    public static final int LONG_SIZE = 250;
    public static final int SHORT_SIZE = 30;
    private final boolean horizontal;
    private final Color[] colors;
    private int[] values;

    public ColorBar() {
        this(true);
    }

    public ColorBar(boolean horizontal) {
        this.horizontal = horizontal;
        setPreferredSize(horizontal
                ? new Dimension(LONG_SIZE, SHORT_SIZE)
                : new Dimension(SHORT_SIZE, LONG_SIZE)
        );
        colors = new Color[]{GREEN, YELLOW, RED};
        setBackground(GRAY);
        setBorder(BorderFactory.createEtchedBorder());
        setValues(new int[3]);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Dimension size = getSize();
        Rectangle rect = new Rectangle(0, 0, size.width, size.height);
        Insets inset = getInsets();
        if (inset != null) {
            rect.x += inset.left;
            rect.y += inset.top;
            rect.width -= inset.left + inset.right;
            rect.height -= inset.top + inset.bottom;
        }
        g.fillRect(rect.x, rect.y, rect.width, rect.height);
        int tot = IntStream.of(values).sum();
        int barSize = horizontal ? rect.width : rect.height;
        int[] sizes = IntStream.of(values).map(x -> tot > 0 ? x * barSize / tot : 0).toArray();
        int[] offsets = new int[values.length];
        for (int i = 0; i < values.length - 1; i++) {
            offsets[i + 1] = offsets[i] + sizes[i];
        }
        for (int i = 0; i < values.length; i++) {
            g.setColor(colors[i]);
            if (horizontal) {
                g.fillRect(rect.x + offsets[i], rect.y, sizes[i], rect.height);
            } else {
                g.fillRect(rect.x, rect.y + offsets[i], rect.width, sizes[i]);
            }
        }
    }

    public void setValues(int... values) {
        requireNonNull(values);
        if (values.length != colors.length) {
            throw new IllegalArgumentException(format("values should be %d size", colors.length));
        }
        this.values = values;
        repaint();
    }
}
