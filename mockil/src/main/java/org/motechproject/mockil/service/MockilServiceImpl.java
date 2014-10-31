package org.motechproject.mockil.service;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.motechproject.commons.date.model.Time;
import org.motechproject.event.MotechEvent;
import org.motechproject.event.listener.EventRelay;
import org.motechproject.event.listener.annotations.MotechListener;
import org.motechproject.messagecampaign.EventKeys;
import org.motechproject.messagecampaign.contract.CampaignRequest;
import org.motechproject.messagecampaign.domain.campaign.CampaignType;
import org.motechproject.messagecampaign.service.MessageCampaignService;
import org.motechproject.messagecampaign.userspecified.CampaignMessageRecord;
import org.motechproject.messagecampaign.userspecified.CampaignRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service("mockilService")
public class MockilServiceImpl implements MockilService {

    private static final String IVR_INITIATE_CALL = "ivr_initiate_call";

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private MessageCampaignService messageCampaignService;
    private Integer numCampaign;
    private EventRelay eventRelay;

    @Autowired
    public MockilServiceImpl(EventRelay eventRelay, MessageCampaignService messageCampaignService) {
        this.eventRelay = eventRelay;
        this.messageCampaignService = messageCampaignService;
        numCampaign = 0;
    }

    @MotechListener(subjects = { EventKeys.SEND_MESSAGE })
    public void handleFiredCampaignMessage(MotechEvent event) {
        logger.debug("handleFiredCampaignMessage({})", event.toString());
    }

    private synchronized String getNextId() {
        return String.format("ExternalId%d", numCampaign++);
    }

    private String decodeDelay(String delay) {
        return delay.replaceAll("[^a-zA-Z0-9]", " ");
    }

    public void create(String campaignName, String delay) {
        logger.debug("create({}, {})", campaignName, delay);

        CampaignRecord campaign = new CampaignRecord();
        campaign.setName(campaignName);
        campaign.setCampaignType(CampaignType.OFFSET);

        CampaignMessageRecord message = new CampaignMessageRecord();
        message.setName("firstMessage");
        message.setStartTime("00:00");
        message.setTimeOffset(decodeDelay(delay));

        campaign.setMessages(Arrays.asList(message));
        messageCampaignService.saveCampaign(campaign);
    }

    public void delete(String campaignName) {
        logger.debug("delete({})", campaignName);

        messageCampaignService.deleteCampaign(campaignName);
    }

    public void enrollMany(String campaignName, int number) {
        logger.debug("enrollMany({}, {})", campaignName, number);
        for (int i = 0 ; i < number ; i++) {
            enroll(campaignName);
        }
    }

    public void enroll(String campaignName) {
        logger.debug("enroll({})", campaignName);
        Time now = new Time(DateTime.now().getHourOfDay(), DateTime.now().getMinuteOfHour());
        LocalDate today = LocalDate.now();
        CampaignRequest campaignRequest = new CampaignRequest(getNextId(), campaignName, today, now);
        messageCampaignService.enroll(campaignRequest);
    }
}
