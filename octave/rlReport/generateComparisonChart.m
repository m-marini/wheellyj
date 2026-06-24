function generateComparisonChart(hFile, data, reportPath, id, chartTitle, legends, mode)

  # Generate charts
  plotFile = [id "_plot.png"];
  fprintf(hFile, "\n");
  fprintf(hFile, "![%s](%s)\n", chartTitle, plotFile);

  xm = data(:, 1);
  z = data(:, 2 : end);
  yMin = min(min(z));
  yMax = max(max(z));

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
  title(chartTitle);
  if scale != 1
    ylabel(scaleLegend);
  endif
  file = [reportPath "/" plotFile];
  print(file, "-dpng", "-S1200,800");

endfunction

