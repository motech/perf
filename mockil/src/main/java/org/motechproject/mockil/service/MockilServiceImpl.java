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
import org.motechproject.messagecampaign.service.CampaignEnrollmentRecord;
import org.motechproject.messagecampaign.service.CampaignEnrollmentsQuery;
import org.motechproject.messagecampaign.service.MessageCampaignService;
import org.motechproject.messagecampaign.userspecified.CampaignMessageRecord;
import org.motechproject.messagecampaign.userspecified.CampaignRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service("mockilService")
public class MockilServiceImpl implements MockilService {

    private static final String IVR_INITIATE_CALL = "ivr_initiate_call";

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private MessageCampaignService messageCampaignService;
    private EventRelay eventRelay;


    @Autowired
    public MockilServiceImpl(EventRelay eventRelay, MessageCampaignService messageCampaignService) {
        this.eventRelay = eventRelay;
        this.messageCampaignService = messageCampaignService;
    }


    @MotechListener(subjects = { EventKeys.SEND_MESSAGE })
    public void handleFiredCampaignMessage(MotechEvent event) {
        logger.debug("handleFiredCampaignMessage({})", event.toString());
    }


    public Map<String, Integer> getMaxIds() {
        Integer maxCampaignId = 0;
        Integer maxExternalId = 0;

        List<CampaignRecord> campaigns  = messageCampaignService.getAllCampaignRecords();
        for (CampaignRecord campaign : campaigns) {
            String campaignName = campaign.getName();
            if (campaignName.matches("C[0-9]*")) {
                Integer i = Integer.parseInt(campaignName.substring(1));
                if (i > maxCampaignId) {
                    maxCampaignId = i;
                }
                List<CampaignEnrollmentRecord> enrollments  = messageCampaignService.search(
                        new CampaignEnrollmentsQuery().withCampaignName(campaignName));
                for (CampaignEnrollmentRecord enrollment : enrollments) {
                    String externalId = enrollment.getExternalId();
                    if (externalId.matches("E[0-9]*")) {
                        Integer j = Integer.parseInt(externalId.substring(1));
                        if (j > maxExternalId) {
                            maxExternalId = j;
                        }
                    }
                }
            }

        }

        Map<String, Integer> ret = new HashMap<>();
        ret.put("maxCampaignId", maxCampaignId);
        ret.put("maxExternalId", maxExternalId);
        return ret;
    }


    private LocalDate extractDate(String datetime) {
        String date = datetime.replaceAll("([0-9][0-9][0-9][0-9])([^0-9])([0-9][0-9])([^0-9])([0-9][0-9])([^0-9])([0-9][0-9])([^0-9])([0-9][0-9])(.*)",
                "$1-$3-$5");
        return new LocalDate(date);
    }


    private String extractTime(String datetime) {
        String time = datetime.replaceAll("([0-9][0-9][0-9][0-9])([^0-9])([0-9][0-9])([^0-9])([0-9][0-9])([^0-9])([0-9][0-9])([^0-9])([0-9][0-9])(.*)",
                "$7:$9");
        return time;
    }


    public void createAbsolute(String campaignName, String datetime) {
        logger.debug("createAbsolute({}, {})", campaignName, datetime);

        LocalDate date = extractDate(datetime);
        String time = extractTime(datetime);

        CampaignRecord campaign = new CampaignRecord();
        campaign.setName(campaignName);
        campaign.setCampaignType(CampaignType.ABSOLUTE);

        CampaignMessageRecord message = new CampaignMessageRecord();
        message.setName("firstMessage");
        message.setDate(date);
        message.setStartTime(time);

        campaign.setMessages(Arrays.asList(message));
        messageCampaignService.saveCampaign(campaign);
        logger.debug("Successfully created Absolute campaign on {} at {}", date.toString(), time);
    }


    private String decodeDelay(String delay) {
        String s = delay.replaceAll("[^a-zA-Z0-9]", " ");
        s = s.replaceAll("([0-9]*)([a-z]*)", "$1 $2");
        return s;
    }


    public void createOffset(String campaignName, String delay) {
        logger.debug("createOffset({}, {})", campaignName, delay);

        String period = decodeDelay(delay);

        CampaignRecord campaign = new CampaignRecord();
        campaign.setName(campaignName);
        campaign.setCampaignType(CampaignType.OFFSET);

        CampaignMessageRecord message = new CampaignMessageRecord();
        message.setName("firstMessage");
        message.setStartTime("00:00");
        message.setTimeOffset(period);

        campaign.setMessages(Arrays.asList(message));
        messageCampaignService.saveCampaign(campaign);

        logger.debug("Successfully created Offset campaign: {}", period);
    }


    public void delete(String campaignName) {
        logger.debug("delete({})", campaignName);

        messageCampaignService.deleteCampaign(campaignName);
    }


    public void enroll(String campaignName, String externalId) {
        logger.debug("enroll({})", campaignName);


        Time now = null;
        LocalDate today = LocalDate.now();
        CampaignRecord campaign = messageCampaignService.getCampaignRecord(campaignName);
        if (campaign.getCampaignType() != CampaignType.ABSOLUTE) {
            now = new Time(DateTime.now().getHourOfDay(), DateTime.now().getMinuteOfHour());
        }
        CampaignRequest campaignRequest = new CampaignRequest(externalId, campaignName, today, now);
        messageCampaignService.enroll(campaignRequest);
    }
}
