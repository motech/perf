package org.motechproject.kil2.service;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.motechproject.commons.date.util.JodaFormatter;
import org.motechproject.event.MotechEvent;
import org.motechproject.event.listener.EventRelay;
import org.motechproject.event.listener.annotations.MotechListener;
import org.motechproject.ivr.service.OutboundCallService;
import org.motechproject.kil2.database.Recipient;
import org.motechproject.kil2.database.RecipientDataService;
import org.motechproject.kil2.database.Status;
import org.motechproject.messagecampaign.EventKeys;
import org.motechproject.messagecampaign.contract.CampaignRequest;
import org.motechproject.messagecampaign.domain.campaign.CampaignType;
import org.motechproject.messagecampaign.service.MessageCampaignService;
import org.motechproject.messagecampaign.userspecified.CampaignMessageRecord;
import org.motechproject.messagecampaign.userspecified.CampaignRecord;
import org.motechproject.scheduler.service.MotechSchedulerService;
import org.motechproject.server.config.SettingsFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;


@Service("kil2Service")
public class Kil2ServiceImpl implements Kil2Service {

    private final static String REDIS_SERVER_PROPERTY = "kil2.redis_server";
    private final static String SLOTS_PROPERTY = "kil2.slots";
    private final static String DAYS_PROPERTY = "kil2.days";

    private static final String CALL_EVENT = "org.motechproject.kil2.call";

    private final static long MILLIS_PER_SECOND = 1000;
    private static final String DATE_TIME_REGEX = "([0-9][0-9][0-9][0-9])([^0-9])([0-9][0-9])([^0-9])([0-9][0-9])" +
            "([^0-9])([0-9][0-9])([^0-9])([0-9][0-9])";
    private static final String DURATION_REGEX = "([0-9]*)([a-zA-Z]*)";
    private static final String REDIS_EXPECTATIONS = "expectations";
    private static final String REDIS_EXPECTING = "expecting";
    private static final String REDIS_TIMESTAMP = "timestamp";
    private static final String REDIS_CAMPAIGN_ID = "campaign_id";
    private Logger logger = LoggerFactory.getLogger(Kil2ServiceImpl.class);
    SettingsFacade settingsFacade;
    private EventRelay eventRelay;
    private MessageCampaignService messageCampaignService;
    private OutboundCallService outboundCallService;
    private RecipientDataService recipientDataService;
    private MotechSchedulerService schedulerService;
    private String redisServer;
    private List<String> slotList;
    private List<String> dayList;
    private String hostName;
    private Random rand;
    JedisPool jedisPool;


    @Autowired
    public Kil2ServiceImpl(@Qualifier("kil2Settings") SettingsFacade settingsFacade, EventRelay eventRelay,
                           MessageCampaignService messageCampaignService, OutboundCallService outboundCallService,
                           RecipientDataService recipientDataService, MotechSchedulerService schedulerService) {
        this.settingsFacade = settingsFacade;
        this.eventRelay = eventRelay;
        this.messageCampaignService = messageCampaignService;
        this.outboundCallService = outboundCallService;
        this.recipientDataService = recipientDataService;
        this.schedulerService = schedulerService;
        setupData();
    }


    private void setupData() {

        redisServer = settingsFacade.getProperty(REDIS_SERVER_PROPERTY);
        slotList = Arrays.asList(settingsFacade.getProperty(SLOTS_PROPERTY).split("\\s*,\\s*"));
        dayList = Arrays.asList(settingsFacade.getProperty(DAYS_PROPERTY).split("\\s*,\\s*"));

        logger.info("redis server: {}", redisServer);
        logger.info("slot list: {}", slotList);
        logger.info("day list: {}", dayList);

        rand = new Random();

        jedisPool = new JedisPool(new JedisPoolConfig(), redisServer);
        
        try {
            InetAddress ip = InetAddress.getLocalHost();
            hostName = ip.getHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException("Could not get instance host name: " + e.toString(), e);
        }

        resetExpectations();

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setnx(REDIS_CAMPAIGN_ID, "0");
        }
    }


    public String deleteCampaigns() {
        logger.info("Delete campaigns...");
        long milliStart = System.currentTimeMillis();

        schedulerService.safeUnscheduleAllJobs("org.motechproject.messagecampaign");

        List<CampaignRecord> campaigns = messageCampaignService.getAllCampaignRecords();
        for (CampaignRecord campaign : campaigns) {
            String campaignName = campaign.getName();
            logger.info("Deleting {}...", campaignName);
            long milliStart2 = System.currentTimeMillis();
            messageCampaignService.deleteCampaign(campaignName);
            logger.info("Deleted {} in {}ms", campaignName, System.currentTimeMillis() - milliStart2);
        }

        logger.info("Deleted in {}ms", System.currentTimeMillis() - milliStart);

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(REDIS_CAMPAIGN_ID, "0");
        }


        return "OK";
    }


    private void sendCallMessage(String phoneNumber) {
        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put("to", phoneNumber);
        MotechEvent event = new MotechEvent(CALL_EVENT, eventParams);
        eventRelay.sendEventMessage(event);
    }


    private String slotFromExternalID(String externalID) {
        return externalID.substring(0, 2);
    }


    private String dayFromExternalID(String externalID) {
        return externalID.substring(2, 3);
    }


    private String externalIDFromSlotDay(String slot, String day) {
        return slot+day;
    }


    @MotechListener(subjects = { EventKeys.SEND_MESSAGE })
    public void handleCampaignMessageEvent(MotechEvent event) {
        logger.info(event.toString());
        String externalID = (String)event.getParameters().get("ExternalID");
        String slot = slotFromExternalID(externalID);
        String day = dayFromExternalID(externalID);

        int count = 0;
        long milliStart = System.currentTimeMillis();
        List<Recipient> recipients = recipientDataService.findBySlotDayStatus(slot, day, Status.Active);
        long millis = System.currentTimeMillis() - milliStart;
        float rate = (float) recipients.size() * MILLIS_PER_SECOND / millis;
        logger.info(String.format("Read %d recipient%s in %dms (%s/sec)", recipients.size(),
                recipients.size() == 1 ? "" : "s", millis, rate));
        milliStart = System.currentTimeMillis();
        for(Recipient recipient : recipients) {
            sendCallMessage(recipient.getPhoneNumber());
            count++;
            if (count % 100 == 0) {
                logger.info("Sent {} messages", count);
            }
        }
        millis = System.currentTimeMillis() - milliStart;
        rate = (float) count * MILLIS_PER_SECOND / millis;
        logger.info(String.format("Sent %d message%s in %dms (%s/sec)", count, count == 1 ? "" : "s", millis, rate));
    }


    @MotechListener(subjects = { CALL_EVENT })
    public void handleCallEvent(MotechEvent event) {
        logger.debug(event.toString());
        String phoneNumber = (String)event.getParameters().get("to");
        call(phoneNumber);
        meetExpectation();
    }


    private String call(String phoneNumber) {
        logger.debug("calling {}", phoneNumber);

        Map<String, String> params = new HashMap<>();
        params.put("to", phoneNumber.replaceAll("[^0-9]", ""));
        outboundCallService.initiateCall("config", params);

        return phoneNumber;
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


    private void setExpectations(int count) {
        logger.info("Setting expectations to {}", count);

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(REDIS_EXPECTATIONS, String.valueOf(count));
            jedis.set(REDIS_EXPECTING, String.valueOf(count));
            logger.info("Expectations: {}/{}", jedis.get(REDIS_EXPECTING), jedis.get(REDIS_EXPECTATIONS));
        }
    }


    public String createCampaign(String dateOrPeriod, String slot, String day) {
        if (!slotList.contains(slot)) {
            return String.format("%s is not a valid slot. Valid slots: %s", slot, slotList);
        }
        if (!dayList.contains(day)) {
            return String.format("%s is not a valid day. Valid days: %s", day, dayList);
        }

        try (Jedis jedis = jedisPool.getResource()) {
            LocalDate date;
            String time;
            if (dateOrPeriod.matches(DATE_TIME_REGEX)) {
                date = extractDate(dateOrPeriod);
                time = extractTime(dateOrPeriod);
            }
            else if (dateOrPeriod.matches(DURATION_REGEX)) {
                Period period = new JodaFormatter().parsePeriod(fixPeriod(dateOrPeriod));
                DateTime now = DateTime.now().plus(period.toPeriod());
                date = now.toLocalDate();
                time = String.format("%02d:%02d", now.getHourOfDay(), now.getMinuteOfHour());
            }
            else {
                throw new IllegalStateException(String.format("%s seems to be neither a datetime or a duration.",
                        dateOrPeriod));
            }

            CampaignRecord campaign = new CampaignRecord();
            String campaignName = String.format("Campaign%s", jedis.incr(REDIS_CAMPAIGN_ID));
            campaign.setName(campaignName);
            campaign.setCampaignType(CampaignType.ABSOLUTE);

            CampaignMessageRecord firstMessage = new CampaignMessageRecord();
            firstMessage.setName("firstMessage");
            firstMessage.setDate(date);
            firstMessage.setStartTime(time);
            firstMessage.setMessageKey("first");

            CampaignMessageRecord lastMessage = new CampaignMessageRecord();
            lastMessage.setName("lastMessage");
            lastMessage.setDate(date.plusYears(1));
            lastMessage.setStartTime(time);
            lastMessage.setMessageKey("last");

            campaign.setMessages(Arrays.asList(firstMessage, lastMessage));
            messageCampaignService.saveCampaign(campaign);

            int slotRecipientCount = (int)recipientDataService.countFindBySlotDayStatus(slot, day, Status.Active);
            logger.info(String.format("Enrolling %s with %d recipient%s in %s/%s @ %s %s", campaignName,
                    slotRecipientCount, slotRecipientCount == 1 ? "" : "s", slot, day, date.toString(), time));
            CampaignRequest campaignRequest = new CampaignRequest(externalIDFromSlotDay(slot, day), campaignName,
                    LocalDate.now(), null);
            messageCampaignService.enroll(campaignRequest);

            setExpectations(slotRecipientCount);
            return String.format("%s @ %s %s > %s/%s (%d recipient%s)", campaignName, date.toString(), time, slot,
                    day, slotRecipientCount, slotRecipientCount == 1 ? "" : "s");
        }
    }


    private void meetExpectation() {
        logger.debug("meetExpectation()");

        try (Jedis jedis = jedisPool.getResource()) {

            // Start timer if not already started
            if (!jedis.exists(REDIS_TIMESTAMP)) {
                List<String> t = jedis.time();
                Long millis = Long.valueOf(t.get(0)) * 1000 + Long.valueOf(t.get(1)) / 1000;
                jedis.setnx(REDIS_TIMESTAMP, millis.toString());
            }

            long expecting = jedis.decr(REDIS_EXPECTING);

            // All expectations met
            if (expecting <= 0) {
                List<String> t = jedis.time();
                long milliStop = Long.valueOf(t.get(0)) * 1000 + Long.valueOf(t.get(1)) / 1000;
                long milliStart = Long.valueOf(jedis.get(REDIS_TIMESTAMP));
                long millis = milliStop - milliStart;
                long expectations = Long.valueOf(jedis.get(REDIS_EXPECTATIONS));
                float rate = (float) Long.valueOf(jedis.get(REDIS_EXPECTATIONS)) * MILLIS_PER_SECOND / millis;
                logger.info("Measured {} calls at {} calls/second", expectations, rate);
                resetExpectations();
            } else if (expecting % 500 == 0) {
                logger.info("Expectations: {}/{}", jedis.get(REDIS_EXPECTING), jedis.get(REDIS_EXPECTATIONS));
            }
        }
    }


    public String getStatus() {
        StringBuilder sb = new StringBuilder();
        try (Jedis jedis = jedisPool.getResource()) {
            sb.append(String.format("Expectations: %s/%s", jedis.get(REDIS_EXPECTING), jedis.get
                    (REDIS_EXPECTATIONS)));
        }

        int recipientCount = (int)recipientDataService.count();
        sb.append(String.format("\n\rRecipients: %d", recipientCount));

        List<CampaignRecord> campaigns = messageCampaignService.getAllCampaignRecords();
        sb.append("\n\rCampaigns: ");
        boolean first = true;
        for (CampaignRecord campaign : campaigns) {
            String campaignName = campaign.getName();
            sb.append(String.format("%s%s", first ? "" : ", ", campaignName));
            if (first) first = false;
        }
        if (first) {
            sb.append("none");
        }

        return sb.toString();
    }


    public String resetExpectations() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(REDIS_EXPECTING, "0");
            jedis.set(REDIS_EXPECTATIONS, "0");
            jedis.del(REDIS_TIMESTAMP);
            logger.info("Expectations: {}/{}", jedis.get(REDIS_EXPECTING), jedis.get(REDIS_EXPECTATIONS));
            return "OK";
        }
    }
}
