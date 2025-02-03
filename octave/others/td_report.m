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

# Handles the report button event
function handleReport(h, ev)
  # Opens progress bar
  hProgress = waitbar(0, "Loading ...");
  hPanel = get(h, "parent");
  data = guidata(hPanel);
  # Gets the data and report path from panel
  dataPath = get(data.hPath, "string");
  reportPath = get(data.hReport, "string");
  generateReport(dataPath, reportPath);
  waitbar(1, hProgress,  "Completed.");
  close(hProgress);
endfunction

# Creates the user interface
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
                    "string", [default_path() ""], ...
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
                    "string", [default_path() ""], ...
                    "enable", "inactive", ...
                    "horizontalalignment", "left", ...
                    "position", [x0, y0, pw, bh]);

  y0 = y0 + ys;
  uicontrol(p, "string", "Select data path ...", ...
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


