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
import net.java.games.input.Event;
import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public interface RxJoystick {
    String NONE_CONTROLLER = "None";
    String X = "x";
    String Y = "y";
    String Z = "z";
    String BUTTON_0 = "0";
    String BUTTON_1 = "1";
    String BUTTON_2 = "2";
    String BUTTON_3 = "3";
    String POV = "pov";
    String THUMB = "Thumb";
    String THUMB_2 = "Thumb 2";
    String TOP = "Top";
    String TRIGGER = "Trigger";

    /**
     *
     */
    void close();

    /**
     *
     */
    Flowable<Event> readEvents();

    /**
     *
     * @param id
     */
    default Flowable<Float> readValues(String id) {
        return readEvents().filter(ev -> id.equals(ev.getComponent().getIdentifier().getName()))
                .map(Event::getValue)
                .startWithItem(0f);
    }

    /**
     * @return
     */
    default Flowable<Tuple2<Float, Float>> readXY() {
        return Flowable.combineLatest(readValues(X), readValues(Y), Tuple2::of);
    }
}
