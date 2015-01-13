package org.motechproject.kil3.service;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.motechproject.commons.date.util.JodaFormatter;
import org.motechproject.event.MotechEvent;
import org.motechproject.event.listener.EventRelay;
import org.motechproject.event.listener.annotations.MotechListener;
import org.motechproject.ivr.service.OutboundCallService;
import org.motechproject.kil3.database.*;
import org.motechproject.mds.query.QueryParams;
import org.motechproject.mds.query.SqlQueryExecution;
import org.motechproject.scheduler.contract.JobBasicInfo;
import org.motechproject.scheduler.contract.JobId;
import org.motechproject.scheduler.contract.RunOnceJobId;
import org.motechproject.scheduler.contract.RunOnceSchedulableJob;
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
import java.util.*;

import static org.motechproject.commons.date.util.DateUtil.newDateTime;


@Service("kil3Service")
public class Kil3ServiceImpl implements Kil3Service {

    private final static String REDIS_SERVER_PROPERTY = "kil3.redis_server";

    private static final String JOB_EVENT = "org.motechproject.kil3.job";
    private static final String CALL_EVENT = "org.motechproject.kil3.call";
    private static final Integer MAX_CALL_BLOCK = 10000;

    private final static long MILLIS_PER_SECOND = 1000;
    private static final String DATE_TIME_REGEX = "([0-9][0-9][0-9][0-9])([^0-9])([0-9][0-9])([^0-9])([0-9][0-9])" +
            "([^0-9])([0-9][0-9])([^0-9])([0-9][0-9])";
    private static final String DURATION_REGEX = "([0-9]*)([a-zA-Z]*)";

    private final static String REDIS_JOB_ID = "job_id";
    private Logger logger = LoggerFactory.getLogger(Kil3ServiceImpl.class);
    SettingsFacade settingsFacade;
    private EventRelay eventRelay;
    private OutboundCallService outboundCallService;
    private CallDataService callDataService;
    private MotechSchedulerService schedulerService;
    private String redisServer;
    private List<String> slotList;
    private List<String> dayList;
    private String hostName;
    private Random rand;
    JedisPool jedisPool;


    @Autowired
    public Kil3ServiceImpl(@Qualifier("kil3Settings") SettingsFacade settingsFacade, EventRelay eventRelay,
                           OutboundCallService outboundCallService,
                           CallDataService callDataService, MotechSchedulerService schedulerService) {
        this.settingsFacade = settingsFacade;
        this.eventRelay = eventRelay;
        this.outboundCallService = outboundCallService;
        this.callDataService = callDataService;
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
                    return String.format("SELECT DISTINCT %s FROM KIL3_CALL", field);
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

        reload();

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setnx(REDIS_JOB_ID, "0");
        }
    }


    private void sendCallMessage(String jobId, String recipientID, String phoneNumber) {
        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put("jobId", jobId);
        eventParams.put("externalID", recipientID);
        eventParams.put("to", phoneNumber);
        MotechEvent event = new MotechEvent(CALL_EVENT, eventParams);
        eventRelay.sendEventMessage(event);
    }


    @MotechListener(subjects = { JOB_EVENT })
    public void handleJobEvent(MotechEvent event) {
        logger.info(event.toString());

        String jobId = (String)event.getParameters().get("JobID");
        String day = (String)event.getParameters().get("day");
        String slot = (String)event.getParameters().get("slot");

        long milliStart = System.currentTimeMillis();

        int expectedNumCalls = (int)callDataService.countFindByDaySlot(day, slot);
        setExpectations(jobId, (long) expectedNumCalls);

        int page = 1;
        int numBlockCalls = 0;
        long numCalls = 0;
        do {
            List<Call> calls = callDataService.findByDaySlot(day, slot, new QueryParams(page, MAX_CALL_BLOCK));
            numBlockCalls = calls.size();

            for (Call call : calls) {
                sendCallMessage(jobId, callDataService.getDetachedField(call, "id").toString(), call.getPhone());
            }

            page++;
            numCalls += numBlockCalls;

            if (numBlockCalls > 0) {
                logger.info(String.format("Read %d recipient%s", numCalls, numCalls == 1 ? "" : "s"));
            }
        } while (numBlockCalls > 0);

        long millis = System.currentTimeMillis() - milliStart;
        float rate = (float) numCalls * MILLIS_PER_SECOND / millis;

        logger.info(String.format("%d message%s in %dms (%s/sec)", numCalls, numCalls == 1 ? "" : "s", millis, rate));
    }


    @MotechListener(subjects = { CALL_EVENT })
    public void handleCallEvent(MotechEvent event) {
        logger.debug(event.toString());
        String externalID = (String)event.getParameters().get("externalID");
        String phoneNumber = (String)event.getParameters().get("to");
        String jobId = (String)event.getParameters().get("jobId");
        call(externalID, phoneNumber);
        meetExpectation(jobId);
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


    private DateTime extractDateTime(String datetime) {
        int h = Integer.valueOf(datetime.replaceAll(DATE_TIME_REGEX, "$7"));
        int m = Integer.valueOf(datetime.replaceAll(DATE_TIME_REGEX, "$9"));

        return newDateTime(extractDate(datetime), h, m, 0);
    }


    private String fixPeriod(String delay) {
        String s = delay.replaceAll("[^a-zA-Z0-9]", " ");
        s = s.replaceAll("([0-9]*)([a-z]*)", "$1 $2");
        return s;
    }


    public String scheduleJob(String dateOrPeriod, String day, String slot) {
        if (!dayList.contains(day)) {
            return String.format("%s is not a valid day. Valid days: %s", day, dayList);
        }
        if (!slotList.contains(slot)) {
            return String.format("%s is not a valid slot. Valid slots: %s", slot, slotList);
        }

        DateTime dt;
        if (dateOrPeriod.matches(DATE_TIME_REGEX)) {

            dt = extractDateTime(dateOrPeriod);
        }
        else if (dateOrPeriod.matches(DURATION_REGEX)) {
            Period period = new JodaFormatter().parsePeriod(fixPeriod(dateOrPeriod));
            dt = DateTime.now().plus(period.toPeriod());
        }
        else {
            throw new IllegalStateException(String.format("%s seems to be neither a datetime or a duration.",
                    dateOrPeriod));
        }

        long slotRecipientCount =  callDataService.countFindByDaySlot(day, slot);

        if (slotRecipientCount > 0) {

            try (Jedis jedis = jedisPool.getResource()) {

                long jobId = jedis.incr(REDIS_JOB_ID);

                Map<String, Object> params = new HashMap<>();
                params.put("JobID", String.valueOf(jobId));
                params.put("day", day);
                params.put("slot", slot);

                MotechEvent motechEvent = new MotechEvent(JOB_EVENT, params);

                RunOnceSchedulableJob runOnceSchedulableJob = new RunOnceSchedulableJob(motechEvent, dt.toDate());

                JobId jobid = new RunOnceJobId(motechEvent);
                logger.info(String.format("%s: day %s slot %s, %d recipients", jobid.toString(), day, slot, slotRecipientCount));

                schedulerService.scheduleRunOnceJob(runOnceSchedulableJob);

                return dt.toDate().toString();
            }
        } else {
            return "No recipients!";
        }
    }


    private static String redisJobExpectations(String jobId) {
        return String.format("%s-expectations", jobId);
    }


    private static String redisJobExpecting(String jobId) {
        return String.format("%s-expecting", jobId);
    }


    private static String redisJobTimer(String jobId) {
        return String.format("%s-timer", jobId);
    }


    private static long redisTime(Jedis jedis) {
        List<String> t = jedis.time();
        return Long.valueOf(t.get(0)) * 1000 + Long.valueOf(t.get(1)) / 1000;
    }


    private void setExpectations(String jobId, long count) {
        logger.info("setExpectations({}, {})", jobId, count);

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(redisJobExpectations(jobId), String.valueOf(count));
            jedis.set(redisJobExpecting(jobId), String.valueOf(count));
            jedis.del(redisJobTimer(jobId));
        }
    }



    private void deleteRedisJob(Jedis jedis, String jobId) {
        jedis.del(redisJobExpectations(jobId));
        jedis.del(redisJobExpecting(jobId));
        jedis.del(redisJobTimer(jobId));
    }



    private void meetExpectation(String jobId) {
        logger.debug("meetExpectation({})", jobId);

        try (Jedis jedis = jedisPool.getResource()) {

            // Start timer if not already started
            if (!jedis.exists(redisJobTimer(jobId))) {
                List<String> t = jedis.time();
                jedis.setnx(redisJobTimer(jobId), String.valueOf(redisTime(jedis)));
            }

            long expecting = jedis.decr(redisJobExpecting(jobId));

            // All expectations met
            if (expecting <= 0) {
                List<String> t = jedis.time();
                long milliStop = redisTime(jedis);
                long milliStart = Long.valueOf(jedis.get(redisJobTimer(jobId)));
                long millis = milliStop - milliStart;
                long expectations = Long.valueOf(jedis.get(redisJobExpectations(jobId)));
                float rate = (float) expectations * MILLIS_PER_SECOND / millis;
                logger.info("Measured {} calls at {} calls/second", expectations, rate);

                deleteRedisJob(jedis, jobId);

            } else if (expecting % 1000 == 0) {
                long milliStop = redisTime(jedis);
                long milliStart = Long.valueOf(jedis.get(redisJobTimer(jobId)));
                long millis = milliStop - milliStart;
                long expectations = Long.valueOf(jedis.get(redisJobExpectations(jobId)));
                long count = expectations - expecting;
                float rate = (float) count * MILLIS_PER_SECOND / millis;
                logger.info(String.format("Expectations: %d/%d @ %f/s", expecting, expectations, rate));
            }
        }
    }



    public String getStatus() {
        StringBuilder sb = new StringBuilder();
        String sep = "";
        try (Jedis jedis = jedisPool.getResource()) {
            for (String expectationsKey : jedis.keys("*-expectations")) {
                String jobId = expectationsKey.substring(0, expectationsKey.length() - "-expectations".length());
                sb.append(sep);
                sb.append(String.format("%s: %s/%s", jobId, jedis.get(redisJobExpectations(jobId)),
                        jedis.get(redisJobExpecting(jobId))));
                if (sep.isEmpty()) {
                    sep = "\n\r";
                }
            }
        }

        for (String day : dayList) {
            sb.append(sep);
            sb.append(String.format("Day %s:", day));
            for (String slot : slotList) {
                int recipientCount = (int) callDataService.countFindByDaySlot(day, slot);
                sb.append(String.format(" %8d", recipientCount));
            }
            if (sep.isEmpty()) {
                sep = "\n\r";
            }
        }

        return sb.toString();
    }



    public String deleteJob(long id) {
        logger.info("deleteJob({})", id);

        JobId jobId = new RunOnceJobId(JOB_EVENT, String.valueOf(id));
        schedulerService.unscheduleJob(jobId);

        try (Jedis jedis = jedisPool.getResource()) {
            deleteRedisJob(jedis, jobId.toString());
        }

        return jobId.toString();
    }



    public String deleteAllJobs() {
        logger.info("deleteAllJobs()");

        logger.info("deleting all redis job data");
        try (Jedis jedis = jedisPool.getResource()) {
            for (String key : jedis.keys(String.format("%s*", JOB_EVENT))) {
                jedis.del(key);
            }
        }

        logger.info("deleting all scheduler jobs");
        schedulerService.safeUnscheduleAllJobs(JOB_EVENT);

        return "OK";
    }



    public String reload() {
        logger.info("reload()");

        slotList = readList("slot");
        dayList = readList("day");
        if (slotList.size() == 0) {
            logger.error("No recipients in the database!!!");
        }
        logger.info("slot list: {}", slotList);
        logger.info("day list: {}", dayList);

        return "OK";
    }



    public String listJobs() {
        logger.info("listJobs()");
        StringBuilder sb = new StringBuilder("redis: [");
        String sep = "";
        try (Jedis jedis = jedisPool.getResource()) {
            for (String key : jedis.keys(String.format("%s*", JOB_EVENT))) {
                if (key.endsWith("-expectations")) {
                    sb.append(sep);
                    sb.append(key.substring(0, key.length()-"-expectations".length()));
                    if (sep.isEmpty()) {
                        sep = ",";
                    }
                }
            }
        }
        sb.append("]\r\nscheduler: [");
        sep = "";
        for (JobBasicInfo info : schedulerService.getScheduledJobsBasicInfo()) {
            sb.append(sep);
            sb.append(info.getName());
            if (sep.isEmpty()) {
                sep = ",";
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
