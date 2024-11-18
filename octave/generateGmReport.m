function generateGmReport(hFile, n, t)
  minimum = exp(2 / t / n) / (n - 1 + exp(2 / t));
  maximum = 1 / n;
  fprintf(hFile, "**The minimum value is %.3G**.\n", minimum);
  fprintf(hFile, "\n");
  fprintf(hFile, "**The maximum value is %.3G**.\n", maximum);
  fprintf(hFile, "\n");
  fprintf(hFile, "$n = %d$\n", n);
  fprintf(hFile, "\n");
  fprintf(hFile, "$T = %.3G$\n", t);
endfunction

