package org.motechproject.mockil.service;

import java.util.Map;

public interface MockilService {
    void createOffset(String campaignName, String delay);
    void createAbsolute(String campaignName, String datetime);
    void delete(String campaignName);
    void enroll(String campaignName, String externalId, String phoneNumber);
    Map<String, Integer> getMaxIds();
}
