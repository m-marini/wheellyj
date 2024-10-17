function importFile(hFile, file)
#  disp(["Import file " file]);
  fid = fopen(file);
  if fid >= 0
    while !feof(fid)
      txt = fgets(fid);
      fputs(hFile, txt);
    endwhile
    fclose (fid);
  else
    disp(["File " file " not found"]);
  endif
endfunction

