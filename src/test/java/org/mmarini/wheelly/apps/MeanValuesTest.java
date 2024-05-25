package org.mmarini.wheelly.apps;

import org.junit.jupiter.api.Test;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;

class MeanValuesTest {
    @Test
    void add() {
        MeanValue means = MeanValue.zeros();
        INDArray x = Nd4j.arange(1, 4).reshape(3, 1);
        double discount = MeanValue.DEFAULT_DISCOUNT;
        double a2 = (1 - discount);
        double a1 = a2 * discount;
        double a0 = a1 * discount;
        double expected = 1 * a0 + 2 * a1 + 3 * a2;
        means.add(x);
        assertThat(means.value(), closeTo(expected, 1e-3));
    }

    @Test
    void add1() {
        MeanValue means = MeanValue.zeros();
        means.add(1);
        double discount = MeanValue.DEFAULT_DISCOUNT;
        double exp = 1 - discount;
        assertThat(means.value(), closeTo(exp, 1e-3));
    }
}