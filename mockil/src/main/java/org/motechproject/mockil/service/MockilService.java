package org.motechproject.mockil.service;

public interface MockilService {
    String makeOutboundCall();
    String createOffset(String period);
    String createAbsolute(String dateOrPeriod);
    String delete(String campaignName);
    String enroll(String campaignName);
    String sendCampaignEvent();
    String sendTestEvent();
    String setExpectations(int number);
    String resetExpectations();
    String getExpectations();
    String doCall();
    String dontCall();
    String resetAll();
}
