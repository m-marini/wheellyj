## usage: printReport(hFile, data, regression, headTemplate, title, reportPath, chartFile, log10Flag=false)
##
## Generate a Markdown report section and write it to an open file handle.
## The report includes aggregated data statistics, linear regression
## information, and the associated chart.
##
## hFile
##     File handle opened for writing where the report is written.
##
## data
##     Aggregated data matrix used to generate the statistical summary
##     and the associated chart.
##
## regression
##     Two-element vector containing the start and end values of the
##     fitted linear regression line. The values correspond to the
##     first and last samples of the data series.
##
## headTemplate
##     Path of the Markdown template file used to generate the
##     report header.
##
## title
##     Title of the generated report section.
##
## reportPath
##     Path of the report output directory where the chart file is stored.
##
## chartFile
##     Name of the chart file embedded in the Markdown report.
##
## log10Flag
##     If true, display statistical and regression values using
##     base-10 exponent notation. Original values are shown together
##     with their corresponding power-of-10 representation.
##     Default value is false.
##
## Example:
##
##  hFile = fopen("report.md", "w");
##  printReport(hFile, data, [0.42 0.47],
##              "header.md", "Statistics report",
##              "../reports/", "chart.png");
##  fclose(hFile);

function printReport(hFile, data, regression, headTemplate, title, reportPath, chartFile, log10Flag=false)
  stats = stats(data);
  importFile(hFile, headTemplate);
  fprintf(hFile, "### Statistics\n");
  if log10Flag
    fprintf(hFile, "The reported values are $\\log_{10}$ of the maximum action probability.\n");
    fprintf(hFile, "The probability values are shown in parentheses.\n");
  endif

  fprintf(hFile, "\n");
  fprintf(hFile, "| samples | mean | min | max | trend start | trend end |\n");
  fprintf(hFile, "|---------|------|-----|-----|-------------|-----------|\n");

  if log10Flag
    fprintf(hFile, "| %.0f | ", stats(1));
    fprintPow(hFile, stats(2));
    fprintf(hFile, " | ");
    fprintPow(hFile, stats(3));
    fprintf(hFile, " | ");
    fprintPow(hFile, stats(4));
    fprintf(hFile, " | ");
    fprintPow(hFile, regression(1));
    fprintf(hFile, " | ");
    fprintPow(hFile, regression(2));
    fprintf(hFile, " |\n");
    fprintf(hFile, "\n");
  else
    fprintf(hFile, "| %.0f | %.3f | %.2f | %.2f | %.2f | %.2f |\n",
      stats(1), stats(2), stats(3), stats(4), regression(1), regression(2));
  endif
  fprintf(hFile, "\n");

  fprintf(hFile, "**Trend start** and **trend end** correspond to the fitted linear regression values at the first and last samples, respectively.\n");
  fprintf(hFile, "\n");

  fprintf(hFile, "### Plot\n");
  fprintf(hFile, "\n");
  fprintf(hFile, "![%s](%s)\n", title, chartFile);
  fprintf(hFile, "\n");

  linplot([reportPath chartFile], data, regression, title);
endfunction

