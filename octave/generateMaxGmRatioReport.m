function generateMaxGmRatioReport(hFile, n, t)
  minimum = 1;
  maximum = 1 / exp(-2 * (n - 1) / t / n);
  fprintf(hFile, "**The minimum value is %.3G**.\n", minimum);
  fprintf(hFile, "\n");
  fprintf(hFile, "**The maximum value is %.3G**.\n", maximum);
  fprintf(hFile, "\n");
  fprintf(hFile, "$n = %d$\n", n);
  fprintf(hFile, "\n");
  fprintf(hFile, "$T = %.3G$\n", t);
endfunction

