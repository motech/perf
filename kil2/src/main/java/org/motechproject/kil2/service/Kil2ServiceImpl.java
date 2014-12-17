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
    private final static String ACTIVE_RECIPIENT_PERCENT_PROPERTY = "kil2.active_recipient_percent";

    private static final String CALL_EVENT = "org.motechproject.kil2.call";

    private final static long MILLIS_PER_SECOND = 1000;
    private static final String DATE_TIME_REGEX = "([0-9][0-9][0-9][0-9])([^0-9])([0-9][0-9])([^0-9])([0-9][0-9])" +
            "([^0-9])([0-9][0-9])([^0-9])([0-9][0-9])";
    private static final String DURATION_REGEX = "([0-9]*)([a-zA-Z]*)";
    private static final String REDIS_EXPECTATIONS = "expectations";
    private static final String REDIS_EXPECTING = "expecting";
    private static final String REDIS_TIMESTAMP = "timestamp";
    private static final String REDIS_EXTERNAL_ID = "external_id";
    private static final String REDIS_CAMPAIGN_ID = "campaign_id";
    private Logger logger = LoggerFactory.getLogger(Kil2ServiceImpl.class);
    SettingsFacade settingsFacade;
    private EventRelay eventRelay;
    private MessageCampaignService messageCampaignService;
    private OutboundCallService outboundCallService;
    private RecipientDataService recipientDataService;
    private MotechSchedulerService schedulerService;
    private String redisServer;
    private int activeRecipientPercent;
    private List<String> slotList;
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


    private synchronized void setupData() {

        redisServer = settingsFacade.getProperty(REDIS_SERVER_PROPERTY);
        activeRecipientPercent = Integer.valueOf(settingsFacade.getProperty(ACTIVE_RECIPIENT_PERCENT_PROPERTY));
        slotList = Arrays.asList(settingsFacade.getProperty(SLOTS_PROPERTY).split("\\s*,\\s*"));
        
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
            jedis.setnx(REDIS_EXTERNAL_ID, "0");
            jedis.setnx(REDIS_CAMPAIGN_ID, "0");
        }
    }


    private String threadId() {
        return String.format("%s-%d", hostName, Thread.currentThread().getId());
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


    public String deleteRecipients() {
        logger.info("Delete recipients...");
        long milliStart = System.currentTimeMillis();

        recipientDataService.deleteAll();

        logger.info("Deleted in {}ms", System.currentTimeMillis() - milliStart);

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(REDIS_EXTERNAL_ID, "0");
        }

        return "OK";
    }


    private void sendCallMessage(String phoneNumber) {
        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put("to", phoneNumber);
        MotechEvent event = new MotechEvent(CALL_EVENT, eventParams);
        eventRelay.sendEventMessage(event);
    }


    @MotechListener(subjects = { EventKeys.SEND_MESSAGE })
    public void handleCampaignMessageEvent(MotechEvent event) {
        logger.info(event.toString());
        String slotName = (String)event.getParameters().get("ExternalID");

        int count = 0;
        long milliStart = System.currentTimeMillis();
        List<Recipient> recipients = recipientDataService.findBySlot(slotName);
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
        logger.info(event.toString());
        String phoneNumber = (String)event.getParameters().get("to");
        call(phoneNumber);
        meetExpectation();
    }


    private String call(String phoneNumber) {
        logger.info("calling {}", phoneNumber);

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


    private String randomPhoneNumber() {
        return String.format("%03d %03d %04d", rand.nextInt(1000), rand.nextInt(1000), rand.nextInt(10000));
    }


    private String randomEDD() {
        // Anytime between 30 and 300 days from today
        DateTime edd = DateTime.now().plusDays(30+rand.nextInt(270));
        return edd.toString("yyyy/dd/MM");
    }


    private boolean randomActiveRecipient() {
        return rand.nextInt(100) > activeRecipientPercent ? false : true;
    }


    public String createRecipients(int slot, int count) {
        if (slot < 1 || slot > slotList.size()) {
            return String.format("### INVALID SLOT ### Slot number must be between 1 and %d", slotList.size());
        }
        String slotName = slotList.get(slot-1);
        try (Jedis jedis = jedisPool.getResource()) {
            logger.info("Creating {} recipient{}...", count, count == 1 ? "" : "s");
            long millis, milliStart = System.currentTimeMillis();
            float rate;
            for (int i = 0; i < count; i++) {
                String externalId = String.format("EID%s", jedis.incr(REDIS_EXTERNAL_ID));
                Recipient recipient = new Recipient(externalId, randomPhoneNumber(), randomEDD(), slotName,
                        randomActiveRecipient());
                recipientDataService.create(recipient);
                if (i>0 && i%100==0) {
                    millis = System.currentTimeMillis() - milliStart;
                    rate = (float) i * MILLIS_PER_SECOND / millis;
                    logger.info(String.format("Created %d/%d recipients for %s (%s/sec)", i, count, slotName, rate));
                }
            }
            millis = System.currentTimeMillis() - milliStart;
            rate = (float) count * MILLIS_PER_SECOND / millis;
            logger.info(String.format("Created %d recipient%s for %s in %dms (%s/sec)", count, count == 1 ? "" : "s",
                    slotName, millis, rate));
        }
        return String.format("%s:%d", slotName, count);
    }


    public String createCampaign(String dateOrPeriod, int slot) {
        if (slot < 1 || slot > slotList.size()) {
            return String.format("### INVALID SLOT ### Slot number must be between 1 and %d", slotList.size());
        }
        String slotName = slotList.get(slot-1);

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
            logger.info(String.format("Absolute campaign %s: %s %s", campaignName, date.toString(), time));

            logger.info(String.format("Enrolling %s in %s", campaignName, slotName));
            CampaignRequest campaignRequest = new CampaignRequest(slotName, campaignName, LocalDate.now(), null);
            messageCampaignService.enroll(campaignRequest);

            int slotRecipientCount = (int)recipientDataService.countFindBySlot(slotName);
            setExpectations(slotRecipientCount);
            return String.format("%s @ %s %s > %s (%d recipient%s)", campaignName, date.toString(), time, slotName,
                    slotRecipientCount, slotRecipientCount == 1 ? "" : "s");
        }
    }


    private synchronized void meetExpectation() {
        logger.debug("Meet expectation");

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
            }
        }
    }


    public String setExpectations(int count) {
        logger.debug("Setting expectations to {}", count);

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(REDIS_EXPECTATIONS, String.valueOf(count));
            jedis.set(REDIS_EXPECTING, String.valueOf(count));
            logger.info("Expectations: {}, expecting: {}", jedis.get(REDIS_EXPECTATIONS), jedis.get(REDIS_EXPECTING));
            return String.valueOf(count);
        }
    }


    public String getExpectations() {
        try (Jedis jedis = jedisPool.getResource()) {
            return String.format("%s/%s", jedis.get(REDIS_EXPECTING), jedis.get(REDIS_EXPECTATIONS));
        }
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
