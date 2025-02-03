function [power left right] = loadTest(id)
  power = csvread([id "_head.csv"]);
  left = csvread([id "_left.csv"]);
  right = csvread([id "_right.csv"]);
endfunction

