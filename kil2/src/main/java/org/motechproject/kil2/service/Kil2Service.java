package org.motechproject.kil2.service;

public interface Kil2Service {
    String createCampaign(String dateOrPeriod, String slot, String day);
    String getStatus();
    String deleteCampaigns();
    String resetExpectations();
}
