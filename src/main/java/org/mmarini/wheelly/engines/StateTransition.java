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
import org.mmarini.yaml.schema.Locator;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Defines the transition between states, with the trigger condition and processing stage
 */
public class StateTransition {

    /**
     * Returns the state transition list from yaml
     *
     * @param root    the root document
     * @param locator the state transition locator
     */
    public static StateTransition create(JsonNode root, Locator locator) {
        Pattern trigger1 = Pattern.compile(locator.path("trigger").getNode(root).asText());
        String from1 = locator.path("from").getNode(root).asText();
        String to1 = locator.path("to").getNode(root).asText();
        Locator onTransitionLoc = locator.path("onTransition");
        ProcessorCommand onTransition = onTransitionLoc.getNode(root).isMissingNode()
                ? null
                : ProcessorCommand.create(root, onTransitionLoc);
        return new StateTransition(trigger1, from1, to1, onTransition);
    }

    /**
     * Returns the state transition from yaml
     *
     * @param root    the root document
     * @param locator the state transition locator
     */
    public static List<StateTransition> createList(JsonNode root, Locator locator) {
        return locator.elements(root)
                .map(l -> create(root, l))
                .collect(Collectors.toList());
    }

    private final Pattern trigger;
    private final String from;
    private final String to;
    private final ProcessorCommand onTransition;

    /**
     * Creates the state transition
     *
     * @param trigger      the trigger regex
     * @param from         the source state
     * @param to           the target state
     * @param onTransition the on transition commands
     */
    public StateTransition(Pattern trigger, String from, String to, ProcessorCommand onTransition) {
        this.trigger = requireNonNull(trigger);
        this.from = requireNonNull(from);
        this.to = requireNonNull(to);
        this.onTransition = onTransition;
    }

    /**
     * Process the acttivation of transition
     *
     * @param processorContext the context
     */
    public void activate(ProcessorContext processorContext) {
        if (onTransition != null) {
            onTransition.execute(processorContext);
        }
    }

    /**
     * Returns the start state identifier
     */
    public String getFrom() {
        return from;
    }

    /**
     * Returns the processor command on transition
     */
    public ProcessorCommand getOnTransition() {
        return onTransition;
    }

    /**
     * Returns the end state identifier
     */
    public String getTo() {
        return to;
    }

    /**
     * Returns the trigger regex
     */
    public Pattern getTrigger() {
        return trigger;
    }

    /**
     * Returns true if the transition key match the trigger
     *
     * @param key transition key
     */
    public boolean isTriggered(String key) {
        return trigger.asMatchPredicate().test(key);
    }
}
