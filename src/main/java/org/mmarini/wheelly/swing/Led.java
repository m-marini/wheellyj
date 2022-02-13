/*
 *
 * Copyright (c) )2022 Marco Marini, marco.marini@mmarini.org
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
import java.net.URL;

import static java.awt.Color.BLACK;
import static java.util.Objects.requireNonNull;

/**
 *
 */
public class Led extends JComponent {
    public static Led create(String... images) {
        ImageIcon[] icons = new ImageIcon[images.length];
        for (int i = 0; i < images.length; i++) {
            if (images[i] != null) {
                URL url = Led.class.getResource(images[i]);
                if (url != null) {
                    icons[i] = new ImageIcon(url);
                }
            }
        }
        return new Led(icons);
    }

    private final ImageIcon[] icons;
    private int value;

    /**
     * @param icons the icons
     */
    public Led(ImageIcon... icons) {
        this.icons = requireNonNull(icons);
        setBackground(BLACK);
        setBorder(BorderFactory.createEmptyBorder());
        Dimension size = new Dimension(30, 30);
        setSize(size);
        setPreferredSize(size);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Dimension size = getSize();
        g.setColor(getBackground());
        g.fillRect(0, 0, size.width, size.height);
        if (value >= 0 && value < icons.length && icons[value] != null) {
            g.drawImage(icons[value].getImage(), 0, 0, size.width, size.height, this);
        }
    }

    /**
     * @param i the value
     */
    public Led setValue(int i) {
        this.value = i;
        repaint();
        return this;
    }


}
