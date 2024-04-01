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

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 * Swing utilities functions
 */
public interface Utils {

    /**
     * Returns the desktop centered frame
     *
     * @param frame the frame
     */
    static JFrame center(JFrame frame) {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension frameSize = frame.getSize();
        frame.setLocation(
                (screen.width - frameSize.width) / 2,
                (screen.height - frameSize.height) / 2);
        return frame;
    }

    /**
     * Returns a non-resizable frame
     *
     * @param title   the title
     * @param content the content
     */
    static JFrame createFixFrame(String title, Component content) {
        return createFrame(title, content, false);
    }

    /**
     * Create a frame
     *
     * @param title     the title
     * @param content   the content of frame
     * @param resizable true if resizable
     */
    static JFrame createFrame(String title, Component content, boolean resizable) {
        JFrame frame = new JFrame(title);
        frame.setResizable(resizable);
        Container content1 = frame.getContentPane();
        content1.setLayout(new BorderLayout());
        content1.add(content, BorderLayout.CENTER);
        frame.pack();
        return frame;
    }

    /**
     * Returns a resizable frame with default size
     *
     * @param title   the title
     * @param content the content
     */
    static JFrame createFrame(String title, Component content) {
        JFrame frame = new JFrame(title);
        frame.setResizable(true);
        Container content1 = frame.getContentPane();
        content1.setLayout(new BorderLayout());
        content1.add(content, BorderLayout.CENTER);
        frame.pack();
        return frame;
    }


    /**
     * Lays horizontally the frames
     *
     * @param frames the list of frames
     */
    static void layHorizontally(Collection<JFrame> frames) {
        layHorizontally(frames.toArray(JFrame[]::new));
    }

    /**
     * Lays horizontally the frames
     *
     * @param frames the list of frames
     */
    static void layHorizontally(JFrame... frames) {
        requireNonNull(frames);
        if (frames.length > 0) {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gd = ge.getDefaultScreenDevice();
            GraphicsConfiguration gc = gd.getDefaultConfiguration();
            Rectangle screenBound = gc.getBounds();
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            int xScreen = screenBound.x + insets.left;
            int yScreen = screenBound.y + insets.top;
            int wScreen = screenBound.width - insets.left - insets.right;
            int hScreen = screenBound.height - insets.top - insets.bottom;
            int remainderWidth = wScreen;
            int x = xScreen;
            for (JFrame frame : frames) {
                int width = frame.getSize().width;
                int height = frame.getSize().height;
                if (width > remainderWidth) {
                    // No more horizontal space on screen
                    if (width > wScreen) {
                        // frame too wide
                        frame.setLocation(xScreen, yScreen);
                        // Resize because of frame too high
                        frame.setSize(wScreen, min(hScreen, height));
                    } else {
                        // Align left
                        frame.setLocation(xScreen + wScreen - width, yScreen);
                        if (height > hScreen) {
                            // Resize because of frame too high
                            frame.setSize(width, hScreen);
                        }
                    }
                } else {
                    // horizontal space available for frame
                    frame.setLocation(x, yScreen);
                    x = x + frame.getWidth();
                    remainderWidth -= width;
                    if (height > hScreen) {
                        // Resize because of frame too high
                        frame.setSize(width, hScreen);
                    }
                }
            }
        }
    }
}