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

import org.mmarini.swing.GridLayoutHelper;

import javax.swing.*;

import static java.awt.Color.BLACK;
import static java.awt.Color.WHITE;
import static java.lang.String.format;

/**
 *
 */
public class RLMonitor extends JPanel {

    private final JLabel averageReward;
    private final JLabel c0;
    private final JLabel c1;
    private final JLabel c2;
    private final JLabel haltAlpha;
    private final ColorBar colorBar;
    private final JLabel directionAlpha;
    private final JLabel speedAlpha;
    private final JLabel sensorAlpha;

    /**
     *
     */
    public RLMonitor() {
        this.averageReward = new JLabel();
        this.c0 = new JLabel();
        this.c1 = new JLabel();
        this.c2 = new JLabel();
        this.haltAlpha = new JLabel();
        this.directionAlpha = new JLabel();
        this.speedAlpha = new JLabel();
        this.sensorAlpha = new JLabel();
        this.colorBar = new ColorBar();

        setBackground(BLACK);

        JLabel avgLabel = new JLabel("Average reward");
        JLabel barLabel = new JLabel("C");
        JLabel c0Label = new JLabel("C0");
        JLabel c1Label = new JLabel("C1");
        JLabel c2Label = new JLabel("C2");
        JLabel haltLabel = new JLabel("Halt alpha");
        JLabel dirLabel = new JLabel("Direction alpha");
        JLabel speedLabel = new JLabel("Speed alpha");
        JLabel sensorLabel = new JLabel("Sensor alpha");

        new GridLayoutHelper<>(this)
                .insets(2)
                .weight(0, 1).nofill()
                .at(0, 0).e().add(avgLabel)
                .at(1, 0).add(averageReward)
                .at(0, 1).center().hspan().add(barLabel)
                .at(0, 2).add(colorBar).nospan()
                .at(0, 3).e().add(c0Label)
                .at(1, 3).add(c0)
                .at(0, 4).add(c1Label)
                .at(1, 4).add(c1)
                .at(0, 5).add(c2Label)
                .at(1, 5).add(c2)
                .at(0, 6).add(haltLabel)
                .at(1, 6).add(haltAlpha)
                .at(0, 7).add(dirLabel)
                .at(1, 7).add(directionAlpha)
                .at(0, 8).add(speedLabel)
                .at(1, 8).add(speedAlpha)
                .at(0, 9).add(sensorLabel)
                .at(1, 9).add(sensorAlpha);

        for (JComponent c : new JComponent[]{averageReward, c0, c1, c2, haltAlpha, directionAlpha, speedAlpha, sensorAlpha,
                avgLabel, barLabel, c0Label, c1Label, c2Label, haltLabel, dirLabel, speedLabel, sensorLabel}) {
            c.setBackground(BLACK);
            c.setForeground(WHITE);
        }

        setAverageReward(0);
        setC(0, 0, 0);
        setHaltAlpha(0);
        setDirectionAlpha(0);
        setSpeedAlpha(0);
        setSensorAlpha(0);
    }

    public void setAverageReward(double value) {
        averageReward.setText(format("%-8.2g", value));
    }

    public void setC(int c0, int c1, int c2) {
        double p0 = 0;
        double p1 = 0;
        double p2 = 0;
        int tot = c0 + c1 + c2;
        if (tot > 0) {
            p0 = 100d * c0 / tot;
            p1 = 100d * c1 / tot;
            p2 = 100d * c2 / tot;
        }
        this.c0.setText(format("%3.0f %% (%d)", p0, c0));
        this.c1.setText(format("%3.0f %% (%d)", p1, c1));
        this.c2.setText(format("%3.0f %% (%d)", p2, c2));
        colorBar.setValues(c2, c1, c0);
    }

    public void setDirectionAlpha(double value) {
        directionAlpha.setText(format("%-8.2g", value));
    }

    public void setHaltAlpha(double value) {
        haltAlpha.setText(format("%-8.2g", value));
    }

    public void setSensorAlpha(double value) {
        sensorAlpha.setText(format("%-8.2g", value));
    }

    public void setSpeedAlpha(double value) {
        speedAlpha.setText(format("%-8.2g", value));
    }
}
