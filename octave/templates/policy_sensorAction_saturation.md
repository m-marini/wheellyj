The strategy to determine the movement of the robot is provided by the agent by generating the probabilities of selecting a given movement.

ANN output layers are generally of the tanh -> softmax type, the inputs to the Softmax layer are the preferences of a single action in the range from -1 to 1, the further the values ​​deviate from zero, the more saturated the levels are.

The indicator gives us the maximum deviation from the zero value relative to the limits.

