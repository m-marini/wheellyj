function autoplot(varargin)
  if length(varargin) == 1
    Y = varargin{1};
  elseif length(varargin) == 2
    X = varargin{1};
    Y = varargin{2};
  endif
  MIN = min(min(Y));
  MAX = max(max(Y));
  logPlot =  MIN > 0 && MAX >= MIN * 10;
  if length(varargin) == 1
    if logPlot
      semilogy(Y);
    else
      plot(Y);
    endif
  elseif logPlot
    semilogy(X, Y);
  else
    plot(X, Y);
  endif
endfunction
