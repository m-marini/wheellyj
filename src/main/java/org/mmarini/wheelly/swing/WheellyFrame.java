package org.mmarini.wheelly.swing;

import hu.akarnokd.rxjava3.swing.SwingObservable;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class WheellyFrame extends JFrame {
    public static final double RADAR_MAX_DISTANCE = 1;
    private final static Logger logger = LoggerFactory.getLogger(WheellyFrame.class);
    private static final int HIGH_WATER = 100;
    private static final int LOW_WATER = 50;
    private static final DateTimeFormatter formatter = new DateTimeFormatterBuilder()
            .appendPattern("HH:mm:ss")
            .toFormatter();
    private final JMenuItem preferences;
    private final Dashboard dashboard;
    private final RLMonitor monitor;
    private final Radar radar;
    private final GlobalMap globalMap;
    private final JLabel statusBar;
    private final JTextArea console;
    private final JToolBar toolBar;
    private final JToggleButton scannerView;
    private final JToggleButton contourView;
    private final JToggleButton prohibitedView;
    private final Flowable<ActionEvent> scannerViewFlow;
    private final Flowable<ActionEvent> contourViewFlow;
    private final Flowable<ActionEvent> prohibitedViewFlow;

    /**
     *
     */
    public WheellyFrame() {
        this.setTitle("Wheelly");
        this.preferences = new JMenuItem("Preferences");
        this.dashboard = new Dashboard();
        this.radar = new Radar();
        this.monitor = new RLMonitor();
        this.globalMap = new GlobalMap();
        this.statusBar = new JLabel("Idle");
        this.console = new JTextArea();
        this.toolBar = new JToolBar();
        this.scannerView = new JToggleButton("Scanner view");
        this.prohibitedView = new JToggleButton("Prohibited view");
        this.contourView = new JToggleButton("Contour view");
        this.scannerViewFlow = SwingObservable.actions(scannerView).toFlowable(BackpressureStrategy.DROP);
        this.contourViewFlow = SwingObservable.actions(contourView).toFlowable(BackpressureStrategy.DROP);
        this.prohibitedViewFlow = SwingObservable.actions(prohibitedView).toFlowable(BackpressureStrategy.DROP);

        this.prohibitedView.setSelected(true);

        radar.setMaxDistance(RADAR_MAX_DISTANCE);
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
        console.setLineWrap(false);
        console.setForeground(Color.WHITE);
        console.setBackground(Color.BLACK);
        console.setFont(Font.decode("Monospaced 14"));
        console.setEditable(false);
        DefaultCaret caret = (DefaultCaret) console.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        // Create content
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        JComponent console = createConsole();
        JSplitPane horizSplit2 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, radar, console);
        horizSplit2.setResizeWeight(0.5);
        JSplitPane horizSplit1 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, globalMap, horizSplit2);
        horizSplit1.setResizeWeight(0.67);
        JSplitPane horizSplit3 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, dashboard, monitor);
        horizSplit3.setResizeWeight(0.5);
        JSplitPane vertSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, horizSplit1, horizSplit3);

        createToolbar();

        contentPane.add(toolBar, BorderLayout.NORTH);
        contentPane.add(vertSplit, BorderLayout.CENTER);
        contentPane.add(statusBar, BorderLayout.SOUTH);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                vertSplit.setDividerLocation(0.67);
                horizSplit1.setDividerLocation(0.33);
                Completable.timer(1, TimeUnit.SECONDS)
                        .doOnComplete(() -> horizSplit2.setDividerLocation(0.5))
                        .subscribe();
            }
        });
        setJMenuBar(createMenuBar());

    }

    /**
     *
     */
    private JComponent createConsole() {
        return new JScrollPane(console);
    }

    /**
     *
     */
    private JMenuBar createMenuBar() {
        JMenuBar jMenuBar = new JMenuBar();
        JMenu prefs = new JMenu("File");
        prefs.add(preferences);
        jMenuBar.add(prefs);
        return jMenuBar;
    }

    private void createToolbar() {
        ButtonGroup group = new ButtonGroup();
        group.add(scannerView);
        group.add(prohibitedView);
        group.add(contourView);

        toolBar.add(scannerView);
        toolBar.add(prohibitedView);
        toolBar.add(contourView);
    }

    public Flowable<ActionEvent> getContourViewFlow() {
        return contourViewFlow;
    }

    /**
     *
     */
    public Dashboard getDashboard() {
        return dashboard;
    }

    public GlobalMap getGlobalMap() {
        return globalMap;
    }

    public RLMonitor getMonitor() {
        return monitor;
    }

    /**
     *
     */
    public JMenuItem getPreferences() {
        return preferences;
    }

    public Flowable<ActionEvent> getProhibitedViewFlow() {
        return prohibitedViewFlow;
    }

    /**
     *
     */
    public Radar getRadar() {
        return radar;
    }

    public Flowable<ActionEvent> getScannerViewFlow() {
        return scannerViewFlow;
    }

    /**
     * @param text the text
     */
    public void log(String text) {
        console.append(formatter.format(LocalDateTime.now()));
        console.append(" ");
        console.append(text);
        console.append("\n");
        if (console.getLineCount() > HIGH_WATER) {
            try {
                int off = console.getLineStartOffset(LOW_WATER);
                Document doc = console.getDocument();
                doc.remove(0, off);
            } catch (BadLocationException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    /**
     * @param text the text
     */
    public WheellyFrame setInfo(String text) {
        statusBar.setText(text);
        return this;
    }
}
