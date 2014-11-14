package org.motechproject.mockil.service;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.motechproject.commons.date.model.Time;
import org.motechproject.commons.date.util.JodaFormatter;
import org.motechproject.event.MotechEvent;
import org.motechproject.event.listener.EventRelay;
import org.motechproject.event.listener.annotations.MotechListener;
import org.motechproject.ivr.service.OutboundCallService;
import org.motechproject.messagecampaign.EventKeys;
import org.motechproject.messagecampaign.contract.CampaignRequest;
import org.motechproject.messagecampaign.domain.campaign.CampaignType;
import org.motechproject.messagecampaign.service.CampaignEnrollmentRecord;
import org.motechproject.messagecampaign.service.CampaignEnrollmentsQuery;
import org.motechproject.messagecampaign.service.MessageCampaignService;
import org.motechproject.messagecampaign.userspecified.CampaignMessageRecord;
import org.motechproject.messagecampaign.userspecified.CampaignRecord;
import org.motechproject.mockil.database.Recipient;
import org.motechproject.mockil.database.RecipientDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;


@Service("mockilService")
public class MockilServiceImpl implements MockilService {

    private static final String DATE_TIME_REGEX = "([0-9][0-9][0-9][0-9])([^0-9])([0-9][0-9])([^0-9])([0-9][0-9])" +
            "([^0-9])([0-9][0-9])([^0-9])([0-9][0-9])";
    private static final String DURATION_REGEX = "([0-9]*)([a-zA-Z]*)";
    private String campaignNameRegex;
    private String externalIdRegex;
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private MessageCampaignService messageCampaignService;
    private EventRelay eventRelay;
    private OutboundCallService outboundCallService;
    private RecipientDataService recipientDataService;
    private String hostName;
    private List<String> campaignList;
    private List<String> externalIdList;
    private List<String> phoneNumberList; //indexed the same way externalIdList is
    private Random rand;



    @Autowired
    public MockilServiceImpl(EventRelay eventRelay, MessageCampaignService messageCampaignService,
                             OutboundCallService outboundCallService, RecipientDataService recipientDataService) {
        this.eventRelay = eventRelay;
        this.messageCampaignService = messageCampaignService;
        this.outboundCallService = outboundCallService;
        this.recipientDataService = recipientDataService;
        setupData();
    }


    private String campaignName(int id) {
        return String.format("%s-C%d", hostName, id);
    }


    private String externalId(int id) {
        return String.format("%s-E%d", hostName, id);
    }


    private void setupData() {
        Integer maxCampaignId = 0;
        Integer maxExternalId = 0;
        campaignList = new ArrayList<>();
        externalIdList = new ArrayList<>();
        phoneNumberList = new ArrayList<>();
        rand = new Random();

        try {
            InetAddress ip = InetAddress.getLocalHost();
            hostName = ip.getHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException("Could not get instance host name: " + e.toString(), e);
        }
        campaignNameRegex = String.format("(%s-C)([0-9]*)", hostName);
        externalIdRegex = String.format("(%s-E)([0-9]*)", hostName);

        List<CampaignRecord> campaigns  = messageCampaignService.getAllCampaignRecords();
        for (CampaignRecord campaign : campaigns) {
            String campaignName = campaign.getName();
            if (campaignName.matches(campaignNameRegex)) {
                Integer i = Integer.parseInt(campaignName.replaceAll(campaignNameRegex, "$2"));
                campaignList.add(i, campaignName(i));
                if (i > maxCampaignId) {
                    maxCampaignId = i;
                }
                List<CampaignEnrollmentRecord> enrollments  = messageCampaignService.search(
                        new CampaignEnrollmentsQuery().withCampaignName(campaignName));
                for (CampaignEnrollmentRecord enrollment : enrollments) {
                    String externalId = enrollment.getExternalId();
                    Recipient recipient = recipientDataService.findByExternalId(externalId);
                    Integer j = Integer.parseInt(externalId.replaceAll(externalIdRegex, "$2"));
                    externalIdList.add(j, externalId(j));
                    phoneNumberList.add(j, recipient.getPhoneNumber());
                    if (j > maxExternalId) {
                        maxExternalId = j;
                    }
                }
            }
        }
    }


    private String randomPhoneNumber() {
        return String.format("%03d %03d %04d", rand.nextInt(1000), rand.nextInt(1000), rand.nextInt(10000));
    }


    private synchronized int createRecipient() {
        int id = externalIdList.size();
        externalIdList.add(id, externalId(id));
        phoneNumberList.add(id, randomPhoneNumber());
        return id;
    }


    private synchronized int createCampaign() {
        int id = campaignList.size();
        campaignList.add(id, campaignName(id));
        return id;
    }


    @MotechListener(subjects = { EventKeys.SEND_MESSAGE })
    public void handleFiredCampaignMessage(MotechEvent event) {
        String externalId = (String)event.getParameters().get("ExternalID");
        logger.debug("handleFiredCampaignMessage({})", event.toString());
        makeOutboundCall(externalId);
    }


    public String makeOutboundCall(String externalId) {
        logger.debug("makeOutboundCall({})", externalId);

        Recipient recipient = recipientDataService.findByExternalId(externalId);
        logger.debug("The phone number for recipient with externalID {} is {}", externalId, recipient.getPhoneNumber());

        Map<String, String> params = new HashMap<>();
        params.put("ExternalID", externalId);
        params.put("to", recipient.getPhoneNumber().replaceAll("[^0-9]", ""));
        outboundCallService.initiateCall("config", params);

        return externalId;
    }


    private LocalDate extractDate(String datetime) {
        String date = datetime.replaceAll(DATE_TIME_REGEX, "$1-$3-$5");
        return new LocalDate(date);
    }


    private String extractTime(String datetime) {
        String time = datetime.replaceAll(DATE_TIME_REGEX, "$7:$9");
        return time;
    }


    private String fixPeriod(String delay) {
        String s = delay.replaceAll("[^a-zA-Z0-9]", " ");
        s = s.replaceAll("([0-9]*)([a-z]*)", "$1 $2");
        return s;
    }


    public String createAbsolute(String dateOrPeriod) {
        LocalDate date;
        String time;
        if (dateOrPeriod.matches(DATE_TIME_REGEX)) {
            date = extractDate(dateOrPeriod);
            time = extractTime(dateOrPeriod);
        } else if (dateOrPeriod.matches(DURATION_REGEX)) {
            Period period = new JodaFormatter().parsePeriod(fixPeriod(dateOrPeriod));
            DateTime now = DateTime.now().plus(period.toPeriod());
            date = now.toLocalDate();
            time = String.format("%02d:%02d", now.getHourOfDay(), now.getMinuteOfHour());
        } else {
            throw new IllegalStateException(String.format("%s seems to be neither a datetime or a duration.",
                    dateOrPeriod));
        }

        CampaignRecord campaign = new CampaignRecord();
        int id = createCampaign();
        String campaignName = campaignList.get(id);
        campaign.setName(campaignName);
        campaign.setCampaignType(CampaignType.ABSOLUTE);

        CampaignMessageRecord message = new CampaignMessageRecord();
        message.setName("firstMessage");
        message.setDate(date);
        message.setStartTime(time);

        campaign.setMessages(Arrays.asList(message));
        messageCampaignService.saveCampaign(campaign);
        logger.debug(String.format("Absolute campaign %s: %s %s", campaignName, date.toString(), time));

        return campaignName;
    }


    public String createOffset(String period) {

        String fixedupPeriod = fixPeriod(period);

        CampaignRecord campaign = new CampaignRecord();
        int id = createCampaign();
        String campaignName = campaignList.get(id);
        campaign.setName(campaignName);
        campaign.setCampaignType(CampaignType.OFFSET);

        CampaignMessageRecord message = new CampaignMessageRecord();
        message.setName("firstMessage");
        message.setStartTime("00:00");
        message.setTimeOffset(fixedupPeriod);

        campaign.setMessages(Arrays.asList(message));
        messageCampaignService.saveCampaign(campaign);

        logger.debug("Offset campaign {}: {}", campaignName, fixedupPeriod);

        return campaignName;
    }


    public String delete(String campaignName) {
        logger.debug("delete({})", campaignName);

        messageCampaignService.deleteCampaign(campaignName);

        return campaignName;
    }


    public String enroll(String campaignName) {

        Time now = null;
        LocalDate today = LocalDate.now();
        CampaignRecord campaign = messageCampaignService.getCampaignRecord(campaignName);
        if (campaign.getCampaignType() != CampaignType.ABSOLUTE) {
            now = new Time(DateTime.now().getHourOfDay(), DateTime.now().getMinuteOfHour());
        }
        int id = createRecipient();
        String externalId = externalIdList.get(id);
        String phoneNumber = phoneNumberList.get(id);
        CampaignRequest campaignRequest = new CampaignRequest(externalId, campaignName, today, now);
        messageCampaignService.enroll(campaignRequest);
        Recipient r = recipientDataService.create(new Recipient(externalId, phoneNumber));
        logger.debug("Enrollment externalId {}: {}", externalId, phoneNumber);

        return externalId;
    }
}
