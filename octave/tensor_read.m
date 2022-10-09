function Y = tensor_read(FILE_PREFIX)
    SHAPE = csvread([FILE_PREFIX  "_shape.csv"]);
    SHAPE = SHAPE(find(SHAPE > 1));
    if size(SHAPE) == [ 1, 0 ]
      SHAPE = [1];
    endif
    DATA = csvread([FILE_PREFIX "_data.csv"]);
    N = prod(SHAPE);
    M = prod(size(DATA)) / N;
    SHAPE1 = [M SHAPE];
    Y = reshape(DATA, SHAPE1);
endfunction