package org.mmarini.wheelly.apis;

import io.reactivex.rxjava3.core.Flowable;

public interface WithIOFlowable {
    Flowable<String> readReadLine();

    Flowable<String> readWriteLine();
}
