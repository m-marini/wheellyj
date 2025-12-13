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
import org.mmarini.wheelly.apis.WheellyContactsMessage;
import org.mmarini.wheelly.apis.WheellyMotionMessage;
import org.mmarini.wheelly.apis.WheellySupplyMessage;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.time.Instant;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Shows the record filters' user interface
 */
public class RecordFilterPanel extends JPanel {
    public static final Predicate<DumpRecord> CONTACT_FILTER = record -> record instanceof DumpRecord.MessageDumpRecord &&
            ((DumpRecord.MessageDumpRecord<?>) record).message() instanceof WheellyContactsMessage;
    public static final Predicate<DumpRecord> MOTION_FILTER = record -> record instanceof DumpRecord.MessageDumpRecord &&
            ((DumpRecord.MessageDumpRecord<?>) record).message() instanceof WheellyMotionMessage;
    public static final Predicate<DumpRecord> SUPPLY_FILTER = record -> record instanceof DumpRecord.MessageDumpRecord &&
            ((DumpRecord.MessageDumpRecord<?>) record).message() instanceof WheellySupplyMessage;
    public static final Predicate<DumpRecord> ERROR_FILTER = record -> record instanceof DumpRecord.ReadDumpRecordIntf && record.data().startsWith("!! ");
    public static final Predicate<DumpRecord> OTHER_READ_FILTER = Predicate.not(
                    SUPPLY_FILTER
                            .or(MOTION_FILTER)
                            .or(ERROR_FILTER)
                            .or(CONTACT_FILTER))
            .and(record -> record instanceof DumpRecord.ReadDumpRecordIntf);
    public static final Predicate<DumpRecord> MOVE_FILTER = record -> record instanceof DumpRecord.WriteDumpRecord && record.data().startsWith("mv ");
    public static final Predicate<DumpRecord> HALT_FILTER = record -> record instanceof DumpRecord.WriteDumpRecord && record.data().equals("ha");
    public static final Predicate<DumpRecord> SCAN_FILTER = record -> record instanceof DumpRecord.WriteDumpRecord && record.data().startsWith("sc ");
    public static final Predicate<DumpRecord> OTHER_WRITE_FILTER = Predicate.not(MOVE_FILTER.or(HALT_FILTER).or(SCAN_FILTER))
            .and(record -> record instanceof DumpRecord.WriteDumpRecord);
    private static final Predicate<DumpRecord> NONE_FILTER = record -> false;

    private final JCheckBox motionBtn;
    private final JCheckBox proxyBtn;
    private final JCheckBox supplyBtn;
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
        this.proxyBtn = SwingUtils.createCheckBox("RecordFiltersMenu.proxyButton");
        this.motionBtn = SwingUtils.createCheckBox("RecordFiltersMenu.motionButton");
        this.supplyBtn = SwingUtils.createCheckBox("RecordFiltersMenu.supplyButton");
        this.contactsBtn = SwingUtils.createCheckBox("RecordFiltersMenu.contactsButton");
        this.errorBtn = SwingUtils.createCheckBox("RecordFiltersMenu.errorButton");
        this.readBtn = SwingUtils.createCheckBox("RecordFiltersMenu.readButton");
        this.moveBtn = SwingUtils.createCheckBox("RecordFiltersMenu.moveButton");
        this.haltBtn = SwingUtils.createCheckBox("RecordFiltersMenu.haltButton");
        this.scanBtn = SwingUtils.createCheckBox("RecordFiltersMenu.scanButton");
        this.writeBtn = SwingUtils.createCheckBox("RecordFiltersMenu.writeButton");
        this.beforeBtn = SwingUtils.createCheckBox("RecordFiltersMenu.beforeButton");
        this.afterBtn = SwingUtils.createCheckBox("RecordFiltersMenu.afterButton");
        this.allTypesBtn = SwingUtils.createCheckBox("RecordFiltersMenu.allTypesButton");
        this.noneTypesBtn = SwingUtils.createCheckBox("RecordFiltersMenu.noneTypesButton");
        this.allTimeBtn = SwingUtils.createCheckBox("RecordFiltersMenu.allTimeButton");
        this.noneTimeBtn = SwingUtils.createCheckBox("RecordFiltersMenu.noneTimeButton");
        this.offsetFlow = PublishProcessor.create();
        this.offset = Instant.now();
        init();
        createContent();
    }

    /**
     * Creates the panel content
     */
    private void createContent() {
        new GridLayoutHelper<>(this).modify("insets,2,2 w nofill weight,1,1")
                .modify("at,0,0").add(motionBtn)
                .modify("at,0,1").add(proxyBtn)
                .modify("at,0,2").add(contactsBtn)
                .modify("at,0,3").add(supplyBtn)
                .modify("at,0,4").add(errorBtn)
                .modify("at,0,5").add(readBtn)
                .modify("at,0,6").add(moveBtn)
                .modify("at,0,7").add(haltBtn)
                .modify("at,0,8").add(scanBtn)
                .modify("at,0,9").add(writeBtn)
                .modify("at,0,10").add(allTypesBtn)
                .modify("at,0,11").add(noneTypesBtn)
                .modify("at,0,12").add(new JSeparator())
                .modify("at,0,13").add(beforeBtn)
                .modify("at,0,14").add(afterBtn)
                .modify("at,0,15").add(allTimeBtn)
                .modify("at,0,16").add(noneTimeBtn);
    }

    /**
     * Returns the current record filter
     *
     * @param ignored the ignored parameter
     */
    private Predicate<DumpRecord> createFilter(Instant ignored) {
        Predicate<DumpRecord> afterFilter = afterBtn.isSelected()
                ? record -> !record.instant().isBefore(offset)
                : NONE_FILTER;
        Predicate<DumpRecord> beforeFilter = beforeBtn.isSelected()
                ? record -> !record.instant().isAfter(offset)
                : NONE_FILTER;
        Predicate<DumpRecord> timeFilter = afterFilter.or(beforeFilter);

        Predicate<DumpRecord> motionFilter = motionBtn.isSelected()
                ? MOTION_FILTER
                : NONE_FILTER;
        Predicate<DumpRecord> supplyFilter = supplyBtn.isSelected()
                ? SUPPLY_FILTER
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

        Predicate<DumpRecord> typesFilter = Stream.of(motionFilter, supplyFilter, contactsFilter, errorFilter, readFilter,
                        moveFilter, haltFilter, scanFilter, writeFilter)
                .reduce(Predicate::or)
                .orElseThrow();
        return timeFilter.and(typesFilter);
    }

    /**
     * Handles all localTime button
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
            Stream.of(motionBtn, proxyBtn, contactsBtn, supplyBtn, errorBtn, readBtn, moveBtn, haltBtn, scanBtn, writeBtn, allTypesBtn)
                    .forEach(btn -> btn.setSelected(true));
        }
    }

    /**
     * Handles none localTime button
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
            Stream.of(motionBtn, proxyBtn, contactsBtn, supplyBtn, errorBtn, readBtn, moveBtn, haltBtn, scanBtn, writeBtn, noneTypesBtn)
                    .forEach(btn -> btn.setSelected(false));
        }
    }

    /**
     * Initializes the panel
     */
    private void init() {
        Stream.of(motionBtn, proxyBtn, contactsBtn, supplyBtn, errorBtn, readBtn, moveBtn, haltBtn, scanBtn, writeBtn, afterBtn, allTimeBtn, allTypesBtn)
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

        filtersFlow = Stream.of(motionBtn, proxyBtn, supplyBtn, contactsBtn, errorBtn, readBtn, moveBtn, haltBtn, scanBtn, writeBtn, beforeBtn, afterBtn)
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

    /**
     * Sets the localTime offset
     *
     * @param offset the localTime offset
     */
    public void setOffset(Instant offset) {
        this.offset = offset;
        offsetFlow.onNext(offset);
    }
}
