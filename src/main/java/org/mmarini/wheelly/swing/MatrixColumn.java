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

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Locale;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * The matrix column display text in matrix digital rain mode.
 */
public class MatrixColumn extends JComponent {
    public static final String DEFAULT_TIME_PATTERN = "%1$tH:%1$tM:%1$tS.%1$tL";
    public static final int TIMESTAMP_COLUMNS = 13;
    private static final long DEFAULT_DECAY_TIME = 3000L;
    private static final float DEFAULT_MIN_BRIGHT = 50F / 256;
    private static final Font DEFAULT_FONT = Font.decode(Font.MONOSPACED + " 14");
    private static final int DEFAULT_ROW_NUMBER = 36;
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
    private boolean scrollOnChange;
    private boolean highlightLast;

    /**
     * Creates the matrix panel
     */
    public MatrixColumn() {
        setBackground(Color.BLACK);
        setForeground(Color.GREEN);
        setAlignmentX(0F);
        setAlignmentY(0F);
        this.titleFont = Font.decode("Dialog  bold");
        setFont(DEFAULT_FONT);

        decayTime = DEFAULT_DECAY_TIME;
        minBright = DEFAULT_MIN_BRIGHT;
        timePattern = DEFAULT_TIME_PATTERN;
        printTimestamp = true;
        setRows(DEFAULT_ROW_NUMBER);
        this.titleColor = Color.BLACK;
        this.titleBackgroundColor = Color.WHITE;
        this.title = "";
        this.highlightLast = true;
    }

    /**
     * Creates a matrix column
     *
     * @param title the column title
     * @param size  the size (chars)
     */
    public MatrixColumn(String title, int size) {
        this();
        this.title = requireNonNull(title);
        setColumns(size);
    }

    /**
     * Clears the column
     */
    public void clearAll() {
        Arrays.fill(rows, "");
        Arrays.fill(timestamps, System.currentTimeMillis());
        cursor = 0;
        resize();
        repaint();
    }

    /**
     * Returns the color by interval
     *
     * @param interval the interval
     */
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

    public MatrixColumn setColumns(int columns) {
        this.columns = columns;
        resize();
        repaint();
        return this;
    }

    public long getDecayTime() {
        return decayTime;
    }

    public MatrixColumn setDecayTime(long decayTime) {
        this.decayTime = decayTime;
        repaint();
        return this;
    }

    private int getPrevIndex() {
        return (cursor + rows.length - 1) % rows.length;
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
     * @return
     */
    public MatrixColumn setRows(int rows) {
        this.rows = new String[rows];
        timestamps = new long[rows];
        clearAll();
        return this;
    }

    /**
     * Returns the time pattern to print timestamp
     */
    public String getTimePattern() {
        return timePattern;
    }

    /**
     * Sets the time pattern to print timestamp
     *
     * @param timePattern the pattern
     * @return
     */
    public MatrixColumn setTimePattern(String timePattern) {
        this.timePattern = timePattern;
        resize();
        repaint();
        return this;
    }

    /**
     * Returns true if highlights the last record
     */
    public boolean isHighlightLast() {
        return highlightLast;
    }

    /**
     * Sets the highlight last record
     *
     * @param highlightLast true if highlight last record
     * @return
     */
    public MatrixColumn setHighlightLast(boolean highlightLast) {
        this.highlightLast = highlightLast;
        repaint();
        return this;
    }

    /**
     * Returns true if timestamp is printed
     */
    public boolean isPrintTimestamp() {
        return printTimestamp;
    }

    /**
     * Sets the print timestamp
     *
     * @param printTimestamp true if time stamp is printed
     */
    public MatrixColumn setPrintTimestamp(boolean printTimestamp) {
        this.printTimestamp = printTimestamp;
        resize();
        repaint();
        return this;
    }

    /**
     * Returns true if scroll on change is active
     */
    public boolean isScrollOnChange() {
        return scrollOnChange;
    }

    /**
     * Sets the scroll on change
     * If scroll on change is active the new value is painted in the new row only if it changed the value
     * otherwise only the new refresh color is activated
     *
     * @param scrollOnChange true if scroll on change
     */
    public MatrixColumn setScrollOnChange(boolean scrollOnChange) {
        this.scrollOnChange = scrollOnChange;
        return this;
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
        x = fm.charWidth('m') / 2;
        int prevIndex = getPrevIndex();
        Color lastColor = getColor(0);
        for (int i = 0; i < rows.length; i++) {
            String row = rows1[i];
            if (row.length() > 0) {
                String line = printTimestamp
                        ? format(timePattern + " %2$s", timestamps1[i], row)
                        : row;
                g.setColor(highlightLast && i == prevIndex
                        ? lastColor
                        : getColor(timestamp - timestamps1[i]));
                g.drawString(line, x, y);
            }
            y += h;
        }
    }

    public void print(String pattern, Object... args) {
        printLines(format(Locale.ENGLISH, pattern, args));
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
        if (scrollOnChange) {
            int prev = getPrevIndex();
            if (text.equals(rows[prev])) {
                timestamps[prev] = timestamp;
                repaint();
                return;
            }
        }
        rows[cursor] = text;
        timestamps[cursor] = timestamp;
        cursor++;
        if (cursor >= rows.length) {
            cursor = 0;
        }
        rows[cursor] = "";
        repaint();
    }

    /**
     * Recomputes the preferred size base on content
     */
    private void resize() {
        Font font = getFont();
        FontMetrics fm = getFontMetrics(font);
        int colW = fm.charWidth('m');
        int minW = colW * (columns + (printTimestamp ? TIMESTAMP_COLUMNS : 0) + 2);
        Dimension size = new Dimension(minW, (rows.length + 1) * fm.getHeight() + fm.getMaxDescent());
        setPreferredSize(size);
        setMinimumSize(size);
        setMaximumSize(size);
        invalidate();
    }
}
