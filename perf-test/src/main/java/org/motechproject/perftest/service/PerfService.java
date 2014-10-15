package org.motechproject.perftest.service;

public interface PerfService {
    String doNothing();
    String doSendEvent();
    String doSendAndReceiveEvent();
}
