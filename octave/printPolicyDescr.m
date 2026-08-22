## usage: printPolicyDescr(hFile, A)
##
## Generate a Markdown report section and write it to an open file handle.
## The report includes policy description for the specificed number of actions.
##
## hFile
##     File handle opened for writing where the report is written.
##
## A
##     Number of actions
##
## Example:
##
##  hFile = fopen("report.md", "w");
##  printPolicyDescr(hFile, 306);
##  fclose(hFile);

function printPolicyDescr(hFile, A)
  P = 1 / A;
  Plog10 = -log10(A);
  Plog2 = -log2(A);
  fprintf(hFile, "For $|A| = %d$\n", A);
  fprintf(hFile, "\n");
  fprintf(hFile, "$$\n");
  if A > 100
    fprintf(hFile, "P(a) \\approx %.3f \\times 10^{-3}\n", P * 1000);
  else
    fprintf(hFile, "P(a) \\approx %.3f\n", P);
  endif
  fprintf(hFile, "$$.\n");
  fprintf(hFile, "\n");
  fprintf(hFile, "In logarithmic scale it corresponds to\n");
  fprintf(hFile, "\n");
  fprintf(hFile, "$$\n");
  fprintf(hFile, "\\log_{10}(P(a)) \\approx %.3f\\\\\n", Plog10);
  fprintf(hFile, "\\log_{2}(P(a)) \\approx %.3f\n", Plog2);
  fprintf(hFile, "$$.\n");
  fprintf(hFile, "\n");
  fprintf(hFile, "The report displays logarithmic values together with their corresponding probability values.\n");
endfunction

