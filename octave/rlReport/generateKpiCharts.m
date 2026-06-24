function generateKpiCharts(hFile, id, kpiTitle, reportPath, mode, stats, histogram, chart, linear, exponential)

  n = stats(1);
  yMin = stats(2);
  yMax = stats(3);

  expTrend = 0;
  if exponential != 0 && linear(3) > exponential(3)
    expTrend = 1;
  endif

  [scaleHist, ordHist] = computeScale(histogram(2,1) , histogram(2,end));
  withHist = ordHist < 12;

  # Generate charts
  plotFile = [id "_plot.png"];
  histFile = [id "_hist.png"];
  fprintf(hFile, "\n");
  fprintf(hFile, "![%s](%s)\n", kpiTitle, plotFile);

  fprintf(hFile, "\n");
  fprintf(hFile, "### %s histogram\n", kpiTitle);
  fprintf(hFile, "\n");

  if withHist
    fprintf(hFile, "![%s](%s)\n", kpiTitle, histFile);
  else
    fprintf(hFile, "*Not available*\n");
  endif

  # Generate legends
  z=[];
  xm = chart(:, 1);
  legends={};
  for I = [1 : length(mode)]
    V = mode{I};
    if strcmp(V, "mean")
      z = [z, chart(:, 2)];
      legends = [ legends, { "Mean" }];
    elseif strcmp(V, "min")
      z = [z, chart(:, 3)];
      legends = [ legends, {"Min"}];
    elseif strcmp(V, "max")
      z = [z, chart(:, 4)];
      legends = [ legends, {"Max"}];
    elseif (strcmp(V, "lin") && !expTrend)
      z = [z,  xm .* linear(1, 1) + linear(1, 2)];
      legends = [ legends, {"Linear"}];
    elseif (strcmp(V, "exp") && expTrend)
      z = [ z exp(xm .* exponential(1) + exponential(2)) ];
      legends = [legends, {"Exponential"}];
    endif
  endfor

  # Generate scale legend
  [scale, ord] = computeScale(yMin, yMax);
  scaleLegend = sprintf("x 10^{%d}", ord);

  # Generate values chart
  clf();
  if yMin > 0 && (yMax / yMin) > 100
    semilogy(xm, z * scale);
  else
    plot(xm, z * scale);
  endif
  grid on;
  grid minor on;
  legend("location", "northwest");
  legend(legends);
  title(kpiTitle);
  if scale != 1
    ylabel(scaleLegend);
  endif
  file = [reportPath "/" plotFile];
  print(file, "-dpng", "-S1200,800");

  # Generate histogram chart
  scaleHistLegend = sprintf("x 10^{%d}", ordHist);
  clf();
  if withHist
    bar(histogram(2, :) * scaleHist, histogram(1, :));
    grid on;
    grid minor on;
    title(kpiTitle);
    if scaleHist != 1
      xlabel(scaleHistLegend);
    endif
    file = [reportPath "/" histFile];
    print(file, "-dpng", "-S1200,800");
  else
  endif
endfunction

