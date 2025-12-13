/*
 * Copyright (c) 2019 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Stream;

import static java.lang.String.format;

public class Utils {

    public static final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    /**
     * Returns the object created by invoking create method of class of "class" property
     *
     * @param root       the json document
     * @param locator    the locator
     * @param args       the arguments
     * @param argClasses the method argument class
     * @param <T>        the type of object
     */
    public static <T> T createObject(JsonNode root, Locator locator, Object[] args, Class<?>[] argClasses) {
        return createObject(root, locator, "create", args, argClasses);
    }

    /**
     * Returns the object created by invoking create method of class of "class" property
     *
     * @param file       the configuration file
     * @param args       the arguments
     * @param argClasses the method argument class
     * @param <T>        the type of object
     */
    public static <T> T createObject(File file, Object[] args, Class<?>[] argClasses) throws Throwable {
        return createObject(file, "create", args, argClasses);
    }

    /**
     * Returns the object created by invoking create method of class of "class" property
     *
     * @param file the configuration file
     * @param <T>  the type of object
     */
    public static <T> T createObject(File file) throws Throwable {
        return createObject(file, "create", new Object[0], new Class[0]);
    }

    /**
     * Returns the object created by invoking method of class of "class" property
     *
     * @param file       the configuration file
     * @param method     the method name
     * @param args       the arguments
     * @param argClasses the method argument class
     * @param <T>        the type of object
     */
    public static <T> T createObject(File file, String method, Object[] args, Class<?>[] argClasses) throws Throwable {
        JsonNode conf = Utils.fromFile(file);
        try {
            String className = conf.path("class").asText();
            Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(className);
            Class<?>[] argsType = Stream.concat(Stream.of(JsonNode.class, File.class),
                            Arrays.stream(argClasses))
                    .toArray(Class[]::new);
            Method creator = clazz.getDeclaredMethod(method, argsType);
            if (!Modifier.isStatic(creator.getModifiers())) {
                throw new IllegalArgumentException(format("Method %s.%s is not static", className, method));
            }
            Object[] builderArgs = Stream.concat(Stream.of(conf, file),
                            Arrays.stream(args))
                    .toArray(Object[]::new);
            return (T) creator.invoke(null, builderArgs);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /**
     * Returns the object created by invoking method of class of "class" property
     *
     * @param root       the json document
     * @param locator    the locator
     * @param method     the method name
     * @param args       the arguments
     * @param argClasses the method argument class
     * @param <T>        the type of object
     */
    public static <T> T createObject(JsonNode root, Locator locator, String method, Object[] args, Class<?>[] argClasses) {
        try {
            Locator classLocator = locator.path("class");
            String className = classLocator.getNode(root).asText();
            Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(className);
            Class<?>[] argsType = Stream.concat(Stream.of(JsonNode.class, Locator.class),
                            Arrays.stream(argClasses))
                    .toArray(Class[]::new);
            Method creator = clazz.getDeclaredMethod(method, argsType);
            if (!Modifier.isStatic(creator.getModifiers())) {
                throw new IllegalArgumentException(format("Method %s.%s is not static", className, method));
            }
            Object[] builderArgs = Stream.concat(Stream.of(root, locator),
                            Arrays.stream(args))
                    .toArray(Object[]::new);
            return (T) creator.invoke(null, builderArgs);
        } catch (ClassNotFoundException | NoSuchMethodException |
                 IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    /**
     * @param file the file
     * @throws IOException in case of error
     */
    public static JsonNode fromFile(File file) throws IOException {
        return objectMapper.readTree(new FileReader(file));
    }

    /**
     * @param file the filename
     * @throws IOException in case of error
     */
    public static JsonNode fromFile(String file) throws IOException {
        return objectMapper.readTree(new FileReader(file));
    }

    /**
     * @param resource the resource name
     * @throws IOException in case of error
     */
    public static JsonNode fromResource(String resource) throws IOException {
        InputStream res = Utils.class.getResourceAsStream(resource);
        if (res == null) {
            throw new FileNotFoundException(String.format("Resource \"%s\" not found", resource));
        }
        return objectMapper.readTree(new InputStreamReader(res));
    }

    /**
     * @param text the yaml text
     * @throws IOException in case of error
     */
    public static JsonNode fromText(String text) throws IOException {
        return objectMapper.readTree(new StringReader(text));
    }
}
