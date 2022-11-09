clear all;

function handleSelectDataPath(h, ev)
  data = guidata(get(h, "parent"));
  path = get(data.hPath, "string");
  path = uigetdir(path, "Select folder with kpi data");
  if path
    set(data.hPath, "string", path);
  endif
endfunction

function handleSelectReportPath(h, ev)
  data = guidata(get(h, "parent"));
  path = get(data.hReport, "string");
  path = uigetdir(path, "Select folder for genereted report");
  if path
    set(data.hReport, "string", path);
  endif
endfunction

function handleReport(h, ev)
  hProgress = waitbar(0, "Loading ...");
  gamma = 0.999;
  hPanel = get(h, "parent");
  data = guidata(hPanel);
  dataPath = get(data.hPath, "string");
  reportPath = get(data.hReport, "string");
  generateReport(dataPath, reportPath, gamma);
  waitbar(1, hProgress,  "Completed.");
  close(hProgress);
endfunction


function createUI()
  f = figure("position", centerPosition(630, 510));
  p = uipanel(f, "position", [0 0 1 1]);
  bw = 200;
  bh = 30;
  pw = 600;
  ys = 40;
  x0 = 10;
  y0 = 10;

  uicontrol(p, "string", "Generate report", ...
            "position", [x0, y0, bw, bh], ...
            "callback", @handleReport);

  y0 = y0 + ys;
  hReport = uicontrol(p, ...
                    "style", "edit", ...
                    "string", "../reports/", ...
                    "enable", "inactive", ...
                    "horizontalalignment", "left", ...
                    "position", [x0, y0, pw, bh]);

  y0 = y0 + ys;
  uicontrol(p, "string", "Select report path ...", ...
            "position", [x0, y0, bw, bh], ...
            "callback", @handleSelectReportPath);

  y0 = y0 + ys;
  hPath = uicontrol(p, ...
                    "style", "edit", ...
                    "string", default_path(), ...
                    "enable", "inactive", ...
                    "horizontalalignment", "left", ...
                    "position", [x0, y0, pw, bh]);

  y0 = y0 + ys;
  uicontrol(p, "string", "Select path ...", ...
            "position", [x0, y0, bw, bh], ...
            "callback", @handleSelectDataPath);

  panelsData = struct("hPath", hPath,
                      "hReport", hReport);
  guidata(p, panelsData);

endfunction

function main()
  createUI();
endfunction

main();


