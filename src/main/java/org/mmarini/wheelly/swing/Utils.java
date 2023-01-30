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

import static java.util.Objects.requireNonNull;

/**
 * Swing utilities functions
 */
public interface Utils {
    Dimension DEFAULT_SIZE = new Dimension(800, 800);

    /**
     * Returns a non-resizable frame
     *
     * @param title   the title
     * @param size    the size
     * @param content the content
     */
    static JFrame createFixFrame(String title, Dimension size, Component content) {
        return createFrame(title, size, content, false);
    }

    /**
     * Returns a non-resizable frame
     *
     * @param title   the title
     * @param content the content
     */
    static JFrame createFixFrame(String title, Component content) {
        return createFixFrame(title, DEFAULT_SIZE, content);
    }

    /**
     * Returns a resizable frame with default size
     *
     * @param title   the title
     * @param content the content
     */
    static JFrame createFrame(String title, Component content) {
        return createFrame(title, DEFAULT_SIZE, content, true);
    }

    /**
     * Create a frame
     *
     * @param title     the title
     * @param size      the size of frame
     * @param content   the content of frame
     * @param resizable true if resizable
     */
    static JFrame createFrame(String title, Dimension size, Component content, boolean resizable) {
        JFrame frame = new JFrame(title);
        frame.setSize(size);
        frame.setResizable(resizable);
        Container content1 = frame.getContentPane();
        content1.setLayout(new BorderLayout());
        content1.add(content, BorderLayout.CENTER);
        return frame;
    }

    /**
     * Create a resizable frame
     *
     * @param title   the title
     * @param size    the size of frame
     * @param content the content of frame
     */
    static JFrame createFrame(String title, Dimension size, Component content) {
        JFrame frame = new JFrame(title);
        frame.setSize(size);
        Container content1 = frame.getContentPane();
        content1.setLayout(new BorderLayout());
        content1.add(content, BorderLayout.CENTER);
        return frame;
    }

    static void layHorizontaly(JFrame... frames) {
        requireNonNull(frames);
        if (frames.length > 0) {
            int x0 = frames[0].getX();
            int y0 = frames[0].getY();
            int x = x0;
            for (JFrame frame : frames) {
                frame.setLocation(x, y0);
                x = x + frame.getWidth();
            }
        }
    }
}