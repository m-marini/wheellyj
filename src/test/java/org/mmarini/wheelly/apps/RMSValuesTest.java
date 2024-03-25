package org.mmarini.wheelly.apps;

import org.junit.jupiter.api.Test;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import static java.lang.Math.sqrt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class RMSValuesTest {

    @Test
    void add() {
        RMSValues means = RMSValues.zeros(2);
        INDArray x = Nd4j.createFromArray(-1, 1, 2, -2).reshape(2, 2).castTo(DataType.FLOAT);
        float discount = MeanValues.DEFAULT_DISCOUNT;
        float avg0 = (1 - discount) * discount;
        float avg1 = (1 - discount);
        INDArray expected = Nd4j.createFromArray(
                (float) sqrt(1 * avg0 + 4 * avg1), (float) sqrt(1 * avg0 + 4 * avg1)
        );

        means.add(x);
        assertThat(means.values(), matrixCloseTo(expected, 1e-3));
    }

    @Test
    void add1() {
        RMSValues means = RMSValues.zeros(2);
        means.add(-1);
        float discount = MeanValues.DEFAULT_DISCOUNT;
        float avg1 = (1 - discount);
        assertThat(means.values(), matrixCloseTo(new float[]{
                (float) sqrt(avg1), (float) sqrt(avg1)
        }, 1e-3));
    }
}