## usage: linplot(plotFile, X, REGRESSION, TITLE)
##
## Generate a line plot from aggregated data stored in X and save it
## to a file. The plot includes the data series and the linear
## regression trend.
##
## plotFile
##     Path of the output plot file.
##
## X
##     Matrix containing the data to plot.
##     X must be an N-by-5 matrix. The first column contains the x-axis
##     values. Columns 2 to 5 contain the y-data series: mean value,
##     minimum value, maximum value, and exponential moving average,
##     respectively.
##
## REGRESSION
##     Two-element vector containing the start and end values of the
##     linear regression line. The regression line is computed between
##     the first and last x-axis values and added to the plot.
##
## TITLE
##     Title of the generated plot.
##
## Example:
##
##  X = [1 10 8 12 9;
##       2 15 11 18 14;
##       3 12 10 16 13];
##  linplot("my-plot.png", X, [9 14], "Sample data plot");
function linplot(plotFile, X, REGRESSION, TITLE)
  clf();
  XX = X(:, 1);
  Y0 = REGRESSION(1);
  Y1 = REGRESSION(2);
  N = XX(end, 1);
  YR = (Y1 - Y0) .* XX / N + Y0;
  YY = [X(:, 2 : 5) YR];
  plot(XX, YY);
  grid on;
  grid minor on;
  legend("location", "northwest");
  legends = {"Mean", "Min", "Max", "EMA", "Regression"};
  legend(legends);
  title(TITLE);
  print(plotFile, "-dpng", "-S1200,800");
endfunction

