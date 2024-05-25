package org.mmarini.wheelly.apps;

import org.junit.jupiter.api.Test;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;

class RMSValuesTest {

    @Test
    void add() {
        RMSValue means = RMSValue.zeros();
        INDArray x = Nd4j.arange(-1, 2).reshape(3, 1).castTo(DataType.FLOAT).muli(2);
        double discount = MeanValue.DEFAULT_DISCOUNT;

        double a2 = (1 - discount);
        double a1 = a2 * discount;
        double a0 = a1 * discount;
        double expected = Math.sqrt(4 * a0 + 4 * a2);

        means.add(x);
        assertThat(means.value(), closeTo(expected, 1e-3));
    }

    @Test
    void add1() {
        RMSValue means = RMSValue.zeros();
        means.add(-1);
        double discount = MeanValue.DEFAULT_DISCOUNT;
        double exp = Math.sqrt(1 - discount);
        assertThat(means.value(), closeTo(exp, 1e-3));
    }
}