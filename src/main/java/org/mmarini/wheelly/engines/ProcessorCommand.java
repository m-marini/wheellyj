/*
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.engines;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.yaml.Locator;

import java.util.*;
import java.util.function.Consumer;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Performs processor context processing
 * <p>
 * It is an immutable object
 * </p>
 */
public record ProcessorCommand(String id, Consumer<ProcessorContext> command) {
    private static final List<ProcessorCommand> COMMANDS = List.of(
            new ProcessorCommand("put", ProcessorCommand::putCommand),
            new ProcessorCommand("get", ProcessorCommand::getCommand),
            new ProcessorCommand("add", ProcessorCommand::addCommand),
            new ProcessorCommand("sub", ProcessorCommand::subCommand),
            new ProcessorCommand("neg", ProcessorCommand::negCommand),
            new ProcessorCommand("mul", ProcessorCommand::mulCommand),
            new ProcessorCommand("div", ProcessorCommand::divCommand),
            new ProcessorCommand("swap", ProcessorCommand::swapCommand),
            new ProcessorCommand("localTime", ProcessorCommand::timeCommand)
    );

    /**
     * Adds two operands (op1 + op2)
     *
     * @param context the processor context
     */
    private static void addCommand(ProcessorContext context) {
        double b = context.popDouble();
        double a = context.popDouble();
        context.push(a + b);
    }

    /**
     * Returns the command that performs a sequence of commands
     *
     * @param commands the commands
     */
    public static ProcessorCommand concat(ProcessorCommand... commands) {
        ProcessorCommand[] compress = Arrays.stream(commands).filter(Objects::nonNull)
                .toArray(ProcessorCommand[]::new);
        return new ProcessorCommand("@program",
                ctx -> {
                    for (ProcessorCommand cmd : compress) {
                        cmd.execute(ctx);
                    }
                    int n = ctx.stackSize();
                    if (n != 0) {
                        throw new IllegalArgumentException(format("Stack pollution (%d unused elements)", n));
                    }
                });
    }

    /**
     * Returns the command processor from yaml
     *
     * @param root    the yaml
     * @param locator the command processor locator
     */
    public static ProcessorCommand create(JsonNode root, Locator locator) {
        if ((locator).getNode(root).isMissingNode()) {
            return null;
        }
        String[] lines = locator.elements(root)
                .map(l -> l.getNode(root).asText())
                .toArray(String[]::new);
        return parse(lines);
    }

    /**
     * Divide two operands (op1 / op2)
     *
     * @param context the processor context
     */
    private static void divCommand(ProcessorContext context) {
        double b = context.popDouble();
        double a = context.popDouble();
        context.push(a / b);
    }

    /**
     * Pushes a values from value map (key get)
     *
     * @param context the processor context
     */
    private static void getCommand(ProcessorContext context) {
        String key = context.popString();
        Object value = context.get(key);
        if (value == null) {
            throw new IllegalArgumentException(format("Missing value for \"%s\"", key));
        }
        context.push(value);
    }

    /**
     * Multiplies two operands (op1 * op2)
     *
     * @param context the processor context
     */
    private static void mulCommand(ProcessorContext context) {
        double b = context.popDouble();
        double a = context.popDouble();
        context.push(a * b);
    }

    /**
     * Negates an operands (-op1)
     *
     * @param context the processor context
     */
    private static void negCommand(ProcessorContext context) {
        double a = context.popDouble();
        context.push(-a);
    }

    /**
     * Returns the command by lines
     *
     * @param lines the lines
     */
    public static ProcessorCommand parse(String... lines) {
        requireNonNull(lines);
        ProcessorCommand[] commands = Arrays.stream(lines)
                .map(ProcessorCommand::parseCommand)
                .toArray(ProcessorCommand[]::new);
        return concat(commands);
    }

    /**
     * Returns a command by parsing the string that may be a value, a string or, an operator
     *
     * @param command the command string
     */
    public static ProcessorCommand parseCommand(String command) {
        try {
            double value = Double.parseDouble(command);
            return new ProcessorCommand(command, ctx -> ctx.push(value));
        } catch (NumberFormatException ex) {
            Optional<ProcessorCommand> cmd = COMMANDS.stream()
                    .filter(c -> c.id().equals(command))
                    .findAny();
            return cmd.orElseGet(() -> new ProcessorCommand(command, ctx -> ctx.push(command)));
        }
    }

    /**
     * Returns the command that set a value in the value map
     *
     * @param key   the key
     * @param value the value
     */
    public static ProcessorCommand put(String key, Object value) {
        return new ProcessorCommand(key + " " + value + " put ",
                ctx -> ctx.put(key, value)
        );
    }

    /**
     * Put a values in the value map (key value put)
     *
     * @param context the processor context
     */
    private static void putCommand(ProcessorContext context) {
        Object value = context.pop();
        String key = context.popString();
        context.put(key, value);
    }

    /**
     * Returns the command that puts a set of key, value entries
     *
     * @param props the entries
     */
    public static ProcessorCommand setProperties(Map<String, Object> props) {
        return new ProcessorCommand("setProperties", ctx -> {
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                ctx.put(entry.getKey(), entry.getValue());
            }
        });
    }

    /**
     * Subtracts two operands (op1 - op2)
     *
     * @param context the processor context
     */
    private static void subCommand(ProcessorContext context) {
        double b = context.popDouble();
        double a = context.popDouble();
        context.push(a - b);
    }

    /**
     * Swap two operands (op1, op2) -> (op2, op1)
     *
     * @param context the processor context
     */
    private static void swapCommand(ProcessorContext context) {
        Object b = context.pop();
        Object a = context.pop();
        context.push(b);
        context.push(a);
    }

    /**
     * Pushes the robot localTime in the stack
     *
     * @param context the processor context
     */
    private static void timeCommand(ProcessorContext context) {
        RobotStatus status = context.robotStatus();
        context.push(status.simulationTime());
    }

    /**
     * Creates the processor command
     *
     * @param id      the id of command
     * @param command the command
     */
    public ProcessorCommand(String id, Consumer<ProcessorContext> command) {
        this.id = requireNonNull(id);
        this.command = requireNonNull(command);
    }

    /**
     * Executes the command and returns the new context
     *
     * @param ctx the context
     */
    public void execute(ProcessorContext ctx) {
        command.accept(ctx);
    }
}
