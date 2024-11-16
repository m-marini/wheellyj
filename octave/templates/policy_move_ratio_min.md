The strategy to determine the movement of the robot is provided by the agent by generating the probabilities of selecting a given movement.

The ratio between the maximum probability and the minimum probability is an indicator of how much the output layer is saturated.

This indicator tells us how many times the most probable action is more likely than the least probable action.

**The minimum value of the ratio is 1** and occurs when all movement are equally probable.

ANN output layers are generally of the tanh -> softmax type, so the maximum value of the tanh layer is +1 and the minimum value -1.

**The maximum value of the ratio is 989**

It is determined by the temperature $T=0.29$ of softmax layer

$e^\frac{2}{T}$

 If we consider the tanh layer saturated when the output of layer is greater than of 0.96 or less than -0.96 _abs(input of tanh) >= 2_ then **the ratio of saturated layer are greater than 750**
 
 $e^{0.96\frac{2}{T}}$
