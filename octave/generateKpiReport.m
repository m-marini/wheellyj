function generateKpiReport(hFile, reportPath, id, kpiTitle, mode, y, len=1, stride=1, bins=11, vertHist=false)

  n = size(y, 1);
  x = [0 : n - 1]';

  z = [];
  legends={};
  ym = sample(y, len, stride, @mean);
  xm = sample(x, len, stride, @mean);
  ymin = min(y);
  expTrend = false;
  linPoly = polyfit(x, y, 1);
  liny = polyval(linPoly, x);
  trend0 = liny(1);
  trend1 = liny(n);
  if (ymin > 0)
    linErr = mean((y - liny) .^ 2);
    expPoly = polyfit(x, log(y), 1);
    expy = exp(polyval(expPoly, x));
    expErr = mean((y - expy) .^ 2);
    expTrend = expErr < linErr;
    if (expTrend)
      trend0 = expy(1);
      trend1 = expy(n);
    endif
  endif

  for I = [1 : length(mode)]
    V = mode{I};
    if strcmp(V, "mean")
      z = [z, ym];
      legends = [ legends, { "Mean" }];
    elseif strcmp(V, "rms")
      z = [z, sample(y, len, stride, @rms)];
      legends = [ legends, {"RMS"}];
    elseif strcmp(V, "median")
      z = [z, sample(y, len, stride, @median)];
      legends = [ legends, {"Median"}];
    elseif strcmp(V, "min")
      z = [z, sample(y, len, stride, @min)];
      legends = [ legends, {"Min"}];
    elseif strcmp(V, "max")
      z = [z, sample(y, len, stride, @max)];
      legends = [ legends, {"Max"}];
    elseif (strcmp(V, "lin") && !expTrend)
      z = [z, polyval(linPoly, xm) ];
      legends = [ legends, {"Linear"}];
    elseif (strcmp(V, "exp") && expTrend)
      z = [ z exp(polyval(expPoly, xm)) ];
      legends = [legends, {"Exponential"}];
    endif
  endfor

  zmin = min(min(z));
  zmax = max(max(z));

  plotFile = [id "_plot.png"];
  histFile = [id "_hist.png"];

  [yHist xHist] = hist(y, bins);

  fprintf(hFile, "## %s\n", kpiTitle);
  fprintf(hFile, "\n");
  fprintf(hFile, "| KPI    |             |\n");
  fprintf(hFile, "|--------|------------:|\n");
  fprintf(hFile, "| Steps  | %11d |\n", n);
  fprintf(hFile, "| Mean   | %11.3g |\n", mean(y));
  fprintf(hFile, "| Sigma  | %11.3g |\n", std(y));
  fprintf(hFile, "| RMS    | %11.3g |\n", rms(y));
  fprintf(hFile, "| Min    | %11.3g |\n", min(y));
  fprintf(hFile, "| Median | %11.3g |\n", median(y));
  fprintf(hFile, "| Max    | %11.3g |\n", max(y));

  if (expTrend)
    fprintf(hFile, "| Trend  | Exponential |\n");
  else
    fprintf(hFile, "| Trend  |      Linear |\n");
  endif
  fprintf(hFile, "| From   | %11.3g |\n", trend0);
  fprintf(hFile, "| To     | %11.3g |\n", trend1);

  fprintf(hFile, "\n");
  if vertHist
    fprintf(hFile, "|   Value    |  Count  |\n");
    fprintf(hFile, "|-----------:|--------:|\n");
    for i = 1 : length(xHist)
      fprintf(hFile, "| %10.3g | %7d |\n", xHist(i), yHist(i));
    endfor
  else
    fprintf(hFile, "|");
    for i = 1 : length(xHist)
      if i > 1
        fprintf(hFile, "|");
      endif
      fprintf(hFile, " %10.3g ", xHist(i));
    endfor
    fprintf(hFile, "|\n");

    fprintf(hFile, "|");
    for i = 1 : length(xHist)
      if i > 1
        fprintf(hFile, "|");
      endif
      fprintf(hFile, "-----------:", xHist(i));
    endfor
    fprintf(hFile, "|\n");

    fprintf(hFile, "|");
    for i = 1 : length(xHist)
      if i > 1
        fprintf(hFile, "|");
      endif
      fprintf(hFile, " %10d ", yHist(i));
    endfor
    fprintf(hFile, "|\n");
  endif

  fprintf(hFile, "\n");
  fprintf(hFile, "### %s notes\n", kpiTitle);
  fprintf(hFile, "\n");
  fprintf(hFile, "[Notes]\n");

  fprintf(hFile, "\n");
  fprintf(hFile, "### %s chart\n", kpiTitle);

  fprintf(hFile, "\n");
  fprintf(hFile, "![%s](%s)\n", kpiTitle, plotFile);

  fprintf(hFile, "\n");
  fprintf(hFile, "### %s histogram\n", kpiTitle);

  fprintf(hFile, "\n");
  fprintf(hFile, "![%s](%s)\n", kpiTitle, histFile);

  clf();
  if expTrend && zmax > 10 * zmin
    semilogy(xm, z);
  else
    plot(xm, z);
  endif
  grid on;
  grid minor on;
  legend("location", "northwest");
  legend(legends);
  title(kpiTitle);
  file = [reportPath plotFile];
  print(file, "-dpng", "-S800,600");

  clf();
  hist(y, bins);
  grid on;
  grid minor on;
  title(kpiTitle);
  file = [reportPath histFile];
  print(file, "-dpng", "-S800,600");

  endfunction
