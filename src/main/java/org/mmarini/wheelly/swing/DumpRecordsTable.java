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

import org.mmarini.wheelly.apis.DumpRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Shows the dump records in table format
 */
public class DumpRecordsTable extends JTable {
    private static final int[] PREFERRED_COLUMN_WIDTHS = new int[]{
            190, 80, 40, 500
    };
    private static final Logger logger = LoggerFactory.getLogger(DumpRecordsTable.class);

    /**
     * Creates the panel
     */
    public DumpRecordsTable() {
        setModel(new DumpRecordTableModel());

        TableColumnModel model = getTableHeader().getColumnModel();
        for (int i = 0; i < PREFERRED_COLUMN_WIDTHS.length; i++) {
            model.getColumn(i).setPreferredWidth(PREFERRED_COLUMN_WIDTHS[i]);
            model.getColumn(i).setWidth(PREFERRED_COLUMN_WIDTHS[i]);
        }
        model = getColumnModel();
        for (int i = 0; i < PREFERRED_COLUMN_WIDTHS.length; i++) {
            model.getColumn(i).setPreferredWidth(PREFERRED_COLUMN_WIDTHS[i]);
            model.getColumn(i).setWidth(PREFERRED_COLUMN_WIDTHS[i]);
        }
        setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        int width = Arrays.stream(PREFERRED_COLUMN_WIDTHS).sum();
        Dimension pf = getPreferredSize();
    }

    /**
     * Returns the selected record
     */
    public Optional<DumpRecord> getSelectedRecord() {
        int row = getSelectedRow();
        return ((DumpRecordTableModel) getModel()).getRecord(row);
    }

    /**
     * Returns the time offset
     */
    public Instant getTimestampOffset() {
        return ((DumpRecordTableModel) getModel()).getTimeOffset();
    }

    /**
     * Sets the time offset
     *
     * @param offset the offset
     */
    public void setTimestampOffset(Instant offset) {
        int row = getSelectedRow();
        ((DumpRecordTableModel) getModel()).setTimeOffset(offset);
        getSelectionModel().setSelectionInterval(row, row);
    }

    /**
     * Sets the data records
     *
     * @param records the data records
     */
    public void setRecords(List<DumpRecord> records) {
        ((DumpRecordTableModel) getModel()).setRecords(records);
    }
}
