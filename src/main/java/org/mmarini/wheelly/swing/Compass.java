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

import static java.awt.Color.*;
import static java.lang.Math.*;

/**
 *
 */
public class Compass extends JComponent {
    private static final Color COMPASS_BACKGROUND = BLACK;
    private static final int MINOR_DEG_STEP = 5;
    private static final int MAJOR_DEG_STEP = 10;
    private static final int MAJOR_STEP_LENGHT = 5;
    private static final int MINOR_STEP_LENGTH = 2;
    private static final Color PROTRACTOR_COLOR = WHITE;
    private static final Color ROSE_COLOR = GRAY;
    private static final Color NEEDLE_COLOR = RED;
    private double angle;

    public Compass() {
        setBackground(BLACK);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Dimension size = getSize();
        g.setColor(getBackground());
        g.fillRect(0, 0, size.width, size.height);
        int xc = size.width / 2;
        int yc = size.height / 2;
        int radius = min(size.height, size.width) / 2;
        int x0 = xc - radius;
        int y0 = yc - radius;
        if (!getBackground().equals(COMPASS_BACKGROUND)) {
            g.setColor(COMPASS_BACKGROUND);
            g.fillOval(x0, y0, radius * 2, radius * 2);
        }
        // Paint protractor
        int innerRadius = radius - MAJOR_STEP_LENGHT;
        int middleRadius = radius - MAJOR_STEP_LENGHT + MINOR_STEP_LENGTH;
        g.setColor(ROSE_COLOR);
        g.drawLine(xc, y0, xc, y0 + radius * 2);
        g.drawLine(x0, yc, x0 + radius * 2, yc);
        g.setColor(PROTRACTOR_COLOR);
        g.drawOval(xc - innerRadius, yc - innerRadius, innerRadius * 2, innerRadius * 2);
        for (int a = 0; a < 360; a += MINOR_DEG_STEP) {
            double sa = sin(toRadians(a));
            double ca = cos(toRadians(a));
            int endRadius = (a % MAJOR_DEG_STEP) == 0
                    ? radius : middleRadius;
            int x1 = (int) round(xc + innerRadius * sa);
            int y1 = (int) round(yc - innerRadius * ca);
            int x2 = (int) round(xc + endRadius * sa);
            int y2 = (int) round(yc - endRadius * ca);
            g.drawLine(x1, y1, x2, y2);
        }
        // Draw needle
        double sa = sin(angle);
        double ca = cos(angle);
        int x3 = (int) round(xc + innerRadius * sa);
        int y3 = (int) round(yc - innerRadius * ca);
        g.setColor(NEEDLE_COLOR);

        g.drawLine(xc, yc, x3, y3);
    }

    /**
     * @param angle the angle in RAD
     */
    public void setAngle(double angle) {
        this.angle = angle;
        repaint();
    }
}
