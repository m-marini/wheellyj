function plot_trend(Y, LENGTH=1, STRIDE=1, MODE={"mean"})

  N = size(Y)(1);
  X = [0 : N - 1]';

  Z = [];
  LEGEND={};
  YM = sample(Y, LENGTH, STRIDE, @mean);
  XM = sample(X, LENGTH, STRIDE, @mean);

  for I = [1 : length(MODE)]
    V = MODE{I};
    if strcmp(V, "mean")
      Z = [Z, YM];
      LEGEND = [ LEGEND, { "Mean" }];
    elseif strcmp(V, "rms")
      Z = [Z, sample(Y, LENGTH, STRIDE, @(X) sqrt(mean(X .* X)))];
      LEGEND = [ LEGEND, {"RMS"}];
    elseif strcmp(V, "median")
      Z = [Z, sample(Y, LENGTH, STRIDE, @median)];
      LEGEND = [ LEGEND, {"Median"}];
    elseif strcmp(V, "min")
      Z = [Z, sample(Y, LENGTH, STRIDE, @min)];
      LEGEND = [ LEGEND, {"Min"}];
    elseif strcmp(V, "max")
      Z = [Z, sample(Y, LENGTH, STRIDE, @max)];
      LEGEND = [ LEGEND, {"Max"}];
    elseif strcmp(V, "lin")
      LIN_POLY = polyfit(X, Y, 1);
      LIN_Y = polyval(LIN_POLY, XM);
      Z = [Z, LIN_Y];
      LEGEND = [ LEGEND, {"Linear"}];
    elseif strcmp(V, "exp") & min(Y) > 0
      EXP_POLY = polyfit(X, log(Y), 1);
      EXP_Y = exp(polyval(EXP_POLY, XM));
      Z = [ Z EXP_Y ];
      LEGEND = [LEGEND, {"Exponential"}];
    endif
  endfor

  plot(XM, Z);
  grid on;
  legend("location", "northwest");
  legend(LEGEND);

endfunction
