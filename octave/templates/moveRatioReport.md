## Move action ratio

The ratio between the maximum probability and the the geometric mean of the probabilities is an indicator of how much
the output layer is saturated.

This indicator tells us how many times the most probable action is more likely than the geometric average probable
action.

$R = \frac{\max(P(a))}{(\prod P(a))^\frac{1}{n}}$

A better understanding of the indicator is obtained by applying the logarithmic scale.

$\log_{10}(R) = \max(\log_{10}(P(a))) - \frac{\sum \log_{10}(P(a))}{n}$

The minimum value is obtained when the actions are all equally probable.

$P(a) = \frac{1}{n}, \forall a \in A$

$R \ge 1$

$\log_{10}(R) \ge 0$

### Move log10 ratio chart

| # samples | mean | min | max |
|-----------|------|-----|-----|
