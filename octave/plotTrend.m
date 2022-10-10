function plotTrend(Y, HIDE_Y=false, LENGTH=100)
  if HIDE_Y
    Z = [movmean(Y, LENGTH), movmedian(Y, LENGTH), movmin(Y, LENGTH), movmax(Y, LENGTH)];    
    LEGEND = {"Y", "Mean", "Median", "Min", "Max"};
  else
    Z = [Y, movmean(Y, LENGTH), movmedian(Y, LENGTH), movmin(Y, LENGTH), movmax(Y, LENGTH)];    
    LEGEND = {"Mean", "Median", "Min", "Max"};
  endif
  plot(Z);
  grid on;
  legend(LEGEND);
endfunction