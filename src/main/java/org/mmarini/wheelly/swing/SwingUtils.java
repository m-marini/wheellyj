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

import org.mmarini.swing.Messages;

import javax.swing.*;
import java.net.URL;

/**
 * Various functionalities used in the user interface.
 *
 * @author marco.marini@mmarini.org
 */
public class SwingUtils {
    private static final SwingUtils instance = new SwingUtils();

    /**
     * Returns the singleton instance of the utilities
     *
     * @return the instance
     */
    public static SwingUtils getInstance() {
        return instance;
    }

    /**
     * Create the utilities
     */
    protected SwingUtils() {
    }

    /**
     * Initialize an action loading the values from message resources file.<br>
     * The loaded value are:
     * <ul>
     * <li>name</li>
     * <li>tooltip</li>
     * <li>accelerator</li>
     * <li>mnemonic</li>
     * <li>smallIcon</li>
     * <li>largeIcon</li>
     * </ul>
     *
     * @param action the action to be initialized
     * @param key    the key identifier in the message file
     */
    public void initAction(final Action action, final String key) {
        String msg = Messages.getString(key + ".name"); //$NON-NLS-1$
        if (!msg.startsWith("!")) { //$NON-NLS-1$
            action.putValue(Action.NAME, msg);
        }
        msg = Messages.getString(key + ".tooltip"); //$NON-NLS-1$
        if (!msg.startsWith("!")) { //$NON-NLS-1$
            action.putValue(Action.SHORT_DESCRIPTION, msg);
        }
        msg = Messages.getString(key + ".accelerator"); //$NON-NLS-1$
        if (!msg.startsWith("!")) { //$NON-NLS-1$
            action.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(msg));
        }
        msg = Messages.getString(key + ".mnemonic"); //$NON-NLS-1$
        if (!msg.startsWith("!")) { //$NON-NLS-1$
            action.putValue(Action.MNEMONIC_KEY, (int) msg.charAt(0));
        }
        msg = Messages.getString(key + ".smallIcon"); //$NON-NLS-1$
        if (!msg.startsWith("!")) { //$NON-NLS-1$
            final URL url = getClass().getResource(msg);
            if (url != null) {
                final ImageIcon img = new ImageIcon(url);
                action.putValue(Action.SMALL_ICON, img);
            }
        }
        msg = Messages.getString(key + ".largeIcon"); //$NON-NLS-1$
        if (!msg.startsWith("!")) { //$NON-NLS-1$
            final URL url = getClass().getResource(msg);
            if (url != null) {
                final ImageIcon img = new ImageIcon(url);
                action.putValue(Action.LARGE_ICON_KEY, img);
            }
        }
    }

    /**
     * @param button the button
     * @param key    the key
     * @param <T>    the type of button
     */
    public <T extends AbstractButton> T initButton(final T button, final String key) {
        final String name = Messages.getString(key + ".name"); //$NON-NLS-1$
        if (!name.startsWith("!")) { //$NON-NLS-1$
            button.setText(name);
        }
        final String tooltip = Messages.getString(key + ".tooltip"); //$NON-NLS-1$
        if (!tooltip.startsWith("!")) { //$NON-NLS-1$
            button.setToolTipText(tooltip);
        }
        final String msg = Messages.getString(key + ".smallIcon"); //$NON-NLS-1$
        if (!msg.startsWith("!")) { //$NON-NLS-1$
            final URL url = getClass().getResource(msg);
            if (url != null) {
                final ImageIcon img = new ImageIcon(url);
                button.setIcon(img);
            }
        }
        return button;
    }

    /**
     * Initialize an action loading the values from message resources file.<br>
     * The loaded value are:
     * <ul>
     * <li>name</li>
     * <li>tooltip</li>
     * <li>accelerator</li>
     * <li>mnemonic</li>
     * <li>smallIcon</li>
     * <li>largeIcon</li>
     * </ul>
     *
     * @param item the action to be initialized
     * @param key  the key identifier in the message file
     */
    public <T extends JMenuItem> T initMenuItem(final T item, final String key) {
        final String name = Messages.getString(key + ".name"); //$NON-NLS-1$
        if (!name.startsWith("!")) { //$NON-NLS-1$
            item.setText(name);
        }
        final String tooltip = Messages.getString(key + ".tooltip"); //$NON-NLS-1$
        if (!tooltip.startsWith("!")) { //$NON-NLS-1$
            item.setToolTipText(tooltip);
        }
        final String mnemonic = Messages.getString(key + ".mnemonic"); //$NON-NLS-1$
        if (!mnemonic.startsWith("!")) { //$NON-NLS-1$
            item.setMnemonic(Integer.valueOf(mnemonic.charAt(0)));
        }
        final String accelerator = Messages.getString(key + ".accelerator"); //$NON-NLS-1$
        if (!accelerator.startsWith("!")) { //$NON-NLS-1$
            item.setAccelerator(KeyStroke.getKeyStroke(accelerator));
        }
        final String msg = Messages.getString(key + ".smallIcon"); //$NON-NLS-1$
        if (!msg.startsWith("!")) { //$NON-NLS-1$
            final URL url = getClass().getResource(msg);
            if (url != null) {
                final ImageIcon img = new ImageIcon(url);
                item.setIcon(img);
            }
        }
        return item;
    }
}