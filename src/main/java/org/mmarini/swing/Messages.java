/*
 * Copyright (c) 2019-2025 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
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
package org.mmarini.swing;

import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * @author marco.marini@mmarini.org
 */
public class Messages {

    private static final String BUNDLE_NAME = "resources"; //$NON-NLS-1$
    private static final MessagesBundle messages = new MessagesBundle(ResourceBundle.getBundle(BUNDLE_NAME));

    public static String format(String patternKey, Object... args) {
        return messages.format(patternKey, args);
    }

    public static Optional<String> formatOpt(String patternKey, Object... args) {
        return messages.formatOpt(patternKey, args);
    }

    public static String getString(final String key) {
        return messages.getString(key);
    }

    public static Optional<String> getStringOpt(final String key) {
        return messages.getStringOpt(key);
    }

    public static MessagesBundle messages() {
        return messages;
    }

    public record MessagesBundle(ResourceBundle resourceBundle) {

        public String format(String patternKey, Object... args) {
            return formatOpt(patternKey, args).orElseGet(() -> '!' + patternKey + '!');
        }

        public Optional<String> formatOpt(String patternKey, Object... args) {
            return getStringOpt(patternKey).map(pattern ->
                    format(pattern, args));
        }

        public String getString(final String key) {
            return getStringOpt(key).orElseGet(() -> '!' + key + '!');
        }

        public Optional<String> getStringOpt(final String key) {
            try {
                return Optional.of(resourceBundle.getString(key));
            } catch (final MissingResourceException e) {
                return Optional.empty();
            }
        }
    }
}