clear all;

function [up down] = parseMeasures(data1)
  data = groupBy(data1, @(data)[data(1,1) sum(data(:,2)) mean(data(:,3))]);
  pwr = data( : , 3);
  idx = find (abs(pwr) == max(abs(pwr)));
  rampUp = idx(end);
  rampDown = idx(1);

  upData=data(1 : rampUp, : );
  dt = upData(2 : end, 1) - upData( 1 : end - 1, 1);
  pwr = upData( : , 3);
  pps = [0; upData(2 : end, 2) * 1000 ./ dt];
  up = [pwr pps];

  downData = data(rampUp : end, : );
  dt = downData(2 : end, 1) - downData( 1 : end - 1, 1);
  pwr = downData(1 : end, 3);
  pps = [downData(2 : end, 2) * 1000 ./ dt; 0];
  down = [pwr pps];
endfunction

function [leftUp leftDown rightUp rightDown] = loadTests(tests)
  leftUp = [];
  leftDown = [];
  rightUp = [];
  rightDown = [];
  for i = 1 : size(tests, 1)
    id = tests{i,1};
    [_ left right] = loadTest(id);

    [up down] = parseMeasures(left);
    leftUp = [leftUp; up];
    leftDown = [leftDown; down];

    [up down] = parseMeasures(right);
    rightUp = [rightUp; up];
    rightDown = [rightDown; down];
  endfor
  f = @(data) [data(1, 1) mean(data(:, 2)) std(data(:, 2))];
  leftUp = groupBy(leftUp, f);
  leftDown = groupBy(leftDown, f);
  rightUp = groupBy(rightUp, f);
  rightDown = groupBy(rightDown, f);
endfunction

function plotData(tit, data)
  plot(data(:,1), [data(:,2) data(:,2) - data(:, 3) data(:,2) + data(:, 3)]);
  #errorbar(leftUp( : , 1), leftUp( : , 2), leftUp( : , 3) - leftUp( : , 2), leftUp( : , 4) + leftUp( : , 2));
  grid on;
  grid minor on;
  title(tit);
  xlabel("Power");
  ylabel("Speed (pps)");
endfunction

folder="../measures/";

[leftUp leftDown rightUp rightDown] = loadTests(listTest(folder));

f = @(data) abs(data(2)) <= 250 && data(3) < 10;

leftUp = filterBy(leftUp, f);
leftDown = filterBy(leftDown, f);
rightUp = filterBy(rightUp, f);
rightDown = filterBy(rightDown, f);

csvwrite("leftUp.csv", leftUp);
csvwrite("leftDown.csv", leftDown);
csvwrite("rightUp.csv", rightUp);
csvwrite("rightDown.csv", rightDown);

subplot(2, 2, 1);
plotData("Left Up", leftUp);

subplot(2, 2, 2);
plotData("Right Up", rightUp);

subplot(2, 2, 3);
plotData("Left Down", leftDown);

subplot(2, 2, 4);
plotData("Right Down", rightDown);


