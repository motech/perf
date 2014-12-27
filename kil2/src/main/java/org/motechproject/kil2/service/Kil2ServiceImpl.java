package org.motechproject.kil2.service;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.motechproject.commons.date.util.JodaFormatter;
import org.motechproject.event.MotechEvent;
import org.motechproject.event.listener.EventRelay;
import org.motechproject.event.listener.annotations.MotechListener;
import org.motechproject.ivr.service.OutboundCallService;
import org.motechproject.kil2.database.*;
import org.motechproject.mds.query.SqlQueryExecution;
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

import javax.jdo.Query;
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
    private CallDataService callDataService;
    private CampaignDataService campaignDataService;
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
                           CallDataService callDataService, CampaignDataService campaignDataService,
                           MotechSchedulerService schedulerService) {
        this.settingsFacade = settingsFacade;
        this.eventRelay = eventRelay;
        this.messageCampaignService = messageCampaignService;
        this.outboundCallService = outboundCallService;
        this.callDataService = callDataService;
        this.campaignDataService = campaignDataService;
        this.schedulerService = schedulerService;
        setupData();
    }


    private List<String> readList(String what) {

        final String field = what;

        List<String> slots = (List<String>) callDataService.executeSQLQuery(new SqlQueryExecution<List<String>>() {
            @Override
            public List<String> execute(Query query) {
                return (List<String>) query.execute();
            }

            @Override
            public String getSqlQuery() {
                    return String.format("SELECT DISTINCT %s FROM KIL2_CALL", field);
            }
        });

        return slots;
    }


    private void setupData() {

        redisServer = settingsFacade.getProperty(REDIS_SERVER_PROPERTY);
        logger.info("redis server: {}", redisServer);

        rand = new Random();

        jedisPool = new JedisPool(new JedisPoolConfig(), redisServer);
        
        try {
            InetAddress ip = InetAddress.getLocalHost();
            hostName = ip.getHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException("Could not get instance host name: " + e.toString(), e);
        }

        reset();

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setnx(REDIS_CAMPAIGN_ID, "0");
        }
    }


    private void sendCallMessage(String recipientID, String phoneNumber) {
        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put("externalID", recipientID);
        eventParams.put("to", phoneNumber);
        MotechEvent event = new MotechEvent(CALL_EVENT, eventParams);
        eventRelay.sendEventMessage(event);
    }


    @MotechListener(subjects = { EventKeys.SEND_MESSAGE })
    public void handleCampaignEvent(MotechEvent event) {
        logger.info(event.toString());

        List<Call> calls;
        String externalID = (String)event.getParameters().get("ExternalID");
        long milliStart = System.currentTimeMillis();
        Campaign campaign = campaignDataService.findById(Long.parseLong(externalID));
        logger.info(String.format("Reading all recipients for %s/%s", campaign.getDay(), campaign.getSlot()));
        calls = callDataService.findByDaySlot(campaign.getDay(), campaign.getSlot());
        long millis = System.currentTimeMillis() - milliStart;
        float rate = (float) calls.size() * MILLIS_PER_SECOND / millis;
        logger.info(String.format("Read %d recipient%s in %dms (%s/sec)", calls.size(),
                calls.size() == 1 ? "" : "s", millis, rate));

        int count = 0;
        milliStart = System.currentTimeMillis();
        for(Call call : calls) {
            sendCallMessage(callDataService.getDetachedField(call, "id").toString(), call.getPhone());
            count++;
            if (count % 1000 == 0) {
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
        String externalID = (String)event.getParameters().get("externalID");
        String phoneNumber = (String)event.getParameters().get("to");
        call(externalID, phoneNumber);
        meetExpectation();
    }


    private String call(String externalID, String phoneNumber) {
        logger.debug("calling {}, {}", externalID, phoneNumber);

        Map<String, String> params = new HashMap<>();
        params.put("externalID", externalID);
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


    private void setExpectations(long count) {
        logger.info("Setting expectations to {}", count);

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(REDIS_EXPECTATIONS, String.valueOf(count));
            jedis.set(REDIS_EXPECTING, String.valueOf(count));
            logger.info("Expectations: {}/{}", jedis.get(REDIS_EXPECTING), jedis.get(REDIS_EXPECTATIONS));
        }
    }


    private String createCampaign(String day, String slot) {
        Campaign campaign = new Campaign(day, slot);
        campaignDataService.create(campaign);
        String id =campaignDataService.getDetachedField(campaign, "id").toString();
        logger.info("Created campaign {}: {}", id, campaign);
        return id;
    }


    public String createDaySlotCampaign(String dateOrPeriod, String day, String slot) {
        if (!dayList.contains(day)) {
            return String.format("%s is not a valid day. Valid days: %s", day, dayList);
        }
        if (!slotList.contains(slot)) {
            return String.format("%s is not a valid slot. Valid slots: %s", slot, slotList);
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

            CampaignRecord campaignRecord = new CampaignRecord();
            String campaignName = String.format("Campaign%s", jedis.incr(REDIS_CAMPAIGN_ID));
            campaignRecord.setName(campaignName);
            campaignRecord.setCampaignType(CampaignType.ABSOLUTE);

            //todo: create 9 months campaigns...
            CampaignMessageRecord firstMessage = new CampaignMessageRecord();
            firstMessage.setName("firstMessage");
            firstMessage.setDate(date);
            firstMessage.setStartTime(time);
            firstMessage.setMessageKey("first");

            campaignRecord.setMessages(Arrays.asList(firstMessage));
            messageCampaignService.saveCampaign(campaignRecord);


            long slotRecipientCount =  callDataService.countFindByDaySlot(day, slot);
            String msg = String.format("Enrolling %s with %d recipient%s in %s/%s @ %s %s", campaignName,
                    slotRecipientCount, slotRecipientCount == 1 ? "" : "s", slot, day, date.toString(), time);
            logger.info(msg);
            String externalID = createCampaign(day, slot);
            CampaignRequest campaignRequest = new CampaignRequest(externalID, campaignName, LocalDate.now(), null);
            messageCampaignService.enroll(campaignRequest);

            setExpectations(slotRecipientCount);
            return msg;
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
                reset();
            } else if (expecting % 1000 == 0) {
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

        int recipientCount = (int) callDataService.count();
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


    public String reset() {

        slotList = readList("slot");
        dayList = readList("day");
        if (slotList.size() == 0) {
            logger.error("No recipients in the database!!!");
        }
        logger.info("slot list: {}", slotList);
        logger.info("day list: {}", dayList);

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(REDIS_EXPECTING, "0");
            jedis.set(REDIS_EXPECTATIONS, "0");
            jedis.del(REDIS_TIMESTAMP);
            logger.info("Expectations: {}/{}", jedis.get(REDIS_EXPECTING), jedis.get(REDIS_EXPECTATIONS));
            return "OK";
        }
    }
}
