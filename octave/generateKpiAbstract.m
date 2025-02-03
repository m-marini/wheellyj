function generateKpiAbstract(hFile, id, kpiTitle, stats, histogram, chart, linear, exponential, genParmFunc)
  n = stats(1);
  yMin = stats(2);
  yMax = stats(3);

  fprintf(hFile, "\n");
  fprintf(hFile, "## %s\n", kpiTitle);
  fprintf(hFile, "\n");
  importFile(hFile, ["templates/" id ".md"]);

  if is_function_handle(genParmFunc)
    genParmFunc(hFile);
  endif

  fprintf(hFile, "\n");
  fprintf(hFile, "| KPI    |             |\n");
  fprintf(hFile, "|--------|------------:|\n");
  fprintf(hFile, "| Steps  | %11d |\n", n);
  fprintf(hFile, "| Mean   | %11s |\n", strFloat(stats(4)));
  fprintf(hFile, "| Sigma  | %11s |\n", strFloat(stats(5)));
  fprintf(hFile, "| Min    | %11s |\n", strFloat(yMin));
  fprintf(hFile, "| Max    | %11s |\n", strFloat(yMax));

  expTrend = 0;
  if exponential != 0 && linear(3) > exponential(3)
    expTrend = 1;
  endif

  if (expTrend)
    fprintf(hFile, "| Trend  | Exponential |\n");
    fprintf(hFile, "| From   | %11s |\n", strFloat(exp(exponential(2))));
    fprintf(hFile, "| To     | %11s |\n", strFloat(exp(exponential(1) * n + exponential(2))));
  else
    fprintf(hFile, "| Trend  |      Linear |\n");
    fprintf(hFile, "| From   | %11s |\n", strFloat(linear(2)));
    fprintf(hFile, "| To     | %11s |\n", strFloat(linear(1) * n + linear(2)));
  endif

  # Prints histogram
  fprintf(hFile, "\n");
  numBins = length(histogram);
  fprintf(hFile, "|   Value    |  Count  |         %% |\n");
  fprintf(hFile, "|-----------:|--------:|----------:|\n");
  for i = 1 : size(histogram, 2);
    fprintf(hFile, "| %10s | %7d | %9s |\n", strFloat(histogram(2, i)), histogram(1, i), strFloat(histogram(1, i) * 100 / n));
  endfor

  fprintf(hFile, "\n");
  fprintf(hFile, "### %s chart\n", kpiTitle);

endfunction

