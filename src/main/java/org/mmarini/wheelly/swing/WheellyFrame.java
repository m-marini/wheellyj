package org.mmarini.wheelly.swing;

import io.reactivex.rxjava3.core.Completable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Document;
import java.awt.*;
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
    private final static Logger logger = LoggerFactory.getLogger(WheellyFrame.class);
    private static final int HIGH_WATER = 100;
    private static final int LOW_WATER = 50;
    private static final DateTimeFormatter formatter = new DateTimeFormatterBuilder()
            .appendPattern("HH:mm:ss")
            .toFormatter();
    public static final double RADAR_MAX_DISTANCE = 1;
    private final JMenuItem preferences;
    private final Dashboard dashboard;
    private final Radar radar;
    private final GlobalMap globalMap;
    private final JLabel statusBar;
    private final JTextArea console;

    /**
     *
     */
    public WheellyFrame() {
        this.setTitle("Wheelly");
        this.preferences = new JMenuItem("Preferences");
        this.dashboard = new Dashboard();
        this.radar = new Radar();
        this.globalMap = new GlobalMap();
        this.statusBar = new JLabel("Idle");
        this.console = new JTextArea();

        radar.setMaxDistance(RADAR_MAX_DISTANCE);
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
        console.setLineWrap(false);
        console.setForeground(Color.WHITE);
        console.setBackground(Color.BLACK);
        console.setFont(Font.decode("Monospaced 14"));
        console.setEditable(false);
        DefaultCaret caret = (DefaultCaret) console.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        JComponent console = createConsole();
        JSplitPane horizSplit2 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, radar, console);
        horizSplit2.setResizeWeight(0.5);
        JSplitPane horizSplit1 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, globalMap, horizSplit2);
        horizSplit1.setResizeWeight(0.67);
        JSplitPane vertSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, horizSplit1, dashboard);
        contentPane.add(vertSplit, BorderLayout.CENTER);
        contentPane.add(statusBar, BorderLayout.SOUTH);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                vertSplit.setDividerLocation(0.67);
                horizSplit1.setDividerLocation(0.33);
                Completable.timer(1, TimeUnit.SECONDS)
                        .doOnComplete(() -> {
                            horizSplit2.setDividerLocation(0.5);
                        }).subscribe();
            }
        });

        setJMenuBar(createMenuBar());
    }

    /**
     * @return
     */
    private JComponent createConsole() {
        JScrollPane sp = new JScrollPane(console);
        return sp;
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

    /**
     *
     */
    public Dashboard getDashboard() {
        return dashboard;
    }

    public GlobalMap getGlobalMap() {
        return globalMap;
    }

    /**
     *
     */
    public JMenuItem getPreferences() {
        return preferences;
    }

    /**
     *
     */
    public Radar getRadar() {
        return radar;
    }

    /**
     * @param text
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
     * @param text
     */
    public WheellyFrame setInfo(String text) {
        statusBar.setText(text);
        return this;
    }
}
