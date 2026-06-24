function printReport(hFile, data, headTemplate, title, chartFile, log10Flag=false)
  stats = stats(data);
  importFile(hFile, headTemplate);

  if log10Flag
    fprintf(hFile, "| %.0f | ", stats(1));
    fprintPow(hFile, stats(2));
    fprintf(hFile, " | ");
    fprintPow(hFile, stats(3));
    fprintf(hFile, " | ");
    fprintPow(hFile, stats(4));
    fprintf(hFile, " |\n");
  else
    fprintf(hFile, "| %.0f | %.3f | %.2f | %.2f |\n",
      stats(1), stats(2), stats(3), stats(4));
  endif

  fprintf(hFile, "\n");
  fprintf(hFile, "### %s chart\n", title);
  fprintf(hFile, "\n");
  fprintf(hFile, "![%s](%s)\n", title, chartFile);
  fprintf(hFile, "\n");
endfunction

