clear all;

function main()
  checkRightRear();
  checkWrongRear();
endfunction

function checkRightRear()
  csvPath = "d:/csv";
  contactsFile = [csvPath "/s0/canMoveStates/data.csv"];
  actionFile = [csvPath "/actions/move/data.csv"];
  contacts = csvread(contactsFile);
  n = size(contacts, 1);
  actions = csvread(actionFile);
  indexNext = find(filterRearContacts(contacts) & filterMoveForward(actions)) + 1;
  if indexNext(end) > n
    indexNext = indexNext(1 : end - 1);
  endif
  contactsNext = contacts(indexNext);
  indexRight = find(! filterRearContacts(contactsNext));
  printf("right %d / %d\n", size(indexRight, 1), size(indexNext, 1));
  hist(contactsNext(indexRight));
endfunction

function checkWrongRear()
  csvPath = "d:/csv";
  contactsFile = [csvPath "/s0/canMoveStates/data.csv"];
  actionFile = [csvPath "/actions/move/data.csv"];
  contacts = csvread(contactsFile);
  n = size(contacts, 1);
  actions = csvread(actionFile);
  indexNext = find(filterRearContacts(contacts) & filterMoveForward(actions)) + 1;
  if indexNext(end) > n
    indexNext = indexNext(1 : end - 1);
  endif
  contactsNext = contacts(indexNext);
  indexWrong = find(filterRearContacts(contactsNext));
  printf("wrong %d / %d\n", size(indexWrong, 1), size(indexNext, 1));
  hist(contactsNext(indexWrong));
endfunction

function Y = filterRearContacts(contacts)
  Y = contacts == 0 | contacts == 2 | contacts == 4;
endfunction

function Y = speed(move)
  Y = mod(move, 5) .* 30 - 60;
endfunction

function Y = dir(move)
  Y = floor(move ./ 5) * 360 ./ 8 - 180;
endfunction

function Y = filterMoveForward(move)
  dir = dir(move);
  Y = speed(move) > 0 & dir > -90 & dir < 90;
endfunction

function Y = filterMoveBack(move)
  dir = dir(move);
  Y = speed(move) < 0 & dir > -90 & dir < 90;
endfunction

function Y = filterFrontDirection(move)
  Y = contacts == 0 | contacts == 2 | contacts == 4;
endfunction

main();
