package org.motechproject.mockil.service;

import java.util.Map;

public interface MockilService {
    void create(String campaignName, Integer minutes);
    void enroll(String campaignName);
}
