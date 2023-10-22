function z = filterBy(data, f)
  idx = [];
  for i = 1 : size(data,1)
      if  f(data(i,:))
        idx=[idx; i];
      endif
  endfor
  z = data(idx,:);
endfunction

