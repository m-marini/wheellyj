#clear all;
1;

function files = readKpiFiles(path)
  filenames = readdir(path);
  files = {};
  j = 1;
  for i = 1 : size(filenames, 1)
    name = filenames{i, 1};
    if endsWith(name, "_db_data.csv") || endsWith(name, "_dw_data.csv")
      files{j} = name;
      j = j + 1;
    endif
  endfor
endfunction

function [kpi, layer] = extractName(name)
    [kpi, rem] = strtok(name, ".");
    layer = strtok(rem(2 : end), "_");
endfunction

function [kpis, layers] = extractNames(files)
  kpis = {};
  layers = {};
  for i = 1 : size(files, 2)
    name = files{1, i};
    [kpiName, layerName] = extractName(name);
    kpis{i} = kpiName;
    layers{i} = layerName;
  endfor
  kpis = unique(kpis);
  layers = unique(layers);
endfunction

function y = loadData(hProgress, path, files, fn)
  n = length(files);
  waitbar(0 / n, hProgress,  sprintf("Loading 0 / %d ...", n));
  y = csvread([path "/" files{1}]);
  for i = 2 : size(files, 1)
    file = [path "/" files{i}];
    waitbar((i - 1) / n, hProgress,  sprintf("Loading %d / %d ...", i - 1, n));
    y1 = csvread(file);
    y = [y y1];
  endfor
  waitbar(1, hProgress,  sprintf("Loaded %d / %d", n, n));
  y = fn(y);
endfunction

function loadPanel(hPanel, path)
  data = guidata(hPanel);
  filenames = readKpiFiles(path);
  [kpis, layers] = extractNames(filenames);
  set(data.hFilenames, "string", filenames);
  set(data.hKpis, "string", [{"*"} kpis]);
  set(data.hFilenames, "value", []);
  set(data.hLayers, "string", [{"*"} layers]);
  data.path = path;
  guidata(hPanel, data);
endfunction

function values = selectedValues(hList)
  indices = get(hList, "value");
  values = get(hList, "string")(indices);
endfunction

function matching = matchFile(filename, kpi, layer)
  [k l] = extractName(filename);
  matching = (strcmp(kpi, "*") || strcmp(kpi, k)) && (strcmp(layer, "*") || strcmp(layer, l));
endfunction

function indices = findIndices(items, kpi, layer)
  indices = [];
  for i = 1 : length(items)
    if matchFile(items{i}, kpi, layer)
      indices(end + 1) = i;
    endif
  endfor
endfunction

function handleSelect(h, ev)
  data = guidata(get(h, "parent"));
  items = get(data.hFilenames, "string");
  kpi = selectedValues(data.hKpis);
  layer = selectedValues(data.hLayers);
  selectedIndices = get(data.hFilenames, "value");
  newSelected = union(selectedIndices, findIndices(items, kpi, layer));
  set(data.hFilenames, "value", newSelected);
endfunction

function handleClear(h, ev)
  data = guidata(get(h, "parent"));
  items = get(data.hFilenames, "string");
  kpi = selectedValues(data.hKpis);
  layer = selectedValues(data.hLayers);
  selectedIndices = get(data.hFilenames, "value");
  newSelected = setdiff(selectedIndices, findIndices(items, kpi, layer));
  set(data.hFilenames, "value", newSelected);
endfunction

function handleSelectAll(h, ev)
  data = guidata(get(h, "parent"));
  items = get(data.hFilenames, "string");
  set(data.hFilenames, "value", [1 : length(items)]);
endfunction

function handleClearAll(h, ev)
  data = guidata(get(h, "parent"));
  set(data.hFilenames, "value", []);
endfunction

function handlePlot(h, ev)
  hPanel = get(h, "parent");
  data = guidata(hPanel);

  files = selectedValues(data.hFilenames);
  if length(files) > 0
    hFunction = data.hFunction;
    hs = get(hFunction, "selectedobject");
    fn = get(hs, "userdata");
    hProgress = waitbar(0, "Loading ...");
    y = loadData(hProgress, data.path, files, fn);
    delete(hProgress);
    hChart = data.hChart;
    if !(hChart && ishghandle(hChart))
      data.hChart = figure("position", centerPosition(800, 600));
      guidata(hPanel, data);
    endif

    n = size(y)(1);
    mode = {"mean", "min", "max", "lin", "exp"};
    len = max(round(n / 100), 1);
    stride = max((len/ 2), 1);

    name = get(hs, "string");
    printf("\n## %s delta weights \n", name);
    plot_trend(y, len, stride, mode);
    title(sprintf("%s \\Delta weights", name));
  endif
endfunction

function handleLoadData(h, ev)
  path = uigetdir(default_path(), "Select folder with kpi data");
  if path
    loadPanel(get(h, "parent"), path);
  endif
endfunction

function p = createUI()
  f = figure("position", centerPosition(630, 510));
  p = uipanel(f, "position", [0 0 1 1]);

  uicontrol(p, "string", "Load ...", ...
            "position", [10, 460, 80, 30],...
            "callback", @handleLoadData);

  uicontrol(p, "style", "text", ...
            "string", "KPIs", ...
            "position", [10, 430, 150, 20])

  kpisHandle = uicontrol(p, ...
                          "style", "listbox", ...
                          "position", [10, 340, 150, 80]);

  uicontrol(p, "style", "text", ...
            "string", "Layers", ...
            "position", [10, 310, 150 20])

  layersHandle = uicontrol(p, ...
                          "style", "listbox", ...
                          "position", [10, 50, 150, 250]);

  uicontrol(p, "style", "text", ...
            "string", "Files", ...
            "position", [190, 470, 300 20])

  filenamesHandle = uicontrol(p, ...
                          "style", "listbox", ...
                          "position", [190, 50, 300, 410], ...
                          "max", 100);

  uicontrol(p, "string", "Select", ...
            "position", [10 10 50 30], ...
            "callback", @handleSelect);
  uicontrol(p, "string", "Clear", ...
            "position", [70 10 50 30], ...
            "callback", @handleClear);

  uicontrol(p, "string", "Select all", ...
            "position", [190 10 80 30],
            "callback", @handleSelectAll);
  uicontrol(p, "string", "Clear all", ...
            "position", [280 10 80 30], ...
            "callback", @handleClearAll);
  uicontrol(p, "string", "Plot ...", ...
            "position", [370 10 80 30], ...
            "callback", @handlePlot);

  hFunction = uibuttongroup(p, "units", "pixels", ...
                            "title", "Aggregation", ...
                            "position", [500, 360, 120, 130]);

  uicontrol(hFunction, "style", "radiobutton", ...
            "position", [10, 70, 100, 20], ...
            "string", "Max abs", ...
            "userdata", @(x) max(abs(x), [], 2));
  uicontrol(hFunction, "style", "radiobutton", ...
            "position", [10, 40, 100, 20], ...
            "string", "RMS",
            "userdata", @(x) sqrt(mean(x .* x, 2)));
  uicontrol(hFunction, "style", "radiobutton", ...
            "position", [10, 10, 100, 20], ...
            "string", "Mean",
            "userdata", @(x) mean(x, 2));

  panelsData = struct("hKpis", kpisHandle, ...
                      "hLayers", layersHandle, ...
                      "hFilenames", filenamesHandle, ...
                      "hFunction", hFunction, ...
                      "hChart", 0);
  guidata(p, panelsData);

endfunction

function main()
  p = createUI();
  loadPanel(p, default_path());
endfunction

main();


