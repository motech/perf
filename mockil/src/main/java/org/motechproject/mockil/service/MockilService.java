package org.motechproject.mockil.service;

public interface MockilService {
    String makeOutboundCall(String externalId);
    String createOffset(String period);
    String createAbsolute(String dateOrPeriod);
    String delete(String campaignName);
    String enroll(String campaignName);
}
