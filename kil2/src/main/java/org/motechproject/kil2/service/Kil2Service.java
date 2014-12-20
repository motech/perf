package org.motechproject.kil2.service;

public interface Kil2Service {
    String createRecipients(String slot, String day, Boolean isActive, int count);
    String createCampaign(String dateOrPeriod, String slot, String day);
    String getStatus();
    String deleteCampaigns();
    String deleteRecipients();
    String resetExpectations();
}
