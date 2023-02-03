/*
 * Copyright (c) 2023 Marco Marini, marco.marini@mmarini.org
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

import io.reactivex.rxjava3.core.Flowable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * The matrix panel display text in matrix fashion.
 */
public class MatrixColumn extends JComponent {
    public static final String DEFAULT_TIME_PATTERN = "%1$tH.%1$tM:%1$tS.%1$tL";
    private static final long DEFAULT_DECAY_TIME = 3000L;
    private static final float DEFAULT_MIN_BRIGHT = 0.5F;
    private static final Font DEFAULT_FONT = Font.decode(Font.MONOSPACED + " 14");
    private static final int DEFAULT_ROW_NUMBER = 36;
    private static final int TIMESTAMP_COLUMNS = 13;
    private final float minBright;
    private final Color titleColor;
    private final Color titleBackgroundColor;
    private final Font titleFont;
    private String title;
    private String[] rows;
    private int cursor;
    private long[] timestamps;
    private long decayTime;
    private int columns;
    private boolean printTimestamp;
    private String timePattern;

    /**
     * Creates the matrix panel
     */
    public MatrixColumn() {
        setBackground(Color.BLACK);
        setForeground(Color.GREEN);
        this.titleFont = Font.decode("Dialog  bold");
        setFont(DEFAULT_FONT);

        decayTime = DEFAULT_DECAY_TIME;
        minBright = DEFAULT_MIN_BRIGHT;
        timePattern = DEFAULT_TIME_PATTERN;
        printTimestamp = true;
        setRows(DEFAULT_ROW_NUMBER);
        this.titleColor = Color.BLACK;
        this.titleBackgroundColor = Color.WHITE;
        Flowable.interval(1000 / 60, TimeUnit.MILLISECONDS)
                .subscribe(i -> repaint());
        this.title = "";
    }

    public MatrixColumn(String title, int size) {
        this();
        this.title = requireNonNull(title);
        setColumns(size);
    }

    /**
     * Clears the panel
     */
    public void clearAll() {
        Arrays.fill(rows, "");
        Arrays.fill(timestamps, System.currentTimeMillis());
        cursor = 0;
        resize();
        repaint();
    }

    private Color getColor(long interval) {
        Color color = getForeground();
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        double alpha = 1 - (1 - minBright) * min(max((double) interval / decayTime, 0), 1);
        hsb[2] *= alpha;
        return Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
    }

    public int getColumns() {
        return columns;
    }

    public void setColumns(int columns) {
        this.columns = columns;
        resize();
        repaint();
    }

    public long getDecayTime() {
        return decayTime;
    }

    public void setDecayTime(long decayTime) {
        this.decayTime = decayTime;
        repaint();
    }

    /**
     * Returns the row number
     */
    public int getRows() {
        return rows.length;
    }

    /**
     * Sets the number of row
     *
     * @param rows the number of row
     */
    public void setRows(int rows) {
        this.rows = new String[rows];
        timestamps = new long[rows];
        clearAll();
    }

    public String getTimePattern() {
        return timePattern;
    }

    public void setTimePattern(String timePattern) {
        this.timePattern = timePattern;
    }

    public boolean isPrintTimestamp() {
        return printTimestamp;
    }

    public void setPrintTimestamp(boolean printTimestamp) {
        this.printTimestamp = printTimestamp;
        invalidate();
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(getBackground());
        Dimension size = getSize();
        g.fillRect(0, 0, size.width, size.height);
        FontMetrics fm = getFontMetrics(getFont());
        int h = fm.getHeight();
        int y = fm.getMaxAscent();
        long timestamp = System.currentTimeMillis();
        String[] rows1 = rows;
        long[] timestamps1 = timestamps;
        g.setColor(titleBackgroundColor);
        g.fillRect(0, 0, getPreferredSize().width, h);
        g.setColor(titleColor);
        g.drawRect(0, 0, getPreferredSize().width, h);
        g.setFont(titleFont);
        int width = g.getFontMetrics().stringWidth(title);
        int x = (getPreferredSize().width - width) / 2;
        g.drawString(title, x, y);
        g.setFont(getFont());
        y += h;
        g.setColor(getBackground());
        for (int i = 0; i < rows.length; i++) {
            String row = rows1[i];
            if (row.length() > 0) {
                String line = printTimestamp
                        ? format(timePattern + " %2$s", timestamps1[i], row)
                        : row;
                g.setColor(getColor(timestamp - timestamps1[i]));
                g.drawString(line, 0, y);
            }
            y += h;
        }
    }

    public void print(String pattern, Object... args) {
        printLines(format(pattern, args));
    }

    /**
     * Prints a lines into the panel
     *
     * @param lines the lines
     */
    private void printLines(String lines) {
        long timestamp = System.currentTimeMillis();
        for (String s : lines.split("\n")) {
            printRow(s, timestamp);
        }
    }

    /**
     * Prints a line into the panel
     *
     * @param text the line
     */
    private void printRow(String text, long timestamp) {
        rows[cursor] = text;
        timestamps[cursor] = timestamp;
        cursor++;
        if (cursor >= rows.length) {
            cursor = 0;
        }
        rows[cursor] = "";
        resize();
        repaint();
    }

    /**
     * Recomputes the prefered size base on content
     */
    private void resize() {
        Font font = getFont();
        FontMetrics fm = getFontMetrics(font);
        int colW = fm.charWidth('m');
        int minW = colW * (columns + (printTimestamp ? TIMESTAMP_COLUMNS : 0));
        Dimension size = new Dimension(minW, (rows.length + 1) * fm.getHeight() + fm.getMaxDescent());
        setPreferredSize(size);
        invalidate();
    }
}
