package org.motechproject.kil2.service;

public interface Kil2Service {
    String createRecipients(int slot, int count);
    String createCampaign(String dateOrPeriod, int slot);
    String getExpectations();
    String setExpectations(int count);
    String deleteCampaigns();
    String deleteRecipients();
    String resetExpectations();
}
