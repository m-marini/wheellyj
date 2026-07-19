## usage: importFile(hFile, file)
##
## Copy the contents of a text file to an open output file handle.
##
## hFile
##     File handle opened for writing.
## file
##     Path to the source text file.
##
## Example:
##
##  hFile = fopen("an-output-file.txt", "w");
##  importFile(hFile, "a-text-file.txt");
##  fclose(hFile);

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

