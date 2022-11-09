function activateFigure(hPanel)
  data = guidata(hPanel);
  hChart = data.hChart;
  if !(hChart && ishghandle(hChart))
    data.hChart = figure("position", centerPosition(800, 600));
    guidata(hPanel, data);
  endif
  set(groot, "currentfigure",  data.hChart);
endfunction

