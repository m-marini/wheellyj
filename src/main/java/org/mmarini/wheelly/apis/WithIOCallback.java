package org.mmarini.wheelly.apis;

import java.util.function.Consumer;

public interface WithIOCallback {
    void setOnReadLine(Consumer<String> onReadLine);

    void setOnWriteLine(Consumer<String> onWriteLine);
}
