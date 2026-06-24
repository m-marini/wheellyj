The strategy to determine the direction of the proxy sensor is provided by the agent by generating the probabilities of selecting a given direction.

The ratio between the maximum probability and the minimum probability is an indicator of how much the output layer is saturated.

This indicator tells us how many times the most probable action is more likely than the least probable action.

The minimum value of the ratio is 1 and occurs when all directions are equally probable.

ANN output layers are generally of the tanh -> softmax type, so the maximum value of the tanh layer is +1 and the minimum value -1.

The maximum value of the ratio is 100 determined by the temperature $T$ of softmax layer

$e^\frac{2}{T}$

 If we consider the tanh layer saturated when the output of layer is greater than of 0.96 or less than -0.96 _abs(input of tanh) >= 2_ then the ratio of saturated layer are greater than
 
 $e^{0.96\frac{2}{T}}$

