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

package org.mmarini.wheelly.model;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.StringJoiner;

/**
 * The move message
 */
@XmlRootElement
public class MoveToBody {
    private int left;
    private int right;
    private long validTo;

    /**
     * Creates an empty move message
     */
    public MoveToBody() {
    }

    /**
     * Creates the move message
     *
     * @param left    the left motor speed
     * @param right   the left motor speed
     * @param validTo the expiration instant (robot timestamp)
     */
    public MoveToBody(int left, int right, long validTo) {
        this.left = left;
        this.right = right;
        this.validTo = validTo;
    }

    /**
     * Returns the left motor speed
     */
    public int getLeft() {
        return left;
    }

    /**
     * Sets the left motors speed
     *
     * @param left the left motor speed
     */
    public void setLeft(int left) {
        this.left = left;
    }

    /**
     * Returns the right motor speed
     */
    public int getRight() {
        return right;
    }

    /**
     * Sets the right motors speed
     *
     * @param right the right motor speed
     */
    public void setRight(int right) {
        this.right = right;
    }

    /**
     * Returns the validity in remote clock ticks
     */
    public long getValidTo() {
        return validTo;
    }

    /**
     * Sets the validity in remote clock ticks
     *
     * @param validTo the expiration instant (robot timestamp)
     */
    public void setValidTo(long validTo) {
        this.validTo = validTo;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", MoveToBody.class.getSimpleName() + "[", "]")
                .add("left=" + left)
                .add("right=" + right)
                .add("validTo=" + validTo)
                .toString();
    }
}
