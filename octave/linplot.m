function linplot(plotFile, X, TITLE)
  clf();
  XX = X(:, 1);
  YY = X(:, 2 : 5);
  plot(XX, YY);
  grid on;
  grid minor on;
  legend("location", "northwest");
  legends = {"mean", "min", "max", "exp mean"};
  legend(legends);
  title(TITLE);
  print(plotFile, "-dpng", "-S1200,800");
endfunction

