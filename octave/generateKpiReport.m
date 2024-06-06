function generateKpiReport(hFile, dataPath, reportPath, id, kpiTitle, mode, vertHist=false)
  if exist([dataPath "/stats/data.csv"], "file")
    disp(["Creating report for " dataPath "/stats/data.csv ..."]);
    stats = csvread([dataPath "/stats/data.csv"]);
    histogram = csvread([dataPath "/histogram/data.csv"]);
    chart = csvread([dataPath "/chart/data.csv"]);
    linear = csvread([dataPath "/linear/data.csv"]);
    if exist([dataPath "/exponential/data.csv"], "file")
      exponential = csvread([dataPath "/exponential/data.csv"]);
    else
      exponential = 0;
    endif

    n = stats(1);
    yMin = stats(2);
    yMax = stats(3);

    fprintf(hFile, "\n");
    fprintf(hFile, "## %s\n", kpiTitle);
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
  #  if exponential != 0
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
    fprintf(hFile, "### %s notes\n", kpiTitle);
    fprintf(hFile, "\n");
    fprintf(hFile, "[Notes]\n");

    fprintf(hFile, "\n");
    fprintf(hFile, "### %s chart\n", kpiTitle);

    # Generate charts
    disp(["Creating report for " dataPath "/chart/data.csv ..."]);
    plotFile = [id "_plot.png"];
    histFile = [id "_hist.png"];
    fprintf(hFile, "\n");
    fprintf(hFile, "![%s](%s)\n", kpiTitle, plotFile);

    fprintf(hFile, "\n");
    fprintf(hFile, "### %s histogram\n", kpiTitle);

    fprintf(hFile, "\n");
    fprintf(hFile, "![%s](%s)\n", kpiTitle, histFile);

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
    disp(["Creating report for " dataPath "/histogram/data.csv ..."]);
    [scaleHist, ordHist] = computeScale(histogram(2,1) , histogram(2,end));
    scaleHistLegend = sprintf("x 10^{%d}", ordHist);
    clf();
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
    disp(["File " dataPath "/stats/data.csv not found"]);
  endif
endfunction
