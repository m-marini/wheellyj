package org.mmarini.wheelly.apps;

import org.junit.jupiter.api.Test;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class MeanValuesTest {

    @Test
    void add() {
        MeanValues means = MeanValues.zeros(2, 2);
        INDArray x = Nd4j.arange(0, 12).reshape(3, 2, 2);
        float discount = MeanValues.DEFAULT_DISCOUNT;
        float avg0 = (1 - discount) * discount * discount;
        float avg1 = (1 - discount) * discount;
        float avg2 = (1 - discount);
        INDArray expected = Nd4j.createFromArray(
                0 * avg0 + 4 * avg1 + 8 * avg2, 1 * avg0 + 5 * avg1 + 9 * avg2,
                2 * avg0 + 6 * avg1 + 10 * avg2, 3 * avg0 + 7 * avg1 + 11 * avg2
        ).reshape(2, 2);

        means.add(x);
        assertThat(means.values(), matrixCloseTo(expected, 1e-3));
    }

    @Test
    void add1() {
        MeanValues means = MeanValues.zeros(2, 2);
        means.add(1);
        float discount = MeanValues.DEFAULT_DISCOUNT;
        float avg1 = (1 - discount);
        assertThat(means.values(), matrixCloseTo(new float[][]{
                {1 * avg1, 1 * avg1},
                {1 * avg1, 1 * avg1}
        }, 1e-3));
    }
}