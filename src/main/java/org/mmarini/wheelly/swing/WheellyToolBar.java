/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
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

import javax.swing.*;

/**
 * Provides the toolbar containing the main control buttons of the Wheelly
 * graphical user interface.
 * <p>
 * The toolbar provides buttons for starting and stopping the robot,
 * resetting its state, clearing the map, and relocating the robot.
 * </p>
 * <p>
 * The buttons are initialised through {@link SwingUtils} and are exposed
 * through accessor methods so that client components can register action
 * listeners or otherwise configure their behaviour.
 * </p>
 *
 * @author Marco Marini
 */
public class WheellyToolBar extends JToolBar {
    /**
     * Button used to pause the current operation.
     */
    private final JButton pauseButton;

    /**
     * Button used to start or run the robot.
     */
    private final JButton playButton;

    /**
     * Button used to relocate the robot.
     */
    private final JButton relocateButton;

    /**
     * Button used to reset the robot
     */
    private final JButton resetButton;

    /**
     * Button used to clear the current map.
     */
    private final JButton clearMapButton;

    /**
     * Creates a new Wheelly toolbar and initialises its control buttons.
     * <p>
     * The buttons are added to the toolbar in the following order:
     * </p>
     * <ol>
     *     <li>Pause</li>
     *     <li>Play</li>
     *     <li>Reset</li>
     *     <li>Clear map</li>
     *     <li>Relocate</li>
     * </ol>
     */
    public WheellyToolBar() {
        this.relocateButton = SwingUtils.getInstance()
                .initButton(new JButton(), "WheellyToolBar.relocateButton");
        this.pauseButton = SwingUtils.getInstance()
                .initButton(new JButton(), "WheellyToolBar.pauseButton");
        this.playButton = SwingUtils.getInstance()
                .initButton(new JButton(), "WheellyToolBar.playButton");
        this.resetButton = SwingUtils.getInstance()
                .initButton(new JButton(), "WheellyToolBar.resetButton");
        this.clearMapButton = SwingUtils.getInstance()
                .initButton(new JButton(), "WheellyToolBar.clearMapButton");

        add(pauseButton);
        add(playButton);
        add(resetButton);
        add(clearMapButton);
        add(relocateButton);
    }

    /**
     * Returns the button used to pause the current operation.
     */
    public JButton pauseButton() {
        return pauseButton;
    }

    /**
     * Returns the button used to play the current operation.
     */
    public JButton playButton() {
        return playButton;
    }

    /**
     * Returns the button used to relocate the robot.
     */
    public JButton relocateButton() {
        return relocateButton;
    }

    /**
     * Returns the button used to reset the robot.
     */
    public JButton resetButton() {
        return resetButton;
    }

    /**
     * Returns the button used to clear the map
     */
    public JButton clearMapButton() {
        return clearMapButton;
    }
}