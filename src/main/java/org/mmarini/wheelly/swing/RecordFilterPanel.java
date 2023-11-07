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
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.swing.SwingUtils;
import org.mmarini.wheelly.apis.DumpRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.time.Instant;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Shows the record filters' user interface
 */
public class RecordFilterPanel extends JPanel {
    public static final Predicate<DumpRecord> CONTACT_FILTER = record -> record instanceof DumpRecord.StatusDumpRecord &&
            (!((DumpRecord.StatusDumpRecord) record).getStatus().canMoveForward()
                    || !((DumpRecord.StatusDumpRecord) record).getStatus().getCanMoveBackward());
    public static final Predicate<DumpRecord> STATUS_FILTER = CONTACT_FILTER.negate().and(record -> record instanceof DumpRecord.StatusDumpRecord);
    public static final Predicate<DumpRecord> OTHER_READ_FILTER = Predicate.not(STATUS_FILTER.or(ERROR_FILTER).or(CONTACT_FILTER))
            .and(record -> record instanceof DumpRecord.ReadDumpRecord);
    public static final Predicate<DumpRecord> ERROR_FILTER = record -> record instanceof DumpRecord.ReadDumpRecord && record.getData().startsWith("!! ");
    public static final Predicate<DumpRecord> MOVE_FILTER = record -> record instanceof DumpRecord.WriteDumpRecord && record.getData().startsWith("mv ");
    public static final Predicate<DumpRecord> HALT_FILTER = record -> record instanceof DumpRecord.WriteDumpRecord && record.getData().equals("ha");
    public static final Predicate<DumpRecord> SCAN_FILTER = record -> record instanceof DumpRecord.WriteDumpRecord && record.getData().startsWith("sc ");
    public static final Predicate<DumpRecord> OTHER_WRITE_FILTER = Predicate.not(MOVE_FILTER.or(HALT_FILTER).or(SCAN_FILTER))
            .and(record -> record instanceof DumpRecord.WriteDumpRecord);
    private static final Predicate<DumpRecord> NONE_FILTER = record -> false;
    private static final Logger logger = LoggerFactory.getLogger(RecordFilterPanel.class);
    private final JCheckBox statusBtn;
    private final JCheckBox contactsBtn;
    private final JCheckBox errorBtn;
    private final JCheckBox readBtn;
    private final JCheckBox moveBtn;
    private final JCheckBox haltBtn;
    private final JCheckBox scanBtn;
    private final JCheckBox writeBtn;
    private final JCheckBox beforeBtn;
    private final JCheckBox afterBtn;
    private final JCheckBox allTypesBtn;
    private final JCheckBox noneTypesBtn;
    private final JCheckBox allTimeBtn;
    private final JCheckBox noneTimeBtn;
    private final PublishProcessor<Instant> offsetFlow;
    private Flowable<Predicate<DumpRecord>> filtersFlow;
    private Instant offset;

    /**
     * Creates the panel
     */
    public RecordFilterPanel() {
        this.statusBtn = SwingUtils.createCheckBox("RecordFiltersPanel.statusButton");
        this.contactsBtn = SwingUtils.createCheckBox("RecordFiltersPanel.contactsButton");
        this.errorBtn = SwingUtils.createCheckBox("RecordFiltersPanel.errorButton");
        this.readBtn = SwingUtils.createCheckBox("RecordFiltersPanel.readButton");
        this.moveBtn = SwingUtils.createCheckBox("RecordFiltersPanel.moveButton");
        this.haltBtn = SwingUtils.createCheckBox("RecordFiltersPanel.haltButton");
        this.scanBtn = SwingUtils.createCheckBox("RecordFiltersPanel.scanButton");
        this.writeBtn = SwingUtils.createCheckBox("RecordFiltersPanel.writeButton");
        this.beforeBtn = SwingUtils.createCheckBox("RecordFiltersPanel.beforeButton");
        this.afterBtn = SwingUtils.createCheckBox("RecordFiltersPanel.afterButton");
        this.allTypesBtn = SwingUtils.createCheckBox("RecordFiltersPanel.allTypesButton");
        this.noneTypesBtn = SwingUtils.createCheckBox("RecordFiltersPanel.noneTypesButton");
        this.allTimeBtn = SwingUtils.createCheckBox("RecordFiltersPanel.allTimeButton");
        this.noneTimeBtn = SwingUtils.createCheckBox("RecordFiltersPanel.noneTimeButton");
        this.offsetFlow = PublishProcessor.create();
        this.offset = Instant.now();
        init();
        createContent();
    }

    /**
     * Creates the panel content
     */
    private void createContent() {
        JPanel typesFilterPanel = new GridLayoutHelper<>(new JPanel())
                .modify("insets,4,4 w weight,1,0")
                .modify("at,0,0")
                .add(contactsBtn)
                .modify("at,0,1")
                .add(statusBtn)
                .modify("at,0,2")
                .add(errorBtn)
                .modify("at,0,3")
                .add(readBtn)
                .modify("at,0,4")
                .add(moveBtn)
                .modify("at,0,5")
                .add(haltBtn)
                .modify("at,0,6")
                .add(scanBtn)
                .modify("at,0,7")
                .add(writeBtn)
                .modify("at,0,8")
                .add(allTypesBtn)
                .modify("at,0,9")
                .add(noneTypesBtn)
                .getContainer();
        typesFilterPanel.setBorder(BorderFactory.createTitledBorder(
                Messages.getString("RecordsFiltersPanel.typesFilterPanel.title")));

        JPanel timeFilterPanel = new GridLayoutHelper<>(new JPanel())
                .modify("insets,4,4 w weight,1,0")
                .modify("at,0,0")
                .add(beforeBtn)
                .modify("at,0,1")
                .add(afterBtn)
                .modify("at,0,2")
                .add(allTimeBtn)
                .modify("at,0,3")
                .add(noneTimeBtn)
                .getContainer();
        timeFilterPanel.setBorder(BorderFactory.createTitledBorder(
                Messages.getString("RecordFiltersPanel.timeFilterPanel.title")));

        new GridLayoutHelper<>(this)
                .modify("insets,4,4 n weight,0,1")
                .modify("at,0,0")
                .add(typesFilterPanel)
                .modify("at,1,0")
                .add(timeFilterPanel);
    }

    /**
     * Returns the current record filter
     *
     * @param ignored the ingnored parameter
     */
    private Predicate<DumpRecord> createFilter(Instant ignored) {
        Predicate<DumpRecord> afterFilter = afterBtn.isSelected()
                ? record -> !record.getInstant().isBefore(offset)
                : NONE_FILTER;
        Predicate<DumpRecord> beforeFilter = beforeBtn.isSelected()
                ? record -> !record.getInstant().isAfter(offset)
                : NONE_FILTER;
        Predicate<DumpRecord> timeFilter = afterFilter.or(beforeFilter);

        Predicate<DumpRecord> statusFilter = statusBtn.isSelected()
                ? STATUS_FILTER
                : NONE_FILTER;
        Predicate<DumpRecord> contactsFilter = contactsBtn.isSelected()
                ? CONTACT_FILTER
                : NONE_FILTER;
        Predicate<DumpRecord> errorFilter = errorBtn.isSelected()
                ? ERROR_FILTER
                : NONE_FILTER;
        Predicate<DumpRecord> readFilter = readBtn.isSelected()
                ? OTHER_READ_FILTER
                : NONE_FILTER;
        Predicate<DumpRecord> moveFilter = moveBtn.isSelected()
                ? MOVE_FILTER
                : NONE_FILTER;
        Predicate<DumpRecord> haltFilter = haltBtn.isSelected()
                ? HALT_FILTER
                : NONE_FILTER;
        Predicate<DumpRecord> scanFilter = scanBtn.isSelected()
                ? SCAN_FILTER
                : NONE_FILTER;
        Predicate<DumpRecord> writeFilter = writeBtn.isSelected()
                ? OTHER_WRITE_FILTER
                : NONE_FILTER;

        Predicate<DumpRecord> typesFilter = Stream.of(statusFilter, contactsFilter, errorFilter, readFilter,
                        moveFilter, haltFilter, scanFilter, writeFilter)
                .reduce(Predicate::or)
                .orElseThrow();
        return timeFilter.and(typesFilter);
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
        this.offset = offset;
        offsetFlow.onNext(offset);
    }

    /**
     * Handles all time button
     *
     * @param event the event
     */
    private void handleAllTimeButton(ActionEvent event) {
        Stream.of(beforeBtn, afterBtn, noneTypesBtn)
                .forEach(btn -> btn.setSelected(true));
    }

    /**
     * Handles all type button
     *
     * @param event the event
     */
    private void handleAllTypesButton(ActionEvent event) {
        if (!allTypesBtn.isSelected()) {
            Stream.of(statusBtn, contactsBtn, errorBtn, readBtn, moveBtn, haltBtn, scanBtn, writeBtn, allTypesBtn)
                    .forEach(btn -> btn.setSelected(true));
        }
    }

    /**
     * Handles none time button
     *
     * @param event the event
     */
    private void handleNoneTimeButton(ActionEvent event) {
        Stream.of(beforeBtn, afterBtn, noneTimeBtn)
                .forEach(btn -> btn.setSelected(false));
    }

    /**
     * Handles none type button
     *
     * @param event the event
     */
    private void handleNoneTypeButton(ActionEvent event) {
        if (noneTypesBtn.isSelected()) {
            Stream.of(statusBtn, contactsBtn, errorBtn, readBtn, moveBtn, haltBtn, scanBtn, writeBtn, noneTypesBtn)
                    .forEach(btn -> btn.setSelected(false));
        }
    }

    /**
     * Initializes the panel
     */
    private void init() {
        Stream.of(statusBtn, contactsBtn, errorBtn, readBtn, moveBtn, haltBtn, scanBtn, writeBtn, afterBtn, allTimeBtn, allTypesBtn)
                .forEach(btn -> btn.setSelected(true));
        Flowable<ActionEvent> noneTypesFlow = SwingObservable.actions(noneTypesBtn)
                .toFlowable(BackpressureStrategy.LATEST)
                .doOnNext(this::handleNoneTypeButton)
                .publish()
                .autoConnect();
        Flowable<ActionEvent> noneTimeFlow = SwingObservable.actions(noneTimeBtn)
                .toFlowable(BackpressureStrategy.LATEST)
                .doOnNext(this::handleNoneTimeButton)
                .publish()
                .autoConnect();
        Flowable<ActionEvent> allTimeFlow = SwingObservable.actions(allTimeBtn)
                .toFlowable(BackpressureStrategy.LATEST)
                .doOnNext(this::handleAllTimeButton)
                .publish()
                .autoConnect();
        Flowable<ActionEvent> allTypesFlow = SwingObservable.actions(allTypesBtn)
                .toFlowable(BackpressureStrategy.LATEST)
                .doOnNext(this::handleAllTypesButton)
                .publish()
                .autoConnect();

        filtersFlow = Stream.of(statusBtn, contactsBtn, errorBtn, readBtn, moveBtn, haltBtn, scanBtn, writeBtn, beforeBtn, afterBtn)
                .map(SwingObservable::actions)
                .map(o -> o.toFlowable(BackpressureStrategy.LATEST))
                .reduce(Flowable::mergeWith)
                .orElseThrow()
                .mergeWith(noneTypesFlow)
                .mergeWith(noneTimeFlow)
                .mergeWith(allTimeFlow)
                .mergeWith(allTypesFlow)
                .mergeWith(allTimeFlow)
                .map(x -> offset)
                .mergeWith(offsetFlow)
                .map(this::createFilter);
    }

    /**
     * Returns the filter flow
     */
    public Flowable<Predicate<DumpRecord>> readFilters() {
        return filtersFlow;
    }
}
