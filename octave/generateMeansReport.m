function generateMeansReport(hFile, dataPath, reportPath, id, kpiTitle, mode)
  filename = [dataPath "/data.csv"];
  if exist(filename, "file")
    disp(["Creating report for " filename "..."]);
    stats = csvread(filename);

    fprintf(hFile, "\n");
    fprintf(hFile, "## %s\n", kpiTitle);
    fprintf(hFile, "\n");
    importFile(hFile, ["templates/" id ".md"]);

    fprintf(hFile, "\n");

    fprintf(hFile, "| Action | Probability |\n");
    fprintf(hFile, "|-------:|------------:|\n");
    for i = 1 : size(stats, 2)
      fprintf(hFile, "| %6d | %11.3G |\n", i - 1, stats(i));
    endfor

    fprintf(hFile, "\n");
    fprintf(hFile, "### %s histogram\n", kpiTitle);
    fprintf(hFile, "\n");

    # Generate charts
    histFile = [id "_hist.png"];
    fprintf(hFile, "\n");
    fprintf(hFile, "![%s](%s)\n", kpiTitle, histFile);

    clf();

    bar(0 : size(stats, 2) - 1, stats);
    grid on;
    grid minor on;
    title(kpiTitle);
    file = [reportPath "/" histFile];
    print(file, "-dpng", "-S1200,800");
  else
    disp(["File " filename " not found"]);
  endif
endfunction

