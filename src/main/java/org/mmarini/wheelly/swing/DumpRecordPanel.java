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
    private final JFormattedTextField distanceField;
    private final JCheckBox haltField;
    private final JCheckBox forwardBlockField;
    private final JCheckBox backwardBlockField;
    private final Flowable<Instant> offsetFlow;
    private final JFormattedTextField directionField;
    private final JFormattedTextField leftPpsField;
    private final JFormattedTextField rightPpsField;
    private final JFormattedTextField leftPowerField;
    private final JFormattedTextField rightPowerField;
    private final JFormattedTextField leftTargetPpsField;
    private final JFormattedTextField rightTargetPpsField;
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
        this.leftTargetPpsField = new JFormattedTextField();
        this.rightTargetPpsField = new JFormattedTextField();
        this.leftPowerField = new JFormattedTextField();
        this.rightPowerField = new JFormattedTextField();
        this.sensorField = new JFormattedTextField();
        this.echoField = new JFormattedTextField();
        this.distanceField = new JFormattedTextField();
        this.haltField = SwingUtils.createCheckBox("DumpRecordPanel.haltButton");
        this.forwardBlockField = SwingUtils.createCheckBox("DumpRecordPanel.forwardBlockButton");
        this.backwardBlockField = SwingUtils.createCheckBox("DumpRecordPanel.backwardBlockButton");
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
        Stream.of(directionField, leftPpsField, rightPpsField, echoField, distanceField, sensorField,
                        leftPowerField, rightPowerField, leftTargetPpsField, rightPpsField)
                .forEach(c -> c.setText(""));
        Stream.of(haltField, backwardBlockField, forwardBlockField)
                .forEach(f -> f.setSelected(false));
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
                .modify("at,0,3")
                .add("DumpRecordPanel.leftTargetPps.label")
                .modify("at,1,3")
                .add("DumpRecordPanel.rightTargetPps.label")
                .modify("at,0,5")
                .add("DumpRecordPanel.leftPower.label")
                .modify("at,1,5")
                .add("DumpRecordPanel.rightPower.label")
                .modify("at,0,7 nospan")
                .add("DumpRecordPanel.sensor.label")
                .modify("at,1,7")
                .add("DumpRecordPanel.echo.label")
                .modify("at,0,9")
                .add("DumpRecordPanel.distance.label")
                .modify("at,1,0 nospan")
                .add(directionField)
                .modify("at,0,2")
                .add(leftPpsField)
                .modify("at,1,2")
                .add(rightPpsField)
                .modify("at,0,4")
                .add(leftTargetPpsField)
                .modify("at,1,4")
                .add(rightTargetPpsField)
                .modify("at,1,6")
                .add(rightPowerField)
                .modify("at,0,6")
                .add(leftPowerField)
                .modify("at,0,8")
                .add(sensorField)
                .modify("at,1,8")
                .add(echoField)
                .modify("at,1,9")
                .add(distanceField)
                .modify("at,0,11 noweight")
                .add(forwardBlockField)
                .modify("at,1,11 noweight")
                .add(backwardBlockField)
                .modify("at,0,12 span,2,1")
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
            if (record instanceof DumpRecord.MessageDumpRecord) {
                setStatusFields((DumpRecord.MessageDumpRecord<?>) record);
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
        leftTargetPpsField.setColumns(6);
        rightTargetPpsField.setColumns(6);
        leftPowerField.setColumns(7);
        rightPowerField.setColumns(7);
        echoField.setColumns(5);
        distanceField.setColumns(5);
        offsetButton.setMargin(new Insets(0, 0, 0, 0));
        Stream.of(instantField, dataField, typeField, elapsedField,
                        directionField, leftPpsField, rightPpsField, echoField,
                        leftTargetPpsField, rightTargetPpsField, leftPowerField, rightPowerField,
                        distanceField, sensorField)
                .forEach(c -> {
                    c.setMinimumSize(c.getPreferredSize());
                    c.setEditable(false);
                });
        Stream.of(haltField, forwardBlockField, backwardBlockField)
                .forEach(f -> f.setEnabled(false));
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
    private void setStatusFields(DumpRecord.MessageDumpRecord<?> record) {
    }

    /*
     * Sets the status fields
     *
     * @param record the record
     */
    /*
    private void setStatusFields(DumpRecord.StatusDumpRecord record) {
        WheellyStatus status = record.getStatus();
        directionField.setValue(status.getDirection());
        leftPpsField.setValue(status.getLeftPps());
        rightPpsField.setValue(status.getRightPps());
        sensorField.setValue(status.getSensorDirection());
        echoField.setValue(status.getEchoTime());
        distanceField.setValue(status.getEchoTime() * 100 / 5887);
        haltField.setSelected(status.isHalt());
        forwardBlockField.setSelected(!status.canMoveForward());
        backwardBlockField.setSelected(!status.canMoveBackward());
        statusPanel.setVisible(true);
        leftPowerField.setValue(status.getLeftPower());
        rightPowerField.setValue(status.getRightPower());
        leftTargetPpsField.setValue(status.getLeftTargetPps());
        rightTargetPpsField.setValue(status.getRightTargetPps());
    }

     */
}
