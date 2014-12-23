package org.motechproject.kil2.service;

public interface Kil2Service {
    String createDaySlotCampaign(String dateOrPeriod, String slot, String day);
    String getStatus();
    String deleteCampaigns();
    String reset();
}
