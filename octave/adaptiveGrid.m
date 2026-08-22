#
# Eg: adaptiveGrid([11, 7], 0.1, 0.4)
#
function noCells = adaptiveGrid(sizes, scale, avoidRadius)
  cells = [];
  cells = addCells(cells, [0, 0], avoidRadius);

  h = 1;
  for n = sizes
    for i = 0 : (n - 1) / 2
      for j = 0 : (n - 1) / 2
        cell = [j, i] * h * scale;
        cells = addCells(cells, cell, avoidRadius);
      endfor
      cell = [0, i * h * scale];
      cells = addCells(cells, cell, avoidRadius);
    endfor
    h = h * n;
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

