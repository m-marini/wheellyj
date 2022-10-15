function Y=softmax(X)
  EXP = exp(X);
  Y = EXP ./ sum(EXP, 2);
endfunction
