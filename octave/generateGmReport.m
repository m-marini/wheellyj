function generateGmReport(hFile, n, t)
  minimum = exp(2 / t / n) / (n - 1 + exp(2 / t));
  maximum = 1 / n;
  fprintf(hFile, "**The minimum value is %s**.\n", strFloat(minimum));
  fprintf(hFile, "\n");
  fprintf(hFile, "**The maximum value is %s**.\n", strFloat(maximum));
  fprintf(hFile, "\n");
  fprintf(hFile, "$n$ = %d\n", n);
  fprintf(hFile, "\n");
  fprintf(hFile, "$T$ = %s\n", strFloat(t));
endfunction

