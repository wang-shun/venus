package com.meidusa.venus.monitor.athena.reporter;

/**
 * Created by GodzillaHua on 7/3/16.
 */
public interface ProblemReporter {

    void problem(String message, Throwable cause);

}
