function generateActionErrorReport(hFile, eta, alpha)
  fprintf(hFile, "$\\eta$ = %s\n\n", strFloat(eta));
  fprintf(hFile, "$\\alpha$ = %s\n", strFloat(alpha));
endfunction

