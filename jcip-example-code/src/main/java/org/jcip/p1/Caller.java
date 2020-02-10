package org.jcip.p1;

import java.util.concurrent.Callable;

public class Caller implements Callable {

    public Object call() throws Exception {
        Thread.sleep(5000);
        return "Finished";
    }
}
