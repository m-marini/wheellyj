function generateCriticErrorReport(hFile, r)
  eta = r.eta;
  fprintf(hFile, "$\\eta$ = %s\n", strFloat(eta));
endfunction

