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

import hu.akarnokd.rxjava3.swing.SwingObservable;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.swing.SwingUtils;
import org.mmarini.wheelly.apis.DumpRecord;
import org.mmarini.wheelly.apis.WheellyStatus;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.swing.DumpRecordTableModel.formatDuration;

/**
 * Show the detail of dump record
 */
public class DumpRecordPanel extends JPanel {
    private final JFormattedTextField instantField;
    private final JTextField elapsedField;
    private final JTextArea dataField;
    private final JTextField typeField;
    private final JButton offsetButton;
    private final JFormattedTextField sensorField;
    private final JFormattedTextField echoField;
    private final JCheckBox haltField;
    private final Flowable<Instant> offsetFlow;
    private final JFormattedTextField directionField;
    private final JFormattedTextField leftPpsField;
    private final JFormattedTextField rightPpsField;
    private final JPanel statusPanel;
    private Instant offset;
    private DumpRecord record;

    /**
     * Creates the panel
     */
    public DumpRecordPanel() {
        this.instantField = new JFormattedTextField();
        this.directionField = new JFormattedTextField();
        this.leftPpsField = new JFormattedTextField();
        this.rightPpsField = new JFormattedTextField();
        this.sensorField = new JFormattedTextField();
        this.echoField = new JFormattedTextField();
        this.haltField = SwingUtils.createCheckBox("DumpRecordPanel.haltButton");
        this.typeField = new JTextField();
        this.elapsedField = new JTextField();
        this.dataField = new JTextArea();
        this.statusPanel = new JPanel();
        this.offsetButton = SwingUtils.createToolBarButton("DumpRecordPanel.offsetButton");
        this.offset = Instant.now();
        this.offsetFlow = SwingObservable.actions(offsetButton)
                .toFlowable(BackpressureStrategy.LATEST)
                .filter(x -> record != null)
                .map(x -> record.getInstant());
        init();
        createContent();
    }

    /**
     * Clears the status fields
     */
    private void clearStatusField() {
        Stream.of(directionField, leftPpsField, rightPpsField, echoField, sensorField)
                .forEach(c -> c.setText(""));
        haltField.setSelected(false);
        statusPanel.setVisible(false);
    }

    /**
     * Creates the panel content
     */
    private void createContent() {
        JPanel instantBox = new JPanel();
        instantBox.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        instantBox.add(instantField);
        instantBox.add(offsetButton);
        JScrollPane scrollDataPanel = new JScrollPane(dataField);
        scrollDataPanel.setMinimumSize(new Dimension(
                scrollDataPanel.getMinimumSize().width,
                scrollDataPanel.getPreferredSize().height
        ));
        // Creates the status panel content
        new GridLayoutHelper<>(Messages.RESOURCE_BUNDLE, statusPanel)
                .modify("insets,4,4")
                .modify("at,0,0 w weight,1,0")
                .add("DumpRecordPanel.direction.label")
                .modify("at,0,1 nospan")
                .add("DumpRecordPanel.leftPps.label")
                .modify("at,1,1")
                .add("DumpRecordPanel.rightPps.label")
                .modify("at,0,3 nospan")
                .add("DumpRecordPanel.sensor.label")
                .modify("at,1,3")
                .add("DumpRecordPanel.echo.label")
                .modify("at,1,0 nospan")
                .add(directionField)
                .modify("at,0,2")
                .add(leftPpsField)
                .modify("at,1,2")
                .add(rightPpsField)
                .modify("at,0,4")
                .add(sensorField)
                .modify("at,1,4")
                .add(echoField)
                .modify("at,0,6 span,2,1 noweight")
                .add(haltField);
        // Creates the content
        new GridLayoutHelper<>(Messages.RESOURCE_BUNDLE, this)
                .modify("insets,4,4")
                .modify("at,0,0 w")
                .add("DumpRecordPanel.instant.label")
                .modify("at,0,1")
                .add("DumpRecordPanel.elapsed.label")
                .modify("at,0,2")
                .add("DumpRecordPanel.type.label")
                .modify("at,0,3 span,2,1")
                .add("DumpRecordPanel.data.label")
                .modify("at,1,0 weight,1,0 nospan")
                .add(instantBox)
                .modify("at,1,1")
                .add(elapsedField)
                .modify("at,1,2")
                .add(typeField)
                .modify("at,0,4 fill span,2,1")
                .add(scrollDataPanel)
                .modify("at,0,5 nofill insets,0")
                .add(statusPanel);
    }

    /**
     * Returns the time offset
     */
    public Instant getOffset() {
        return offset;
    }

    /**
     * Sets the time offset
     *
     * @param offset the time offset
     */
    public void setOffset(Instant offset) {
        this.offset = requireNonNull(offset);
        if (record != null) {
            elapsedField.setText(
                    formatDuration(
                            Duration.between(offset, record.getInstant())));
        }
    }

    /**
     * Returns the shown record
     */
    public DumpRecord getRecord() {
        return record;
    }

    /**
     * Sets the shown record
     *
     * @param record the record
     */
    public void setRecord(DumpRecord record) {
        this.record = record;
        if (record != null) {
            instantField.setValue(record.getInstant());
            typeField.setText(record.getComDirection());
            dataField.setText(record.getData());
            elapsedField.setText(
                    formatDuration(
                            Duration.between(offset, record.getInstant())));
            if (record instanceof DumpRecord.StatusDumpRecord) {
                setStatusFields((DumpRecord.StatusDumpRecord) record);
            } else {
                clearStatusField();
            }
        } else {
            Stream.of(instantField, typeField, dataField, elapsedField)
                    .forEach(c -> c.setText(""));
            clearStatusField();
        }
    }

    /**
     * Initializes the panel
     */
    private void init() {
        instantField.setColumns(20);
        typeField.setColumns(5);
        elapsedField.setColumns(15);
        dataField.setColumns(80);
        dataField.setRows(2);
        dataField.setLineWrap(true);
        directionField.setColumns(5);
        sensorField.setColumns(4);
        leftPpsField.setColumns(6);
        rightPpsField.setColumns(6);
        echoField.setColumns(5);
        offsetButton.setMargin(new Insets(0, 0, 0, 0));
        Stream.of(instantField, dataField, typeField, elapsedField,
                        directionField, leftPpsField, rightPpsField, echoField, sensorField)
                .forEach(c -> {
                    c.setMinimumSize(c.getPreferredSize());
                    c.setEditable(false);
                });
        haltField.setEnabled(false);
    }

    /**
     * Returns the offset flow
     */
    public Flowable<Instant> readOffset() {
        return offsetFlow;
    }

    /**
     * Sets the status fields
     *
     * @param record the record
     */
    private void setStatusFields(DumpRecord.StatusDumpRecord record) {
        WheellyStatus status = record.getStatus();
        directionField.setValue(status.getDirection());
        leftPpsField.setValue(status.getLeftPps());
        rightPpsField.setValue(status.getRightPps());
        sensorField.setValue(status.getSensorDirection());
        echoField.setValue(status.getEchoTime());
        haltField.setSelected(status.isHalt());
        statusPanel.setVisible(true);
    }
}
