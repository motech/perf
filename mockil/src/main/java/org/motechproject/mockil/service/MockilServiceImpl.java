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
import org.motechproject.messagecampaign.service.MessageCampaignService;
import org.motechproject.messagecampaign.userspecified.CampaignMessageRecord;
import org.motechproject.messagecampaign.userspecified.CampaignRecord;
import org.motechproject.mockil.database.Recipient;
import org.motechproject.mockil.database.RecipientDataService;
import org.motechproject.scheduler.service.MotechSchedulerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;

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

    private final static long MILLIS_PER_SECOND = 1000;
    private static final String DATE_TIME_REGEX = "([0-9][0-9][0-9][0-9])([^0-9])([0-9][0-9])([^0-9])([0-9][0-9])" +
            "([^0-9])([0-9][0-9])([^0-9])([0-9][0-9])";
    private static final String DURATION_REGEX = "([0-9]*)([a-zA-Z]*)";
    private static final String REDIS_SERVER = "pikaivr.motechproject.org";
    private static final String REDIS_EXPECTATIONS = "expectations";
    private static final String REDIS_EXPECTING = "expecting";
    private static final String REDIS_SETUP = "setup";
    private static final String REDIS_TIMESTAMP = "timestamp";
    private static final String TEST_EVENT = "org.motechproject.mockil.test";
    private static final Pool pool = new Pool(REDIS_SERVER, 20); //assuming 20 threads
    private Logger logger = LoggerFactory.getLogger(MockilServiceImpl.class);
    private MessageCampaignService messageCampaignService;
    private EventRelay eventRelay;
    private MotechSchedulerService schedulerService;
    private OutboundCallService outboundCallService;
    private RecipientDataService recipientDataService;
    private String hostName;
    private List<String> campaignList;
    private List<String> absoluteCampaigns;
    private List<String> externalIdList;
    private List<String> phoneNumberList; //indexed the same way externalIdList is
    private Random rand;
    private boolean dontCall = false;


    @Autowired
    public MockilServiceImpl(EventRelay eventRelay, MessageCampaignService messageCampaignService,
                             OutboundCallService outboundCallService, RecipientDataService recipientDataService,
                             MotechSchedulerService schedulerService) {
        this.eventRelay = eventRelay;
        this.messageCampaignService = messageCampaignService;
        this.outboundCallService = outboundCallService;
        this.recipientDataService = recipientDataService;
        this.schedulerService = schedulerService;
        setupData();
    }

    private String threadId() {
        return String.format("%d", Thread.currentThread().getId());
    }

    private String campaignName(int id) {
        return String.format("%s-C%d", hostName, id);
    }

    private String externalId(int id) {
        return String.format("%s-E%d", hostName, id);
    }

    private synchronized void setupData() {
        rand = new Random();

        try {
            InetAddress ip = InetAddress.getLocalHost();
            hostName = ip.getHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException("Could not get instance host name: " + e.toString(), e);
        }

        try (AutoPool pool = new AutoPool()) {
            pool.getJedis().setnx(REDIS_EXPECTING, "0");
            pool.getJedis().setnx(REDIS_EXPECTATIONS, "0");
            pool.getJedis().setnx(REDIS_SETUP, threadId());
            if (threadId().equals(pool.getJedis().get(REDIS_SETUP))) {
                logger.info("Thread {} is resetting the message campaign data", threadId());
                resetAll();
            } else {
                logger.info("Thread {} is not resetting the message campaign data since thread {} is doing it",
                        threadId(), pool.getJedis().get(REDIS_SETUP));
            }
            pool.getJedis().del(REDIS_SETUP);
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
        call(externalId);
        meetExpectation();
    }

    @MotechListener(subjects = { TEST_EVENT })
    public void handleTestEvent(MotechEvent event) {
        logger.debug("handleTestEvent()");
        meetExpectation();
    }

    public String sendCampaignEvent() {
        logger.debug("sendCampaignEvent()");
        Map<String, Object> eventParams = new HashMap<>();
        String externalId = externalIdList.get(rand.nextInt(externalIdList.size()));
        eventParams.put("ExternalID", externalId);
        MotechEvent event = new MotechEvent(EventKeys.SEND_MESSAGE, eventParams);
        eventRelay.sendEventMessage(event);
        return externalId;
    }

    public String sendTestEvent() {
        logger.debug("sendTestEvent()");
        Map<String, Object> eventParams = new HashMap<>();
        MotechEvent event = new MotechEvent(TEST_EVENT, eventParams);
        eventRelay.sendEventMessage(event);
        return "OK";
    }

    private String call(String externalId) {
        logger.debug("call({})", externalId);

        if (dontCall) {
            return externalId;
        }

        Recipient recipient = recipientDataService.findByExternalId(externalId);
        logger.debug("The phone number for recipient with externalID {} is {}", externalId, recipient.getPhoneNumber());

        Map<String, String> params = new HashMap<>();
        params.put("ExternalID", externalId);
        params.put("to", recipient.getPhoneNumber().replaceAll("[^0-9]", ""));
        outboundCallService.initiateCall("config", params);

        return externalId;
    }

    public String makeOutboundCall() {
        logger.debug("makeOutboundCall()");

        String externalId = externalIdList.get(rand.nextInt(externalIdList.size()));
        call(externalId);
        meetExpectation();
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
        logger.info(String.format("Absolute campaign %s: %s %s", campaignName, date.toString(), time));

        absoluteCampaigns.add(campaignName);

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
        logger.info("Offset campaign: {}: {}", campaignName, fixedupPeriod);

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

        if (!absoluteCampaigns.contains(campaignName)) {
            now = new Time(DateTime.now().getHourOfDay(), DateTime.now().getMinuteOfHour());
        }
        int id = createRecipient();
        String externalId = externalIdList.get(id);
        String phoneNumber = phoneNumberList.get(id);
        recipientDataService.create(new Recipient(externalId, phoneNumber));
        CampaignRequest campaignRequest = new CampaignRequest(externalId, campaignName, today, now);
        messageCampaignService.enroll(campaignRequest);
        logger.debug("{}: {}", externalId, phoneNumber);
        if (id % 100 == 0) {
            logger.info("Enrolled {}...", id);
        }

        return externalId;
    }

    private synchronized void meetExpectation() {
        logger.debug("Meet expectation");

        try (AutoPool pool = new AutoPool()) {

            // Start timer if not already started
            if (!pool.getJedis().exists(REDIS_TIMESTAMP)) {
                List<String> t = pool.getJedis().time();
                Long millis = Long.valueOf(t.get(0)) * 1000 + Long.valueOf(t.get(1)) / 1000;
                pool.getJedis().setnx(REDIS_TIMESTAMP, millis.toString());
            }

            long expecting = pool.getJedis().decr(REDIS_EXPECTING);

            // All expectations met
            if (0 == expecting) {
                List<String> t = pool.getJedis().time();
                long milliStop = Long.valueOf(t.get(0)) * 1000 + Long.valueOf(t.get(1)) / 1000;
                long milliStart = Long.valueOf(pool.getJedis().get(REDIS_TIMESTAMP));
                long millis = milliStop - milliStart;
                long expectations = Long.valueOf(pool.getJedis().get(REDIS_EXPECTATIONS));
                float rate = (float) Long.valueOf(pool.getJedis().get(REDIS_EXPECTATIONS)) * MILLIS_PER_SECOND / millis;
                logger.info("Measured {} calls at {} calls/second", expectations, rate);
                resetExpectations();
            }
        }
    }

    public String setExpectations(int number) {
        logger.debug("Raising expectations by {}", number);

        try (AutoPool pool = new AutoPool()) {
            pool.getJedis().incrBy(REDIS_EXPECTATIONS, number);
            pool.getJedis().incrBy(REDIS_EXPECTING, number);
            String expecting = pool.getJedis().get(REDIS_EXPECTING);
            logger.info("Expectations: {}, expecting: {}", pool.getJedis().get(REDIS_EXPECTATIONS), expecting);
            return expecting;
        }
    }

    public String getExpectations() {
        try (AutoPool pool = new AutoPool()) {
            return String.format("Expectations: %s, expecting: %s", pool.getJedis().get(REDIS_EXPECTATIONS),
                    pool.getJedis().get(REDIS_EXPECTING));
        }
    }

    public String resetExpectations() {
        try (AutoPool pool = new AutoPool()) {
            pool.getJedis().set(REDIS_EXPECTING, "0");
            pool.getJedis().set(REDIS_EXPECTATIONS, "0");
            pool.getJedis().del(REDIS_TIMESTAMP);
            logger.info("Expectations: {}, expecting: {}", pool.getJedis().get(REDIS_EXPECTATIONS), pool.getJedis()
                    .get(REDIS_EXPECTING));
            return "OK";
        }
    }

    public String doCall() {
        dontCall = false;
        return "will call";
    }

    public String dontCall() {
        dontCall = true;
        return "won't call";
    }

    public String resetAll() {
        logger.info("Deleting all message campaign data.");

        try (AutoPool pool = new AutoPool()) {
            pool.getJedis().setnx(REDIS_SETUP, threadId());
            if (threadId().equals(pool.getJedis().get(REDIS_SETUP))) {
                logger.info("Thread {} is resetting the message campaign data", threadId());

                schedulerService.safeUnscheduleAllJobs("org.motechproject.messagecampaign");

                List<CampaignRecord> campaigns = messageCampaignService.getAllCampaignRecords();
                for (CampaignRecord campaign : campaigns) {
                    String campaignName = campaign.getName();
                    messageCampaignService.deleteCampaign(campaignName);
                    logger.debug("Deleted {}", campaignName);
                }
            } else {
                logger.info("Thread {} is not resetting the message campaign data since thread {} is doing it",
                        threadId(), pool.getJedis().get(REDIS_SETUP));
            }
            pool.getJedis().del(REDIS_SETUP);
        }

        campaignList = new ArrayList<>();
        absoluteCampaigns = new ArrayList<>();
        externalIdList = new ArrayList<>();
        phoneNumberList = new ArrayList<>();

        resetExpectations();

        return "OK";
    }

    static class Pool {
        private List<Jedis> available;

        public Pool(String URL, int size) {
            available = new ArrayList<>();
            for (int i=0 ; i<size ; i++) {
                available.add(new Jedis(URL));
            }
        }

        public synchronized Jedis use() {
            return available.remove(0);
        }

        public synchronized void unuse(Jedis jedis) {
            available.add(jedis);
        }
    }

    class AutoPool implements AutoCloseable {
        private Jedis jedis;

        public AutoPool() {
            jedis = pool.use();
        }

        public Jedis getJedis() {
            return jedis;
        }

        public void close() {
            pool.unuse(jedis);
            this.jedis = null;
        }
    }
}
