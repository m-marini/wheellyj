function pos = centerPosition(width, height)
  hgr = groot();
  screenSize = get(hgr, "screenSize");
  screenWidth = screenSize(3);
  screenHeight = screenSize(4);
  pos = [(screenWidth - width) / 2 + 1, (screenHeight - height) / 2 + 1, width, height];
endfunction

