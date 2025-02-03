function z = groupBy(data, f, col=1)
  x = sortrows(data, col);
  z = [];
  key = x(1, col);
  start = 1;
  for i = 2 : size(x, 1)
    if x(i, col) != key
      z = [z; f(x(start : i - 1,:))];
      start = i;
      key = x(i, col);
    endif
  endfor
  z = [z; f(x(start : end, :))];
endfunction

