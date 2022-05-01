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

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import net.java.games.input.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 *
 */
public class RxJoystickImpl implements RxJoystick {
    private static final Logger logger = LoggerFactory.getLogger(RxJoystickImpl.class);

    /**
     * @param joystickPort the controller name
     */
    public static RxJoystickImpl create(String joystickPort) {
        return new RxJoystickImpl(findController(joystickPort));
    }

    /**
     * @param controller the controller
     */
    private static Flowable<Event> createUnsafe(Controller controller) {
        return Flowable.generate(
                () -> (net.java.games.input.EventQueue) null,
                (queue, emitter) -> {
                    Event event = new Event();
                    if (queue == null) {
                        controller.poll();
                        net.java.games.input.EventQueue queue1 = controller.getEventQueue();
                        if (queue1.getNextEvent(event)) {
                            emitter.onNext(event);
                            return queue1;
                        } else {
                            return null;
                        }
                    } else if (queue.getNextEvent(event)) {
                        emitter.onNext(event);
                        return queue;
                    } else {
                        controller.poll();
                        net.java.games.input.EventQueue queue1 = controller.getEventQueue();
                        if (queue.getNextEvent(event)) {
                            emitter.onNext(event);
                            return queue1;
                        } else {
                            return null;
                        }
                    }
                });
    }

    /**
     * @param name the controller name
     */
    private static Controller findController(String name) {
        Controller[] controllers = ControllerEnvironment.getDefaultEnvironment().getControllers();
        return Arrays.stream(controllers).filter(x -> x.getName().equals(name))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(format("Joystick controller \"%s\" not found ", name)));
    }

    private final PublishProcessor<Event> publisher;

    /**
     * @param controller the controller
     */
    protected RxJoystickImpl(Controller controller) {
        logger.debug("Create reactive joystick");
        requireNonNull(controller);
        this.publisher = PublishProcessor.create();
        createUnsafe(controller).subscribeOn(Schedulers.single())
                .subscribe(this.publisher);
        logger.debug("Created reactive joystick");
    }

    @Override
    public void close() {
        publisher.onComplete();
    }

    @Override
    public Flowable<Event> readEvents() {
        return publisher;
    }
}
