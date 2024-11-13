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
    generateKpiAbstract(hFile, id, kpiTitle, stats, histogram, chart, linear, exponential)
    generateKpiCharts(hFile, id, kpiTitle, reportPath, mode, stats, histogram, chart, linear, exponential)
  else
    disp(["File " dataPath "/stats/data.csv not found"]);
  endif
endfunction

