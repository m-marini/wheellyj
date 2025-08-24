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
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;

/**
 * Matrix table shows events in matrix digital rain
 */
public class MatrixTable extends JPanel {

    /**
     * Returns the matrix table with columns
     *
     * @param args the arguments list of (key, title, size)
     */
    public static MatrixTable create(Object... args) {
        if (!(args.length % 3 == 0)) {
            throw new IllegalArgumentException(format("Arguments must be multiple of 3 (%d)", args.length));
        }
        MatrixTable matrixTable = new MatrixTable();
        for (int i = 0; i < args.length; i += 3) {
            matrixTable.addColumn(String.valueOf(args[i]), String.valueOf(args[i + 1]), ((Number) args[i + 2]).intValue());
        }
        return matrixTable;
    }

    private final List<MatrixColumn> columns;
    private final Map<String, MatrixColumn> columnByKey;

    /**
     * Creates the matrix table
     */
    public MatrixTable() {
        columns = new ArrayList<>();
        columnByKey = new HashMap<>();
        setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
        setBackground(Color.BLACK);
        Flowable.interval(1000 / 30, TimeUnit.MILLISECONDS)
                .doOnNext(ignored -> repaint())
                .subscribe();
    }

    /**
     * Returns the matrix column by adding a named columns
     *
     * @param key   the column key
     * @param title the column title
     * @param size  the size of column (chars)
     */
    public MatrixColumn addColumn(String key, String title, int size) {
        MatrixColumn column = new MatrixColumn(title, size);
        return addColumn(key, column);
    }

    /**
     * Adds a named columns and returns the changed matrix table
     *
     * @param key    the column key
     * @param column the column
     */
    public MatrixColumn addColumn(String key, MatrixColumn column) {
        columns.add(column);
        columnByKey.put(key, column);
        add(column);
        invalidate();
        return column;
    }

    /**
     * Returns the frame for this table
     *
     * @param title the title
     */
    public JFrame createFrame(String title) {
        JScrollPane scroll = new JScrollPane(this);
        JFrame frame = new JFrame(title);
        Container contentPane = frame.getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(scroll, BorderLayout.CENTER);
        frame.pack();
        return frame;
    }

    public MatrixColumn getColumn(String key) {
        return columnByKey.get(key);
    }

    public MatrixColumn getColumn(int i) {
        return columns.get(i);
    }

    public int getColumnNumber() {
        return columns.size();
    }

    public Set<String> getColumnKeys() {
        return columnByKey.keySet();
    }

    public void printf(String key, String pattern, Object... args) {
        getColumn(key).print(pattern, args);
    }

    public void setPrintTimestamp(boolean printTimestamp) {
        for (int i = 0; i < getColumnNumber(); i++) {
            getColumn(i).setPrintTimestamp(printTimestamp);
        }
    }
}
