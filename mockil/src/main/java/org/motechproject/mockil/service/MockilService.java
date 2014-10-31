package org.motechproject.mockil.service;

public interface MockilService {
    void createOffset(String campaignName, String delay);
    void createAbsolute(String campaignName, String datetime);
    void delete(String campaignName);
    void enroll(String campaignName, String externalId);
}
