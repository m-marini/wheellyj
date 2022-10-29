function plot_trend(Y, LENGTH=1, STRIDE=1, MODE={"mean"}, title="?")

  N = size(Y)(1);
  X = [0 : N - 1]';

  Z = [];
  LEGEND={};
  YM = sample(Y, LENGTH, STRIDE, @mean);
  XM = sample(X, LENGTH, STRIDE, @mean);
  yMin = min(Y);
  expTrend = false;
  linPoly = polyfit(X, Y, 1);
  linY = polyval(linPoly, X);
  trend0 = linY(1);
  trend1 = linY(N);
  if yMin > 0
    linErr = mean((Y - linY) .^ 2);
    expPoly = polyfit(X, log(Y), 1);
    expY = exp(polyval(expPoly, X));
    expErr = mean((Y - expY) .^ 2);
    expTrend = expErr < linErr;
    if expTrend
      trend0 = expY(1);
      trend1 = expY(N);
    else
    endif
  endif

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
    elseif strcmp(V, "lin") && !expTrend
      Z = [Z, polyval(linPoly, XM) ];
      LEGEND = [ LEGEND, {"Linear"}];
    elseif strcmp(V, "exp") && expTrend
      Z = [ Z exp(polyval(expPoly, XM)) ];
      LEGEND = [LEGEND, {"Exponential"}];
    endif
  endfor

  zmin=min(min(Z));
  zmax=max(max(Z));

  if expTrend && zmax > 10 * zmin
    semilogy(XM, Z);
  else
    plot(XM, Z);
  endif
  grid on;
  legend("location", "northwest");
  legend(LEGEND);

  printf("## %s\n", title);
  printf("\n");
  printf("| KPI    |             |\n");
  printf("|--------|------------:|\n");
  printf("| Mean   | %11.3g |\n", mean(Y));
  printf("| Min    | %11.3g |\n", min(Y));
  printf("| Median | %11.3g |\n", median(Y));
  printf("| Max    | %11.3g |\n", max(Y));

  if expTrend
    printf("| Trend  | Exponential |\n");
  else
    printf("| Trend  |      Linear |\n");
  endif
  printf("| From   | %11.3g |\n", trend0);
  printf("| To     | %11.3g |\n", trend1);
  printf("\n");
endfunction
