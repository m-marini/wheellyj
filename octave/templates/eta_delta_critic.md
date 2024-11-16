Stochastic gradient-descent (SGD)
methods try to minimize ther error of approximate differential value function (critic) by adjusting the weights after each example by a small amount in
the direction that would most reduce the error on that example.

The amounts of adjiusting weights are proportional to the TD error $\delta$ by the learning parameter $\eta$.

The critic error RMS is the root mean square of the adjusting weights and indicates the amount of changes from the critic output layer.

Values tending towards zero means that the ANN undergoes minimal variations in weights.