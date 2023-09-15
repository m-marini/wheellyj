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
import org.mmarini.yaml.schema.Validator;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.yaml.schema.Validator.*;

/**
 * The state flow defines all process states and state transitions
 */
public class StateFlow {
    /**
     * Returns the state flow from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of state flow in the document
     */
    public static StateFlow create(JsonNode root, Locator locator) {
        Locator onInitLoc = locator.path("onInit");
        ProcessorCommand onInit = onInitLoc.getNode(root).isMissingNode()
                ? null
                : ProcessorCommand.create(root, onInitLoc);

        List<StateNode> states = StateNode.createNodes(root, locator.path("states"));
        List<StateTransition> transitions = StateTransition.createList(root, locator.path("transitions"));
        String entry = locator.path("entry").getNode(root).asText();
        validateTransitions(transitions, states, entry);
        StateNode entryNode = findState(states, entry).orElseThrow();
        return new StateFlow(states, transitions, entryNode, onInit);
    }

    /**
     * Returns the state by identifier from a state list
     *
     * @param states the state list
     * @param id     the identifier
     */
    private static Optional<StateNode> findState(List<StateNode> states, String id) {
        return states.stream().filter(n -> n.getId().equals(id)).findAny();
    }

    /**
     * Validates the transitions and the entry against a state list
     *
     * @param transitions the list of transitions
     * @param states      the list of states
     * @param entry       the entry point
     */
    private static void validateTransitions(List<StateTransition> transitions, List<StateNode> states, String entry) {
        if (findState(states, entry).isEmpty()) {
            throw new IllegalArgumentException(format("entry state %s not found", entry));
        }
        for (int i = 0; i < transitions.size(); i++) {
            StateTransition tr = transitions.get(i);
            if (findState(states, tr.getFrom()).isEmpty()) {
                throw new IllegalArgumentException(format("transition %d  (%s -- %s --> %s) from state not found",
                        i, tr.getFrom(), tr.getTrigger(), tr.getTo()));
            }
            if (findState(states, tr.getTo()).isEmpty()) {
                throw new IllegalArgumentException(format("transition %d  (%s -- %s --> %s) to state not found",
                        i, tr.getFrom(), tr.getTrigger(), tr.getTo()));
            }
        }

    }

    private final List<StateNode> states;
    private final List<StateTransition> transitions;
    private final StateNode entry;
    private final ProcessorCommand onInit;

    /**
     * Creates the state flow
     *
     * @param states      the list of states
     * @param transitions the list of transitions
     * @param entry       the entry state
     * @param onInit      the processor command on initialization
     */
    public StateFlow(List<StateNode> states, List<StateTransition> transitions, StateNode entry, ProcessorCommand onInit) {
        this.states = requireNonNull(states);
        this.transitions = requireNonNull(transitions);
        this.entry = requireNonNull(entry);
        this.onInit = onInit;
    }

    /**
     * Returns the entry state
     */
    public StateNode getEntry() {
        return entry;
    }

    /**
     * Returns the initialization processor command
     */
    public ProcessorCommand getOnInit() {
        return onInit;
    }

    /**
     * Returns the state by identifier or null if not exits
     *
     * @param id the identifier
     */
    public StateNode getState(String id) {
        return findState(states, id).orElse(null);
    }

    /**
     * Returns the list of states
     */
    public List<StateNode> getStates() {
        return states;
    }

    /**
     * Returns the lilst of transitions
     */
    public List<StateTransition> getTransitions() {
        return transitions;
    }
}
