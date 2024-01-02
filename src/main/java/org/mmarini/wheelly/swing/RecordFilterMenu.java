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
import org.mmarini.swing.SwingUtils;
import org.mmarini.wheelly.apis.*;
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
public class RecordFilterMenu extends JMenu {
    public static final Predicate<DumpRecord> PROXY_FILTER = record -> record instanceof DumpRecord.MessageDumpRecord &&
            ((DumpRecord.MessageDumpRecord<?>) record).message() instanceof WheellyProxyMessage;
    public static final Predicate<DumpRecord> CONTACT_FILTER = record -> record instanceof DumpRecord.MessageDumpRecord &&
            ((DumpRecord.MessageDumpRecord<?>) record).message() instanceof WheellyContactsMessage;
    public static final Predicate<DumpRecord> MOTION_FILTER = record -> record instanceof DumpRecord.MessageDumpRecord &&
            ((DumpRecord.MessageDumpRecord<?>) record).message() instanceof WheellyMotionMessage;
    public static final Predicate<DumpRecord> SUPPLY_FILTER = record -> record instanceof DumpRecord.MessageDumpRecord &&
            ((DumpRecord.MessageDumpRecord<?>) record).message() instanceof WheellySupplyMessage;
    public static final Predicate<DumpRecord> ERROR_FILTER = record -> record instanceof DumpRecord.ReadDumpRecordIntf && record.data().startsWith("!! ");
    public static final Predicate<DumpRecord> OTHER_READ_FILTER = Predicate.not(
                    PROXY_FILTER
                            .or(SUPPLY_FILTER)
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
    private static final Logger logger = LoggerFactory.getLogger(RecordFilterMenu.class);

    private final JCheckBoxMenuItem motionBtn;
    private final JCheckBoxMenuItem proxyBtn;
    private final JCheckBoxMenuItem supplyBtn;
    private final JCheckBoxMenuItem contactsBtn;
    private final JCheckBoxMenuItem errorBtn;
    private final JCheckBoxMenuItem readBtn;
    private final JCheckBoxMenuItem moveBtn;
    private final JCheckBoxMenuItem haltBtn;
    private final JCheckBoxMenuItem scanBtn;
    private final JCheckBoxMenuItem writeBtn;
    private final JCheckBoxMenuItem beforeBtn;
    private final JCheckBoxMenuItem afterBtn;
    private final JCheckBoxMenuItem allTypesBtn;
    private final JCheckBoxMenuItem noneTypesBtn;
    private final JCheckBoxMenuItem allTimeBtn;
    private final JCheckBoxMenuItem noneTimeBtn;
    private final PublishProcessor<Instant> offsetFlow;
    private Flowable<Predicate<DumpRecord>> filtersFlow;
    private Instant offset;

    /**
     * Creates the panel
     */
    public RecordFilterMenu() {
        setText(Messages.getString("RecordFiltersMenu.name"));
        Messages.getStringOpt("RecordFiltersMenu.mnemonic")
                .map(s -> s.charAt(0))
                .ifPresent(this::setMnemonic);
        this.proxyBtn = SwingUtils.createCheckBoxMenuItem("RecordFiltersMenu.proxyButton");
        this.motionBtn = SwingUtils.createCheckBoxMenuItem("RecordFiltersMenu.motionBtn");
        this.supplyBtn = SwingUtils.createCheckBoxMenuItem("RecordFiltersMenu.supplyButton");
        this.contactsBtn = SwingUtils.createCheckBoxMenuItem("RecordFiltersMenu.contactsButton");
        this.errorBtn = SwingUtils.createCheckBoxMenuItem("RecordFiltersMenu.errorButton");
        this.readBtn = SwingUtils.createCheckBoxMenuItem("RecordFiltersMenu.readButton");
        this.moveBtn = SwingUtils.createCheckBoxMenuItem("RecordFiltersMenu.moveButton");
        this.haltBtn = SwingUtils.createCheckBoxMenuItem("RecordFiltersMenu.haltButton");
        this.scanBtn = SwingUtils.createCheckBoxMenuItem("RecordFiltersMenu.scanButton");
        this.writeBtn = SwingUtils.createCheckBoxMenuItem("RecordFiltersMenu.writeButton");
        this.beforeBtn = SwingUtils.createCheckBoxMenuItem("RecordFiltersMenu.beforeButton");
        this.afterBtn = SwingUtils.createCheckBoxMenuItem("RecordFiltersMenu.afterButton");
        this.allTypesBtn = SwingUtils.createCheckBoxMenuItem("RecordFiltersMenu.allTypesButton");
        this.noneTypesBtn = SwingUtils.createCheckBoxMenuItem("RecordFiltersMenu.noneTypesButton");
        this.allTimeBtn = SwingUtils.createCheckBoxMenuItem("RecordFiltersMenu.allTimeButton");
        this.noneTimeBtn = SwingUtils.createCheckBoxMenuItem("RecordFiltersMenu.noneTimeButton");
        this.offsetFlow = PublishProcessor.create();
        this.offset = Instant.now();
        init();
        createContent();
    }

    /**
     * Creates the panel content
     */
    private void createContent() {
        Stream.of(motionBtn,
                        proxyBtn,
                        contactsBtn,
                        supplyBtn,
                        errorBtn,
                        readBtn,
                        moveBtn,
                        haltBtn,
                        scanBtn,
                        writeBtn,
                        allTypesBtn,
                        noneTypesBtn)
                .forEach(this::add);
        add(new JSeparator());

        Stream.of(beforeBtn,
                        afterBtn,
                        allTimeBtn,
                        noneTimeBtn)
                .forEach(this::add);
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
        Predicate<DumpRecord> proxyFilter = proxyBtn.isSelected()
                ? PROXY_FILTER
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

        Predicate<DumpRecord> typesFilter = Stream.of(motionFilter, proxyFilter, supplyFilter, contactsFilter, errorFilter, readFilter,
                        moveFilter, haltFilter, scanFilter, writeFilter)
                .reduce(Predicate::or)
                .orElseThrow();
        return timeFilter.and(typesFilter);
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
            Stream.of(motionBtn, proxyBtn, contactsBtn, supplyBtn, errorBtn, readBtn, moveBtn, haltBtn, scanBtn, writeBtn, allTypesBtn)
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
     * Sets the time offset
     *
     * @param offset the time offset
     */
    public void setOffset(Instant offset) {
        this.offset = offset;
        offsetFlow.onNext(offset);
    }
}
