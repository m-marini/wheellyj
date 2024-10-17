The effective correction in sensor direction selection policy is measured by the ratio of the change in probability of the selected action after training to the original value of the probability.

R = (P'(ai) - P(ai)) / P(ai)

The root mean square value of this ratio indicates how much the agent has changed its behavior.

Values tending towards zero means that the agent is not changing the behavior.

**The absolute value of the ratio is limited by the proximal policy optimization method to a value around 20%**

ppoEpsilon=0.2
