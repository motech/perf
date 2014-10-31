package org.motechproject.mockil.service;

public interface MockilService {
    void create(String campaignName, String delay);
    void delete(String campaignName);
    void enroll(String campaignName);
    void enrollMany(String campaignName, int number);
}
