csv=getenv("csv");
if length(csv) == 0
  csv = "../csv/";
endif
report=getenv("report");
if length(report) == 0
  report = "../report/";
endif
generateReport(csv, report);
