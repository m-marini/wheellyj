function plotTrend(Y, LENGTH=100)
  Z = [Y, sqrt(movmean(Y .* Y, LENGTH)), movmedian(Y, LENGTH), movmin(Y, LENGTH), movmax(Y, LENGTH)];
  plot(Z);
  grid on;
  title(sprintf("Trend"));
  LEGEND = {"Y", "RMS", "Median", "Min", "Max"};
  legend(LEGEND);
endfunction