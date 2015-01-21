package org.motechproject.kil3.service;

import com.google.common.base.Strings;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.joda.time.DateTime;
import org.motechproject.event.MotechEvent;
import org.motechproject.event.listener.EventRelay;
import org.motechproject.event.listener.annotations.MotechListener;
import org.motechproject.kil3.database.*;
import org.motechproject.mds.query.QueryParams;
import org.motechproject.mds.query.SqlQueryExecution;
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
import java.io.*;
import java.net.URISyntaxException;
import java.util.*;


@Service("kil3Service")
public class Kil3ServiceImpl implements Kil3Service {

    private final static String CALL_DIRECTORY = "kil3.call_directory";
    private final static String CALL_SERVER_URL = "kil3.call_server_url";
    private final static String CDR_DIRECTORY = "kil3.cdr_directory";
    private static final String PROCESS_CDR_FILE = "process_cdr_file";
    private static final String PROCESS_ONE_CDR = "process_one_cdr";
    private static final Integer MAX_RECIPIENT_BLOCK = 10000;
    private final static String REDIS_SERVER_PROPERTY = "kil3.redis_server";
    private final static long MILLIS_PER_SECOND = 1000;

    private Logger LOGGER = LoggerFactory.getLogger(Kil3ServiceImpl.class);
    private SettingsFacade settingsFacade;
    private EventRelay eventRelay;
    private RecipientDataService recipientDataService;
    private CallHistoryDataService callHistoryDataService;
    private MotechSchedulerService schedulerService;
    private List<String> dayList = Arrays.asList("1", "2", "3", "4", "5", "6", "7");;
    private JedisPool jedisPool;



    @Autowired
    public Kil3ServiceImpl(@Qualifier("kil3Settings") SettingsFacade settingsFacade, EventRelay eventRelay,
                           RecipientDataService recipientDataService, CallHistoryDataService callHistoryDataService,
                           MotechSchedulerService schedulerService) {
        this.settingsFacade = settingsFacade;
        this.eventRelay = eventRelay;
        this.recipientDataService = recipientDataService;
        this.callHistoryDataService = callHistoryDataService;
        this.schedulerService = schedulerService;

        String redisServer = settingsFacade.getProperty(REDIS_SERVER_PROPERTY);
        LOGGER.info("redis server: {}", redisServer);
        jedisPool = new JedisPool(new JedisPoolConfig(), redisServer);
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
        LOGGER.info("setExpectations({}, {})", jobId, count);

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(redisJobExpectations(jobId), String.valueOf(count));
            jedis.set(redisJobExpecting(jobId), String.valueOf(count));
            jedis.del(redisJobTimer(jobId));
        }
    }


    private void meetExpectation(String jobId) {
        LOGGER.debug("meetExpectation({})", jobId);

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
                String expectationsString = jedis.get(redisJobExpectations(jobId));
                if (Strings.isNullOrEmpty(expectationsString)) {
                    LOGGER.warn("meetExpectation was called on a null redis key: {}", redisJobExpectations(jobId));
                } else {
                    long expectations = Long.valueOf(expectationsString);
                    float rate = (float) expectations * MILLIS_PER_SECOND / millis;
                    LOGGER.info("Measured {} calls at {} calls/second", expectations, rate);

                    jedis.del(redisJobExpectations(jobId));
                    jedis.del(redisJobExpecting(jobId));
                    jedis.del(redisJobTimer(jobId));
                }

            } else if (expecting % 1000 == 0) {

                long milliStop = redisTime(jedis);
                long milliStart = Long.valueOf(jedis.get(redisJobTimer(jobId)));
                long millis = milliStop - milliStart;
                long expectations = Long.valueOf(jedis.get(redisJobExpectations(jobId)));
                long count = expectations - expecting;
                float rate = (float) count * MILLIS_PER_SECOND / millis;
                LOGGER.info(String.format("Expectations: %d/%d @ %f/s", expecting, expectations, rate));

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
        return sb.toString();
    }

    
    private String callFileName(String day) {
        return String.format("%sday%s-calls.csv", settingsFacade.getProperty(CALL_DIRECTORY), day);
    }


    private String cdrFileName(String day) {
        return String.format("%sday%s-cdrs.csv", settingsFacade.getProperty(CDR_DIRECTORY), day);
    }


    private String callTempFileName(String day) {
        return String.format("%s~", callFileName(day));
    }


    private void sendCallHttpRequest(String day) {
        LOGGER.debug("sendCallHttpRequest(day={})", day);

        String uri = String.format("%s/call?day=%s", settingsFacade.getProperty(CALL_SERVER_URL), day);

        HttpUriRequest request;
        URIBuilder builder;
        try {
            builder = new URIBuilder(uri);
            builder.setParameter("day", day);
            request = new HttpGet(builder.build());
        } catch (URISyntaxException e) {
            String message = "Unexpected error creating a URI";
            LOGGER.warn(message);
            throw new IllegalStateException(message, e);
        }

        LOGGER.debug("Generated {}", request.toString());

        HttpResponse response;
        try {
            response = new DefaultHttpClient().execute(request);
        } catch (IOException e) {
            String message = String.format("Could not initiate call, unexpected exception: %s", e.toString());
            LOGGER.warn(message);
            throw new IllegalStateException(message, e);
        }
    }


    public String createCallFile(String day) {
        LOGGER.info("createCallFile(day={})", day);

        if (!dayList.contains(day)) {
            return String.format("%s is not a valid day. Valid days: %s", day, dayList);
        }

        String callFileName = callFileName(day);
        String callTempFileName = callTempFileName(day);

        long milliStart = System.currentTimeMillis();
        String ret;

        try (PrintWriter writer = new PrintWriter(callTempFileName, "UTF-8")) {
            int page = 1;
            int numBlockRecipients = 0;
            long numRecipients = 0;
            do {
                List<Recipient> recipients = recipientDataService.findByDay(day,
                        new QueryParams(page, MAX_RECIPIENT_BLOCK));
                numBlockRecipients = recipients.size();

                for (Recipient recipient : recipients) {
                    writer.print(recipientDataService.getDetachedField(recipient, "id"));
                    writer.print(",");
                    writer.print(recipient.getPhone());
                    writer.print(",");
                    writer.print(recipient.pregnancyWeek());
                    writer.print(",");
                    writer.println(recipient.getLanguage());
                }

                page++;
                numRecipients += numBlockRecipients;

                if (numBlockRecipients > 0) {
                    long millis = System.currentTimeMillis() - milliStart;
                    float rate = (float) numRecipients * MILLIS_PER_SECOND / millis;
                    LOGGER.info(String.format("Read %d %s @ %s/sec", numRecipients,
                            numRecipients == 1 ? "recipient" : "recipients", rate));
                }
            } while (numBlockRecipients > 0);

            long millis = System.currentTimeMillis() - milliStart;
            float rate = (float) numRecipients * MILLIS_PER_SECOND / millis;

            LOGGER.info(String.format("Wrote %d %s to %s in %dms (%s/sec)", numRecipients,
                    numRecipients == 1 ? "call" : "calls", callTempFileName, millis, rate));
            ret = String.format("%s %d calls (%s/sec)", callFileName, numRecipients, rate);
        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            String error = String.format("Unable to create temp call file %s: %s", callTempFileName, e.getMessage());
            LOGGER.error(error);
            return error;
        }

        File fOld = new File(callFileName);
        if (fOld.exists()) {
            LOGGER.info("Deleting old file {}...", callFileName);
            fOld.delete();
        }
        LOGGER.info("Renaming temp file {} to {}...", callTempFileName, callFileName);
        File fTmp = new File(callTempFileName);
        fTmp.renameTo(new File(callFileName));

        LOGGER.info("Altering timestamp on {}...", callFileName);
        File fCall = new File(callFileName);
        fCall.setLastModified(fCall.lastModified()+1);

        LOGGER.info("Informing the IVR system the call file is available...");
        sendCallHttpRequest(day);

        return ret;
    }


    public String getRecipients() {
        StringBuilder sb = new StringBuilder();
        String sep = "";

        for (String day : dayList) {
            sb.append(sep);
            sb.append(String.format("%8d", (int) recipientDataService.countFindByDay(day)));
            if (sep.isEmpty()) {
                sep = " ";
            }
        }

        return sb.toString();
    }


    private int callStatusSlotIncrement(CallStatus callStatus) {
        switch (callStatus) {
            case NA:
                return 2;
            case ND:
            case SO:
                return 4;
            default:
                throw new IllegalArgumentException();
        }
    }


    private void addCallHistory(Recipient recipient, CallStatus callStatus, RecipientStatus recipientStatus) {
        CallHistory recipientHistory = new CallHistory(recipient.getDay(), recipient.getCallStage(), recipient.getPhone(),
                recipient.getLanguage(), recipient.getExpectedDeliveryDate(), callStatus, recipientStatus);
        callHistoryDataService.create(recipientHistory);
    }


    @MotechListener(subjects = {PROCESS_ONE_CDR})
    public void processOneCDR(MotechEvent event) {
        LOGGER.debug("processOneCDR(event={})", event.toString());

        String line = (String)event.getParameters().get("CDR");
        CallDetailRecord cdr = CallDetailRecord.fromString(line);
        LOGGER.debug("Processing slotting for {}...", cdr);

        Recipient recipient = recipientDataService.findById(Long.parseLong(cdr.getRecipient()));
        String day = recipient.getDay();
        CallStatus callStatus = cdr.getCallStatus();

        meetExpectation("CDR");

        if (CallStatus.OK == callStatus) {
            if (recipient.getInitialDay().equals(day)) {
                addCallHistory(recipient, callStatus, RecipientStatus.AC);
                return;
            } else {
                recipient.setDay(recipient.getInitialDay());
            }
        } else {
            switch (recipient.getCallStage()) {
                case FB:
                case R1:
                case R2:
                    recipient.setCallStage(CallStage.R1);
                    recipient.incDay();
                    break;

                case R3:
                    recipient.setCallStage(CallStage.FB);
                    recipient.setDay(recipient.getInitialDay());
                    break;
            }
        }
        recipientDataService.update(recipient);
        addCallHistory(recipient, callStatus, RecipientStatus.AC);
    }


    //
    // This is simplistic. The real system should periodically deal with 'orphan' calls which were somehow not
    // included in a CDR file and must be reslotted for their next week..
    //
    @MotechListener(subjects = { PROCESS_CDR_FILE })
    public void processCDRFile(MotechEvent event) {
        LOGGER.info("processCDRFile(event={})", event.toString());

        String path = (String)event.getParameters().get("file");

        long milliStart = System.currentTimeMillis();
        List<String> cdrs = new ArrayList<>();

        try(BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            int lineCount = 0;
            while ((line = br.readLine()) != null) {
                try {
                    if (Strings.isNullOrEmpty(line)) {
                        LOGGER.debug("{}({}): Skipping blank line", path, lineCount + 1);
                        continue;
                    }
                    Map<String, Object> eventParams = new HashMap<>();
                    CallDetailRecord.validate(line);
                    cdrs.add(line);
                } catch (Exception e) {
                    LOGGER.error("{}({}): invalid CDR format", path, lineCount + 1);
                }
                lineCount++;
            }

            long millis = System.currentTimeMillis() - milliStart;
            float rate = (float) lineCount * MILLIS_PER_SECOND / millis;

            LOGGER.info(String.format("Read %d %s in %ss @ (%s/sec)", lineCount, lineCount == 1 ? "line" : "lines",
                    millis / 1000, rate));
        } catch (IOException e) {
            LOGGER.error("Error while reading {}: {}", path, e.getMessage());
        }

        setExpectations("CDR", cdrs.size());

        milliStart = System.currentTimeMillis();
        int cdrCount = 0;
        for (String line : cdrs) {
            Map<String, Object> eventParams = new HashMap<>();
            eventParams.put("CDR", line);
            MotechEvent motechEvent = new MotechEvent(PROCESS_ONE_CDR, eventParams);
            eventRelay.sendEventMessage(motechEvent);
            //processOneCDR(motechEvent);
            cdrCount++;
            if (cdrCount % 10000 == 0) {
                long millis = System.currentTimeMillis() - milliStart;
                float rate = (float) cdrCount * MILLIS_PER_SECOND / millis;
                LOGGER.info(String.format("Queued %d cdrs for processing in %ss @ (%s/sec)", cdrCount, millis / 1000,
                        rate));
            }
        }
        long millis = System.currentTimeMillis() - milliStart;
        float rate = (float) cdrs.size() * MILLIS_PER_SECOND / millis;
        if (cdrCount % 10000 != 0) {
            LOGGER.info(String.format("Queued %d %s for processing in %ss @ (%s/sec)", cdrs.size(),
                    cdrs.size() == 1 ? "cdr" : "cdrs", millis / 1000, rate));
        }
    }


    public String processCallDetailRecords(String day) {
        LOGGER.debug("processCallDetailRecords(day={})", day);
        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put("file", cdrFileName(day));
        MotechEvent motechEvent = new MotechEvent(PROCESS_CDR_FILE, eventParams);
        schedulerService.safeScheduleRunOnceJob(new RunOnceSchedulableJob(motechEvent,
                DateTime.now().plusSeconds(1).toDate()));
        //processCDRFile(motechEvent);
        return "OK";
    }

}
