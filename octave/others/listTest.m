function tests = listTest(folder)
  tests = glob([folder "/*_head.csv"]);
  for i = 1 : size(tests, 1)
    file = tests{i, 1};
    file = file(1 : end - 9);
    tests{i,1} = file;
  endfor
endfunction

