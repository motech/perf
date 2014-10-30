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
import org.motechproject.messagecampaign.domain.message.CampaignMessage;
import org.motechproject.messagecampaign.domain.message.OffsetCampaignMessage;
import org.motechproject.messagecampaign.service.MessageCampaignService;
import org.motechproject.messagecampaign.userspecified.CampaignMessageRecord;
import org.motechproject.messagecampaign.userspecified.CampaignRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.joda.time.Period.days;
import static org.joda.time.Period.minutes;

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
    public void handleCFiredCampaignMEssage(MotechEvent event) {
        logger.debug("handleCFiredCampaignMEssage({})", event.toString());
    }

    private synchronized String getNextId() {
        return String.format("ExternalId%d", numCampaign++);
    }

    public void create(String campaignName, Integer minutes) {
        logger.debug("create({}, {})", campaignName, minutes);

        CampaignRecord campaign = new CampaignRecord();
        campaign.setName(campaignName);
        campaign.setCampaignType(CampaignType.OFFSET);

        CampaignMessageRecord message = new CampaignMessageRecord();
        message.setName("firstMessage");
        message.setStartTime("00:00");
        message.setTimeOffset("00:05");

        campaign.setMessages(Arrays.asList(message));
        messageCampaignService.saveCampaign(campaign);
    }

    public void enroll(String campaignName) {
        logger.debug("enroll({})", campaignName);
        Time now = new Time(DateTime.now().getHourOfDay(), DateTime.now().getMinuteOfHour());
        LocalDate today = LocalDate.now();
        CampaignRequest campaignRequest = new CampaignRequest(getNextId(), campaignName, today, now);
        messageCampaignService.enroll(campaignRequest);
    }
}
