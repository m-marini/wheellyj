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

import org.mmarini.swing.Messages;
import org.mmarini.wheelly.apis.DumpRecord;

import javax.swing.table.AbstractTableModel;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;

/**
 * Models the data records as a table
 */
public class DumpRecordTableModel extends AbstractTableModel {
    public static final int TIMESTAMP_COLUMN = 0;
    public static final int ELAPSED_COLUMN = 1;
    public static final int TYPE_COLUMN = 2;
    public static final int DATA_COLUMN = 3;
    private static final String[] COLUMN_NAMES = new String[]{
            Messages.getString("DumpRecordTableModel.timestamp"),
            Messages.getString("DumpRecordTableModel.elapsed"),
            Messages.getString("DumpRecordTableModel.type"),
            Messages.getString("DumpRecordTableModel.data")
    };

    /**
     * Returns the formated duration
     *
     * @param duration the duration
     */
    public static String formatDuration(Duration duration) {
        Duration abs = duration.abs();
        long hours = abs.toHours();
        int minutes = abs.toMinutesPart();
        int seconds = abs.toSecondsPart();
        int millis = abs.toMillisPart();
        String signum = duration.isNegative() ? "-" : "";
        return hours > 0
                ? format("%s%dh %02d' %02d.%03d\"", signum, hours, minutes, seconds, millis)
                : minutes > 0
                ? format("%s%d' %02d.%03d\"", signum, minutes, seconds, millis)
                : format("%s%d.%03d\"", signum, seconds, millis);
    }

    private List<DumpRecord> records;
    private Instant timeOffset;

    /**
     * Creates the model
     */
    public DumpRecordTableModel() {
        records = List.of();
        timeOffset = Instant.now();
    }

    @Override
    public int getColumnCount() {
        return 4;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    /**
     * Returns the record at row
     *
     * @param row the row index
     */
    public Optional<DumpRecord> getRecord(int row) {
        return row >= 0 && row < records.size()
                ? Optional.of(records.get(row))
                : Optional.empty();
    }

    /**
     * Returns the records
     */
    public List<DumpRecord> getRecords() {
        return records;
    }

    /**
     * Sets the data records
     *
     * @param records the records
     */
    public void setRecords(List<DumpRecord> records) {
        this.records = records;
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return records.size();
    }

    /**
     * Returns the localTime offset
     */
    public Instant getTimeOffset() {
        return timeOffset;
    }

    /**
     * Sets the localTime offset
     *
     * @param timeOffset the localTime offset
     */
    public void setTimeOffset(Instant timeOffset) {
        this.timeOffset = timeOffset;
        fireTableDataChanged();
    }

    @Override
    public Object getValueAt(int row, int col) {
        return switch (col) {
            case TIMESTAMP_COLUMN -> records.get(row).instant();
            case ELAPSED_COLUMN -> formatDuration(Duration.between(timeOffset, records.get(row).instant()));
            case TYPE_COLUMN -> records.get(row).comDirection();
            case DATA_COLUMN -> records.get(row).data();
            default -> "?";
        };
    }
}
