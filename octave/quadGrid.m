#
# Eg: quadGrid([[2, 1]; [3, 2]], 0.1, 0.4)
#
function noCells = quadGrid(params, scale, avoidRadius)
  cells = [];
  cells = addCells(cells, [0, 0], avoidRadius);

  r = 0;
  for i = 1 : size(params, 1)
    n = params(i, 1);
    h = params(i, 2);
    m = floor((r + h) / h);
    r = m * h;
    for j = 1 : n
      for k = 0 : m - 1
        cells = addCells(cells, [r, k * h] * scale, avoidRadius);
        cells = addCells(cells, [k * h, r] * scale, avoidRadius);
      endfor
      cells = addCells(cells, [r, r] * scale, avoidRadius);
      r = r + h;
      m = m + 1;
    endfor
  endfor
  noCells = size(cells, 1);
  if size(cells, 1) > 0
    scatter(cells(:, 1), cells(:, 2),3)
    grid "on";
    grid "minor", "on";
    txt = sprintf("%d cells", noCells);
    title(txt);
  endif
endfunction

function cells = addCell(cells, cell, avoidRadius)
  d = sqrt(sum(cell .* cell));
  if d > avoidRadius
    cells = [cells; cell];
  endif
endfunction

function cells = addCells(cells, cell, avoidRadius)
  cells = addCell(cells, cell, avoidRadius);
  if cell(1) > 0
    cells = addCell(cells, [-cell(1), cell(2)], avoidRadius);
  endif
  if cell(2) > 0
    cells = addCell(cells, [cell(1), -cell(2)], avoidRadius);
    if cell(1) > 0
      cells = addCell(cells, [-cell(1), -cell(2)], avoidRadius);
    endif
  endif
endfunction

