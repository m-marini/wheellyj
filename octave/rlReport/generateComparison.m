function generateComparison(hFile, dataPath1, dataPath2, reportPath, id, title, legend, mode)
  if !exist([dataPath1 "/chart/data.csv"], "file")
    disp(["File " dataPath1 "/chart/data.csv not found"]);
  elseif !exist([dataPath1 "/chart/data.csv"], "file")
    disp(["File " dataPath2 "/chart/data.csv not found"]);
  else
    chart1 = csvread([dataPath1 "/chart/data.csv"]);
    chart2 = csvread([dataPath2 "/chart/data.csv"]);
    fprintf(hFile, "\n");
    fprintf(hFile, "## %s\n", title);
    fprintf(hFile, "\n");
    importFile(hFile, ["templates/" id ".md"]);
    fprintf(hFile, "\n");
    data = [chart1(:,1), chart1(:,2), chart2(:,2)];

    generateComparisonChart(hFile, data, reportPath, id, title, legend, mode);
  endif
endfunction

