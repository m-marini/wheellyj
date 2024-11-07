function generateKpiAbstract(hFile, id, kpiTitle, stats, histogram, chart, linear, exponential)
  n = stats(1);
  yMin = stats(2);
  yMax = stats(3);

  fprintf(hFile, "\n");
  fprintf(hFile, "## %s\n", kpiTitle);
  fprintf(hFile, "\n");
  importFile(hFile, ["templates/" id ".md"]);
  fprintf(hFile, "\n");
  fprintf(hFile, "| KPI    |             |\n");
  fprintf(hFile, "|--------|------------:|\n");
  fprintf(hFile, "| Steps  | %11d |\n", n);
  fprintf(hFile, "| Mean   | %11.3G |\n", stats(4));
  fprintf(hFile, "| Sigma  | %11.3G |\n", stats(5));
  fprintf(hFile, "| Min    | %11.3G |\n", yMin);
  fprintf(hFile, "| Max    | %11.3G |\n", yMax);

  expTrend = 0;
  if exponential != 0 && linear(3) > exponential(3)
    expTrend = 1;
  endif

  if (expTrend)
    fprintf(hFile, "| Trend  | Exponential |\n");
    fprintf(hFile, "| From   | %11.3G |\n", exp(exponential(2)));
    fprintf(hFile, "| To     | %11.3G |\n", exp(exponential(1) * n + exponential(2)));
  else
    fprintf(hFile, "| Trend  |      Linear |\n");
    fprintf(hFile, "| From   | %11.3G |\n", linear(2));
    fprintf(hFile, "| To     | %11.3G |\n", linear(1) * n + linear(2));
  endif

  # Prints histogram
  fprintf(hFile, "\n");
  numBins = length(histogram);
  fprintf(hFile, "|   Value    |  Count  |         %% |\n");
  fprintf(hFile, "|-----------:|--------:|----------:|\n");
  for i = 1 : size(histogram, 2);
    fprintf(hFile, "| %10.3G | %7d | %9.3G |\n", histogram(2, i), histogram(1, i), histogram(1, i) * 100 / n);
  endfor

  fprintf(hFile, "\n");
  fprintf(hFile, "### %s chart\n", kpiTitle);

endfunction

