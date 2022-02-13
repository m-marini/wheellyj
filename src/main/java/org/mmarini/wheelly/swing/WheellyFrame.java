package org.mmarini.wheelly.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 *
 */
public class WheellyFrame extends JFrame {
    private final JMenuItem preferences;
    private final Dashboard dashboard;
    private final Radar radar;

    /**
     *
     */
    public WheellyFrame() {
        this.setTitle("Wheelly");
        this.preferences = new JMenuItem("Preferences");
        this.dashboard = new Dashboard();
        this.radar = new Radar();

        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, radar, dashboard);
        contentPane.add(split, BorderLayout.CENTER);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                split.setDividerLocation(0.67);
            }
        });

        setJMenuBar(createMenuBar());
    }

    public Radar getRadar() {
        return radar;
    }

    /**
     *
     */
    public Dashboard getDashboard() {
        return dashboard;
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
    private JMenuBar createMenuBar() {
        JMenuBar jMenuBar = new JMenuBar();
        JMenu prefs = new JMenu("File");
        prefs.add(preferences);
        jMenuBar.add(prefs);
        return jMenuBar;
    }
}
