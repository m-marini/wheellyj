function generateMaxMinRatioReport(hFile, n, t)
  minimum = 1;
  maximum = exp(2 / t);
  saturated = exp(0.96 * 2 / t);
  fprintf(hFile, "**The minimum value is %.3G**.\n", minimum);
  fprintf(hFile, "\n");
  fprintf(hFile, "**The maximum value is %.3G**.\n", maximum);
  fprintf(hFile, "\n");
  fprintf(hFile, "**The saturated layer value is %.3G**.\n", saturated);
  fprintf(hFile, "\n");
  fprintf(hFile, "$n = %d$\n", n);
  fprintf(hFile, "\n");
  fprintf(hFile, "$T = %.3G$\n", t);
endfunction

