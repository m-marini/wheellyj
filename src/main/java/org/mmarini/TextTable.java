/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.max;

/**
 * Represents a text-based table with configurable column headers and cell values.
 *
 * <p>The table is built using a fluent API, allowing headers and cell values to
 * be defined by row and column indexes. Values can be provided directly as
 * strings or formatted using {@link String#format(String, Object...)}.</p>
 *
 * <p>The {@link #build()} method generates the table as a list of strings.
 * Columns are automatically sized according to the longest header or cell
 * value contained in each column.</p>
 *
 * <p>For example:</p>
 * <pre>{@code
 * TextTable table = new TextTable()
 *         .header(0, "Name")
 *         .header(1, "Age")
 *         .set(0, 0, "Alice")
 *         .set(0, 1, "30")
 *         .set(1, 0, "Bob")
 *         .set(1, 1, "25");
 *
 * List<String> lines = table.build();
 * }</pre>
 *
 * <p>The resulting table has the following structure:</p>
 * <pre>
 * | Name  | Age |
 * |-------|-----|
 * | Alice |  30 |
 * |   Bob |  25 |
 * </pre>
 */
public class TextTable {

    private final List<String> headers;
    private final Map<Point, String> data;

    /**
     * Creates an empty text table without headers or cell values.
     */
    public TextTable() {
        headers = new ArrayList<>();
        data = new HashMap<>();
    }

    /**
     * Builds the table representation.
     *
     * <p>The returned list contains one string for each line of the rendered
     * table. The first line contains the column headers, the second line is a
     * separator, and the remaining lines contain the cell values.</p>
     *
     * <p>The number of columns is determined by the greatest of the number of
     * defined headers and the highest column index containing a value. The
     * number of rows is determined by the highest row index containing a
     * value.</p>
     *
     * <p>Column widths are automatically calculated using the longest header
     * or cell value in each column. Empty cells are rendered as blank spaces.</p>
     *
     * @return a list of strings representing the formatted table
     */
    public List<String> build() {
        int numCols = max(data.keySet().stream()
                        .mapToInt(p -> p.y)
                        .max()
                        .orElse(-1) + 1,
                headers.size());
        int numRows = data.keySet().stream()
                .mapToInt(p -> p.x)
                .max()
                .orElse(-1) + 1;
        int[] sizes = new int[numCols];
        for (int i = 0; i < headers.size(); i++) {
            sizes[i] = max(headers.get(i).length(), 1);
        }

        data.forEach((indices, text) ->
                sizes[indices.y] = max(sizes[indices.y], text.length())
        );

        List<String> result = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int j = 0; j < numCols; j++) {
            String fmt = "| %" + sizes[j] + "s ";
            line.append(String.format(fmt,
                    j < headers.size()
                            ? headers.get(j)
                            : ""));
        }
        line.append("|");
        result.add(line.toString());

        line.setLength(0);
        for (int j = 0; j < numCols; j++) {
            line.append("|");
            line.repeat("-", Math.max(0, sizes[j] + 2));
        }
        line.append("|");
        result.add(line.toString());

        Point key = new Point();
        for (int i = 0; i < numRows; i++) {
            line.setLength(0);
            key.x = i;
            for (int j = 0; j < numCols; j++) {
                key.y = j;
                String value = data.get(key);
                String fmt = "| %" + sizes[j] + "s ";
                line.append(String.format(fmt, value != null
                        ? value
                        : ""));
            }
            line.append("|");
            result.add(line.toString());
        }

        return result;
    }

    /**
     * Sets a formatted value for a cell.
     *
     * <p>The value is generated using
     * {@link String#format(String, Object...)} and then assigned to the
     * specified cell.</p>
     *
     * @param row  the zero-based row index
     * @param col  the zero-based column index
     * @param fmt  the format string
     * @param args the arguments referenced by the format specifiers in
     *             {@code fmt}
     * @return this table instance, allowing method calls to be chained
     * @see #set(int, int, String)
     * @see String#format(String, Object...)
     */
    public TextTable format(int row, int col, String fmt, Object... args) {
        return set(row, col, String.format(fmt, args));
    }

    /**
     * Sets a formatted title for a column.
     *
     * <p>The title is generated using {@link String#format(String, Object...)}
     * and then assigned to the specified column.</p>
     *
     * @param col  the zero-based column index
     * @param fmt  the format string
     * @param args the arguments referenced by the format specifiers in
     *             {@code fmt}
     * @return this table instance, allowing method calls to be chained
     * @see #header(int, String)
     * @see String#format(String, Object...)
     */
    public TextTable formatHeader(int col, String fmt, Object... args) {
        return header(col, String.format(fmt, args));
    }

    /**
     * Sets the title of a column.
     *
     * <p>If the specified column index is greater than or equal to the current
     * number of headers, empty headers are added as necessary until the
     * specified column is reached. If the column already exists, its title is
     * replaced.</p>
     *
     * @param col   the zero-based column index
     * @param title the title to assign to the column
     * @return this table instance, allowing method calls to be chained
     */
    public TextTable header(int col, String title) {
        if (col >= headers.size()) {
            for (int i = headers.size(); i < col; i++) {
                headers.add("");
            }
            headers.add(title);
        } else {
            headers.set(col, title);
        }
        return this;
    }

    /**
     * Sets the titles of multiple consecutive columns.
     *
     * <p>The first title is assigned to the column specified by {@code col},
     * the second title to the following column, and so on.</p>
     *
     * <p>This method is equivalent to invoking {@link #header(int, String)}
     * for each title, starting at the specified column.</p>
     *
     * @param col    the zero-based index of the first column
     * @param titles the titles to assign to consecutive columns
     * @return this table instance, allowing method calls to be chained
     * @see #header(int, String)
     */
    public TextTable headers(int col, String... titles) {
        for (int i = 0; i < titles.length; i++) {
            header(col + i, titles[i]);
        }
        return this;
    }

    /**
     * Sets the value of a cell.
     *
     * <p>The cell is identified by its zero-based row and column indexes.
     * If a value was previously assigned to the same cell, it is replaced.</p>
     *
     * @param row   the zero-based row index
     * @param col   the zero-based column index
     * @param value the value to assign to the cell
     * @return this table instance, allowing method calls to be chained
     */
    public TextTable set(int row, int col, String value) {
        data.put(new Point(row, col), value);
        return this;
    }
}
