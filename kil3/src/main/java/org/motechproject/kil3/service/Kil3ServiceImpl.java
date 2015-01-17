package org.motechproject.kil3.service;

import com.google.common.base.Strings;
import org.motechproject.event.MotechEvent;
import org.motechproject.event.listener.EventRelay;
import org.motechproject.event.listener.annotations.MotechListener;
import org.motechproject.kil3.database.*;
import org.motechproject.mds.query.QueryParams;
import org.motechproject.mds.query.SqlQueryExecution;
import org.motechproject.scheduler.service.MotechSchedulerService;
import org.motechproject.server.config.SettingsFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.jdo.Query;
import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service("kil3Service")
public class Kil3ServiceImpl implements Kil3Service {

    private final static String CALL_DIRECTORY = "kil3.call_directory";
    private final static String CDR_DIRECTORY = "kil3.cdr_directory";
    private static final String PROCESS_CDR_FILE = "process_cdr_file";
    private static final String PROCESS_ONE_CDR = "process_one_cdr";
    private static final Integer MAX_RECIPIENT_BLOCK = 10000;


    private final static long MILLIS_PER_SECOND = 1000;

    private Logger logger = LoggerFactory.getLogger(Kil3ServiceImpl.class);
    private SettingsFacade settingsFacade;
    private EventRelay eventRelay;
    private ExpectationServiceImpl expectationService;
    private RecipientDataService recipientDataService;
    private CallHistoryDataService callHistoryDataService;
    private MotechSchedulerService schedulerService;
    private List<String> slotList = Arrays.asList("1", "2", "3", "4", "5", "6");
    private List<String> dayList = Arrays.asList("1", "2", "3", "4", "5", "6", "7");;



    @Autowired
    public Kil3ServiceImpl(@Qualifier("kil3Settings") SettingsFacade settingsFacade, EventRelay eventRelay,
                           ExpectationServiceImpl expectationService, RecipientDataService recipientDataService,
                           CallHistoryDataService callHistoryDataService, MotechSchedulerService schedulerService) {
        this.settingsFacade = settingsFacade;
        this.eventRelay = eventRelay;
        this.expectationService = expectationService;
        this.recipientDataService = recipientDataService;
        this.callHistoryDataService = callHistoryDataService;
        this.schedulerService = schedulerService;
    }


    private List<String> readList(String what) {
        final String field = what;

        List<String> slots = (List<String>) recipientDataService.executeSQLQuery(new SqlQueryExecution<List<String>>() {
            @Override
            public List<String> execute(Query query) {
                return (List<String>) query.execute();
            }

            @Override
            public String getSqlQuery() {
                return String.format("SELECT DISTINCT %s FROM KIL3_RECIPIENT", field);
            }
        });

        return slots;
    }


    private String callFileName(String day, String slot) {
        return String.format("%sday%sslot%s-calls.csv", settingsFacade.getProperty(CALL_DIRECTORY), day, slot);
    }


    private String cdrFileName(String day, String slot) {
        return String.format("%sday%sslot%s-cdrs.csv", settingsFacade.getProperty(CDR_DIRECTORY), day, slot);
    }


    private String callTempFileName(String day, String slot) {
        return String.format("%s~", callFileName(day, slot));
    }


    public String createCallFile(String day, String slot) {
        logger.info("createCallFile(day={}, slot={})", day, slot);

        if (!dayList.contains(day)) {
            return String.format("%s is not a valid day. Valid days: %s", day, dayList);
        }
        if (!slotList.contains(slot)) {
            return String.format("%s is not a valid slot. Valid slots: %s", slot, slotList);
        }

        String callFileName = callFileName(day, slot);
        String callTempFileName = callTempFileName(day, slot);

        long milliStart = System.currentTimeMillis();
        String ret;

        try (PrintWriter writer = new PrintWriter(callTempFileName, "UTF-8")) {
            int page = 1;
            int numBlockRecipients = 0;
            long numRecipients = 0;
            do {
                List<Recipient> recipients = recipientDataService.findByDaySlot(day, slot,
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
                    logger.info(String.format("Read %d %s @ %s/sec", numRecipients,
                            numRecipients == 1 ? "recipient" : "recipients", rate));
                }
            } while (numBlockRecipients > 0);

            long millis = System.currentTimeMillis() - milliStart;
            float rate = (float) numRecipients * MILLIS_PER_SECOND / millis;

            logger.info(String.format("Wrote %d %s to %s in %dms (%s/sec)", numRecipients,
                    numRecipients == 1 ? "call" : "calls", callTempFileName, millis, rate));
            ret = String.format("%s %d calls (%s/sec)", callFileName, numRecipients, rate);
        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            String error = String.format("Unable to create temp call file %s: %s", callTempFileName, e.getMessage());
            logger.error(error);
            return error;
        }

        File fOld = new File(callFileName);
        if (fOld.exists()) {
            logger.info("Deleting old file {}...", callFileName);
            fOld.delete();
        }
        logger.info("Renaming temp file {} to {}...", callTempFileName, callFileName);
        File fTmp = new File(callTempFileName);
        fTmp.renameTo(new File(callFileName));

        logger.info("Altering timestamp on {}...", callFileName);
        File fCall = new File(callFileName);
        fCall.setLastModified(fCall.lastModified()+1);

        return ret;
    }


    public String getRecipients() {
        StringBuilder sb = new StringBuilder();
        String sep = "";

        for (String day : dayList) {
            sb.append(sep);
            sb.append(String.format("Day %s:", day));
            for (String slot : slotList) {
                int recipientCount = (int) recipientDataService.countFindByDaySlot(day, slot);
                sb.append(String.format(" %8d", recipientCount));
            }
            if (sep.isEmpty()) {
                sep = "\n\r";
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


    private String nextSlot(String slot, CallStatus callStatus) {
        int count = callStatusSlotIncrement(callStatus);
        int s = (Integer.valueOf(slot) + count - 1) % 6;
        return String.format("%d", s + 1);
    }


    private String nextDay(String day, String slot, CallStatus callStatus) {
        int count = callStatusSlotIncrement(callStatus);
        int s = Integer.valueOf(slot) + count;
        int d = (Integer.valueOf(day) + (s > 6 ? 1 : 0)) % 7;
        return String.format("%d", d);
    }


    private void addCallHistory(Recipient recipient, CallStatus callStatus, RecipientStatus recipientStatus) {
        CallHistory recipientHistory = new CallHistory(recipient.getDay(), recipient.getSlot(), recipient.getCallStage(), recipient.getPhone(),
                recipient.getLanguage(), recipient.getExpectedDeliveryDate(), callStatus, recipientStatus);
        callHistoryDataService.create(recipientHistory);
    }


    @MotechListener(subjects = {PROCESS_ONE_CDR})
    public void processOneCDR(MotechEvent event) {
        logger.debug("processOneCDR(event={})", event.toString());

        String line = (String)event.getParameters().get("CDR");
        CallDetailRecord cdr = CallDetailRecord.fromString(line);
        logger.debug("Processing slotting for {}...", cdr);

        Recipient recipient = recipientDataService.findById(Long.parseLong(cdr.getRecipient()));
        String day = recipient.getDay();
        String slot = recipient.getSlot();
        CallStatus callStatus = cdr.getCallStatus();

        expectationService.meetExpectation("CDR");

        if (CallStatus.OK == callStatus) {
            if (recipient.getInitialDay().equals(day) && recipient.getInitialSlot().equals(slot)) {
                addCallHistory(recipient, callStatus, RecipientStatus.AC);
                return;
            } else {
                recipient.setDay(recipient.getInitialDay());
                recipient.setSlot(recipient.getInitialSlot());
            }
        } else {
            switch (recipient.getCallStage()) {
                case FB:
                    recipient.setCallStage(CallStage.R1);
                    recipient.setDay(nextDay(day, slot, callStatus));
                    recipient.setSlot(nextSlot(slot, callStatus));
                    break;

                case R1:
                    recipient.setCallStage(CallStage.R2);
                    recipient.setDay(nextDay(day, slot, callStatus));
                    recipient.setSlot(nextSlot(slot, callStatus));
                    break;

                case R2:
                    recipient.setCallStage(CallStage.R3);
                    recipient.setDay(nextDay(day, slot, callStatus));
                    recipient.setSlot(nextSlot(slot, callStatus));
                    break;

                case R3:
                    recipient.setCallStage(CallStage.FB);
                    recipient.setDay(recipient.getInitialDay());
                    recipient.setSlot(recipient.getInitialSlot());
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
        logger.info("processCDRFile(event={})", event.toString());

        String path = (String)event.getParameters().get("file");

        long milliStart = System.currentTimeMillis();

        try(BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            int lineCount = 0;
            int cdrCount = 0;
            while ((line = br.readLine()) != null) {
                try {
                    if (Strings.isNullOrEmpty(line)) {
                        logger.debug("{}({}): Skipping blank line", path, lineCount + 1);
                        continue;
                    }
                    Map<String, Object> eventParams = new HashMap<>();
                    CallDetailRecord.validate(line);
                    eventParams.put("CDR", line);
                    MotechEvent motechEvent = new MotechEvent(PROCESS_ONE_CDR, eventParams);
                    eventRelay.sendEventMessage(motechEvent);
                    cdrCount++;
                } catch (Exception e) {
                    logger.error("{}({}): invalid CDR format", path, lineCount+1);
                }
                lineCount++;
            }

            long millis = System.currentTimeMillis() - milliStart;
            float rate = (float) cdrCount * MILLIS_PER_SECOND / millis;

            logger.info(String.format("Read %d %s, dispatched %d %s (%s/sec)", lineCount,
                    lineCount == 1 ? "line" : "lines", cdrCount, cdrCount == 1 ? "cdr" : "cdrs", rate));
            expectationService.setExpectations("CDR", cdrCount);
        } catch (IOException e) {
            logger.error("Error while reading {}: {}", path, e.getMessage());
        }
    }


    public String processCallDetailRecords(String slot, String day) {
        logger.debug("processCallDetailRecords(day={}, slot={})", day, slot);
        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put("file", cdrFileName(day, slot));
        MotechEvent motechEvent = new MotechEvent(PROCESS_CDR_FILE, eventParams);
        eventRelay.sendEventMessage(motechEvent);
        return "OK";
    }

}
