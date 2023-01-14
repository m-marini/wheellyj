/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.swing;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.mmarini.wheelly.apis.RobotStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class EnvironmentFrame extends JFrame {
    private static final Dimension DEFAULT_SIZE = new Dimension(800, 800);
    private static final Logger logger = LoggerFactory.getLogger(EnvironmentFrame.class);
    private final EnvironmentPanel envPanel;

    private Runnable startCallback;
    private boolean silent;

    public EnvironmentFrame(String title) throws HeadlessException {
        super(title);
        setResizable(false);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                if (startCallback != null) {
                    Completable.fromRunnable(startCallback)
                            .subscribeOn(Schedulers.computation())
                            .doOnComplete(EnvironmentFrame.this::handleOnCompletion)
                            .doOnError(EnvironmentFrame.this::handleOnError)
                            .onErrorComplete()
                            .delay(50, TimeUnit.MILLISECONDS)
                            .subscribe(EnvironmentFrame.this::dispose);
                }
            }
        });
        envPanel = new EnvironmentPanel();
        Container content = getContentPane();
        content.setLayout(new BorderLayout());
        content.add(envPanel, BorderLayout.CENTER);
    }

    /**
     * Handles on completion event
     */
    private void handleOnCompletion() {
        logger.info("Completed");
        if (EnvironmentFrame.this.isVisible() && !isSilent()) {
            JOptionPane.showMessageDialog(EnvironmentFrame.this,
                    "Completed", "Information", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Handle on error event
     *
     * @param err the error
     */
    private void handleOnError(Throwable err) {
        logger.error(err.getMessage(), err);
        if (!isSilent()) {
            JOptionPane.showMessageDialog(EnvironmentFrame.this,
                    err.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Returns true is the frame is silent (no window messages)
     */
    public boolean isSilent() {
        return silent;
    }

    /**
     * Returns true to silent  the (no window messages)
     */
    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    /**
     * Set the callback when start process
     *
     * @param startFunc the callback function
     */
    public void onStart(Runnable startFunc) {
        this.startCallback = startFunc;
    }

    /**
     * Set the obstacle map
     *
     * @param map the obstacle map
     */
    public void setObstacleMap(List<Point2D> map) {
        envPanel.setObstacleMap(map);
    }

    public void setObstacleSize(double obstacleSize) {
        this.envPanel.setObstacleSize(obstacleSize);
    }

    public void setReward(double reward) {
        envPanel.setReward(reward);
    }

    /**
     * Set the robot status to draw
     *
     * @param status the robot status
     */
    public void setRobotStatus(RobotStatus status) {
        envPanel.setRobotStatus(status);
    }

    public void setTimeRatio(double timeRatio) {
        envPanel.setTimeRatio(timeRatio);
    }

    /**
     * Run the windows
     */
    public void start() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(DEFAULT_SIZE);
        Point location = new Point(
                (screenSize.width - DEFAULT_SIZE.width) / 2,
                (screenSize.height - DEFAULT_SIZE.height) / 2
        );
        setLocation(location);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setVisible(true);
    }
}
