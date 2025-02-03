clear all;

function result = parseData(data)
#
# Parses data
#
# @param  data Dataset in [time pulses power] format
# @param result Datasets in [stictionPower frictionPower] format

  n = size(data, 1);
  if n > 3
    result = [data(2, 3) data(end - 1, 3)];
  else
    result =[];
  endif
endfunction

function [lf lb rf rb] = loadTests(folder)
#
# Loads all the test in the folder
#
# @returns left forward, left backward, right forward and right backward data
#          in [stictionPower frictionPower] format
  tests = listTest(folder);
  leftData = [];
  rightData = [];
  for i = 1 : size(tests,1)
    [power left right] = loadTest(tests{i});
    leftData = [leftData; parseData(left)];
    rightData = [rightData; parseData(right)];
  endfor
  lf = leftData(find(leftData(:, 1) >= 0), :);
  lb= leftData(find(leftData(:, 1) < 0), :);
  rf = rightData(find(rightData(:, 1) >= 0), :);
  rb = rightData(find(rightData(:, 1) < 0), :);
endfunction

function pwrHist(tit, data)
#
# Plots the histogram of data and prints the 10% or 90% percentile depending on
# data signum
# @param data the data in [values]
#

  x0 = min(data(:, 1));
  x1 = max(data(:, 1));
  slots = x1 - x0 + 1;
  if x0 < 0
    sug = prctile(data(:, 1), [10]);
  else
    sug = prctile(data(:, 1), [90]);
  endif

  hist(data(:, 1), 10);
  title([tit " (" sprintf("%.1f", sug) "=90%)"]);
  xlabel("Power");
  grid on;
endfunction

folder="../stiction/";
[lf lb rf rb] = loadTests(folder);

subplot(2, 4, 1);
pwrHist("Left Forward Stiction", lf(:,1));

subplot(2, 4, 2);
pwrHist("Right Forward Stiction", rf(:,1));

subplot(2, 4, 5);
pwrHist("Left Backward Stiction", lb(:,1));

subplot(2, 4, 6);
pwrHist("Right Backward Stiction", rb(:,1));

subplot(2, 4, 3);
pwrHist("Left Forward Friction", lf(:,2));

subplot(2, 4, 4);
pwrHist("Right Forward Friction", rf(:,2));

subplot(2, 4, 7);
pwrHist("Left Backward Friction", lb(:,2));

subplot(2, 4, 8);
pwrHist("Right Backward Friction", rb(:,2));



