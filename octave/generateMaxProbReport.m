function generateMaxProbReport(hFile, n, t)
  minimum = 1 / n;
  maximum = exp(2 / t) / (n - 1 + exp(2 / t));
  fprintf(hFile, "**The minimum value of maximum probability is %.3G**.\n", minimum);
  fprintf(hFile, "\n");
  fprintf(hFile, "**The maximum value of maximum probability is %.3G**.\n", maximum);
  fprintf(hFile, "\n");
  fprintf(hFile, "$n = %d$\n", n);
  fprintf(hFile, "\n");
  fprintf(hFile, "$T = %.3G$\n", t);
endfunction

