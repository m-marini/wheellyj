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

/**
 *
 */
package org.mmarini.swing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.function.BiFunction;

/**
 * @author us00852
 */
public class GridLayoutHelper<T extends Container> {

    private static final Logger logger = LoggerFactory
            .getLogger(GridLayoutHelper.class);
    private static final Map<String, Modifier> MODIFIERS = new HashMap<String, Modifier>();

    /**
     * @param c
     * @return
     */
    private static GridBagConstraints create(final GridBagConstraints c) {
        return new GridBagConstraints(c.gridx, c.gridy, c.gridwidth,
                c.gridheight, c.weightx, c.weighty, c.anchor, c.fill, c.insets,
                c.ipadx, c.ipady);
    }

    public static GridBagConstraints modify(final GridBagConstraints c,
                                            final String modifiers) {
        GridBagConstraints n = c;
        for (final String s : modifiers.split("\\s+")) {
            final String[] a = s.split(",");
            final Modifier m = MODIFIERS.get(a[0]);
            if (m != null)
                n = m.apply(n, a);
        }
        return n;
    }
    private final ResourceBundle bundle;
    private final T container;
    private final GridBagLayout layout;
    private GridBagConstraints constraints;

    {
        MODIFIERS.put("at", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                if (args.length >= 3)
                    try {
                        final GridBagConstraints n = create(c);
                        n.gridx = Integer.parseInt(args[1]);
                        n.gridy = Integer.parseInt(args[2]);
                        return n;
                    } catch (final NumberFormatException e) {
                        logger.error(e.getMessage(), e);
                    }
                return c;
            }
        });
        MODIFIERS.put("ipad", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                if (args.length >= 3)
                    try {
                        final GridBagConstraints n = create(c);
                        n.ipadx = Integer.parseInt(args[1]);
                        n.ipady = Integer.parseInt(args[2]);
                        return n;
                    } catch (final NumberFormatException e) {
                        logger.error(e.getMessage(), e);
                    }
                return c;
            }
        });
        MODIFIERS.put("span", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                if (args.length >= 3)
                    try {
                        final GridBagConstraints n = create(c);
                        n.gridwidth = Integer.parseInt(args[1]);
                        n.gridheight = Integer.parseInt(args[2]);
                        return n;
                    } catch (final NumberFormatException e) {
                        logger.error(e.getMessage(), e);
                    }
                return c;
            }
        });
        MODIFIERS.put("insets", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                if (args.length <= 1)
                    return c;
                try {
                    final int t = Integer.parseInt(args[1]);
                    int l = t;
                    int b = t;
                    int r = t;
                    if (args.length >= 3)
                        l = r = Integer.parseInt(args[2]);
                    if (args.length >= 5) {
                        b = Integer.parseInt(args[3]);
                        r = Integer.parseInt(args[4]);
                    }
                    final GridBagConstraints n = create(c);
                    n.insets = new Insets(t, l, b, r);
                    return n;
                } catch (final NumberFormatException e) {
                    logger.error(e.getMessage(), e);
                    return c;
                }
            }
        });
        MODIFIERS.put("weight", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                if (args.length >= 3)
                    try {
                        final GridBagConstraints n = create(c);
                        n.weightx = Double.parseDouble(args[1]);
                        n.weighty = Double.parseDouble(args[2]);
                        return n;
                    } catch (final NumberFormatException e) {
                        logger.error(e.getMessage(), e);
                    }
                return c;
            }
        });
        MODIFIERS.put("def", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                return new GridBagConstraints();
            }
        });
        MODIFIERS.put("e", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                final GridBagConstraints n = create(c);
                n.anchor = GridBagConstraints.EAST;
                return n;
            }
        });
        MODIFIERS.put("ne", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                final GridBagConstraints n = create(c);
                n.anchor = GridBagConstraints.NORTHEAST;
                return n;
            }
        });
        MODIFIERS.put("nw", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                final GridBagConstraints n = create(c);
                n.anchor = GridBagConstraints.NORTHWEST;
                return n;
            }
        });
        MODIFIERS.put("w", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                final GridBagConstraints n = create(c);
                n.anchor = GridBagConstraints.WEST;
                return n;
            }
        });
        MODIFIERS.put("n", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                final GridBagConstraints n = create(c);
                n.anchor = GridBagConstraints.NORTH;
                return n;
            }
        });
        MODIFIERS.put("s", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                final GridBagConstraints n = create(c);
                n.anchor = GridBagConstraints.SOUTH;
                return n;
            }
        });
        MODIFIERS.put("se", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                final GridBagConstraints n = create(c);
                n.anchor = GridBagConstraints.SOUTHEAST;
                return n;
            }
        });
        MODIFIERS.put("sw", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                final GridBagConstraints n = create(c);
                n.anchor = GridBagConstraints.SOUTHWEST;
                return n;
            }
        });
        MODIFIERS.put("below", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                final GridBagConstraints n = create(c);
                n.gridy = GridBagConstraints.RELATIVE;
                return n;
            }
        });
        MODIFIERS.put("right", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                final GridBagConstraints n = create(c);
                n.gridx = GridBagConstraints.RELATIVE;
                return n;
            }
        });
        MODIFIERS.put("center", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                final GridBagConstraints n = create(c);
                n.anchor = GridBagConstraints.CENTER;
                return n;
            }
        });
        MODIFIERS.put("hspan", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                final GridBagConstraints n = create(c);
                n.gridwidth = GridBagConstraints.REMAINDER;
                return n;
            }
        });
        MODIFIERS.put("vspan", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                final GridBagConstraints n = create(c);
                n.gridheight = GridBagConstraints.REMAINDER;
                return n;
            }
        });
        MODIFIERS.put("rspan", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                final GridBagConstraints n = create(c);
                n.gridwidth = GridBagConstraints.RELATIVE;
                return n;
            }
        });
        MODIFIERS.put("bspan", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                final GridBagConstraints n = create(c);
                n.gridheight = GridBagConstraints.RELATIVE;
                return n;
            }
        });
        MODIFIERS.put("nospan", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                final GridBagConstraints n = create(c);
                n.gridwidth = 1;
                n.gridheight = 1;
                return n;
            }
        });
        MODIFIERS.put("nofill", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                final GridBagConstraints n = create(c);
                n.fill = GridBagConstraints.NONE;
                return n;
            }
        });
        MODIFIERS.put("fill", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                final GridBagConstraints n = create(c);
                n.fill = GridBagConstraints.BOTH;
                return n;
            }
        });
        MODIFIERS.put("hfill", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                final GridBagConstraints n = create(c);
                n.fill = GridBagConstraints.HORIZONTAL;
                return n;
            }
        });
        MODIFIERS.put("vfill", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                final GridBagConstraints n = create(c);
                n.fill = GridBagConstraints.VERTICAL;
                return n;
            }
        });
        MODIFIERS.put("noinsets", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                final GridBagConstraints n = create(c);
                n.insets = new Insets(0, 0, 0, 0);
                return n;
            }
        });
        MODIFIERS.put("noweight", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {
                final GridBagConstraints n = create(c);
                n.weightx = 0.0;
                n.weighty = 0.0;
                return n;
            }
        });
        MODIFIERS.put("hw", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {

                if (args.length >= 2)
                    try {
                        final GridBagConstraints n = create(c);
                        n.weightx = Double.parseDouble(args[1]);
                        return n;
                    } catch (final NumberFormatException e) {
                        logger.error(e.getMessage(), e);
                        return c;
                    }
                else {
                    final GridBagConstraints n = create(c);
                    n.weightx = 1.0;
                    return n;
                }
            }
        });
        MODIFIERS.put("vw", new Modifier() {

            @Override
            public GridBagConstraints apply(final GridBagConstraints c,
                                            final String[] args) {

                if (args.length >= 2)
                    try {
                        final GridBagConstraints n = create(c);
                        n.weighty = Double.parseDouble(args[1]);
                        return n;
                    } catch (final NumberFormatException e) {
                        logger.error(e.getMessage(), e);
                        return c;
                    }
                else {
                    final GridBagConstraints n = create(c);
                    n.weighty = 1.0;
                    return n;
                }
            }
        });
    }

    /**
     * @param bundle
     * @param container
     */
    public GridLayoutHelper(final ResourceBundle bundle, final T container) {
        this.bundle = bundle;
        this.container = container;
        layout = new GridBagLayout();
        constraints = new GridBagConstraints();
        container.setLayout(layout);
    }

    /**
     * @param container
     */
    public GridLayoutHelper(final T container) {
        this(null, container);
    }

    /**
     * @param args
     */
    public GridLayoutHelper<T> add(final Object... args) {
        GridBagConstraints gbc = constraints;
        for (final Object o : args)
            if (o instanceof Component) {
                final Component c = (Component) o;
                layout.setConstraints(c, gbc);
                container.add(c);
                gbc = constraints;
            } else if (o instanceof Action) {
                final JButton c = new JButton((Action) o);
                layout.setConstraints(c, gbc);
                container.add(c);
                gbc = constraints;
            } else {
                final String s = String.valueOf(o);
                if (s.startsWith("+"))
                    gbc = modify(gbc, s.substring(1));
                else {
                    final JLabel c = new JLabel(getString(s));
                    layout.setConstraints(c, gbc);
                    container.add(c);
                    gbc = constraints;
                }
            }
        return this;
    }

    /**
     * @return the container
     */
    public T getContainer() {
        return container;
    }

    /**
     * @param key
     * @return
     */
    private String getString(final String key) {
        if (bundle != null)
            try {
                return bundle.getString(key);
            } catch (final MissingResourceException e) {
                return '!' + key + '!';
            }
        else
            return key;
    }

    /**
     * @param mods
     * @return
     */
    public GridLayoutHelper<T> modify(final String mods) {
        constraints = modify(constraints, mods);
        return this;
    }

    interface Modifier extends
            BiFunction<GridBagConstraints, String[], GridBagConstraints> {
    }
}