package org.motechproject.mockil.service;

public interface MockilService {
    String makeOutboundCall();
    String createOffset(String period);
    String createAbsolute(String dateOrPeriod);
    String delete(String campaignName);
    String enroll(String campaignName);
    String sendMotechEvent();
    String expect(int number);
    String resetExpectations();
    String doCall();
    String dontCall();
}
