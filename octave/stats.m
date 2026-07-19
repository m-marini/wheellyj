## usage: Y = stats(X)
##
## Compute basic statistical indicators from aggregated data X.
##
## X
##     Aggregated data series used to generate statistical indicators
##     for reporting and plotting functions such as linplot.
##     The input data must contain valid numeric values.
##
## Y
##     Row vector containing the computed statistical indicators.
##     The elements of Y are, respectively: number of samples,
##     mean value, minimum value, and maximum value.
##
## Notes:
##     Empty input data and NaN values are not supported.
##
## Example:
##
##  X = [10; 15; 12; 18; 20];
##  Y = stats(X);
function Y = stats(X)
  N = X(:, 1);
  NUM_SAMPLES = sum(N);
  MEANS = X(:, 2);
  MEAN = sum(MEANS .* N) / NUM_SAMPLES;
  MIN = min(X(:, 3));
  MAX = max(X(:, 4));
  Y = [NUM_SAMPLES MEAN MIN MAX];
endfunction

