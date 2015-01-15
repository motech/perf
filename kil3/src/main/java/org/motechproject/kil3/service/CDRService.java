package org.motechproject.kil3.service;

import com.google.common.base.Strings;
import org.motechproject.event.MotechEvent;
import org.motechproject.event.listener.EventRelay;
import org.motechproject.event.listener.annotations.MotechListener;
import org.motechproject.kil3.database.*;
import org.motechproject.server.config.SettingsFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;


@Service("CDRService")
public class CDRService {

    private final static String CDR_DIRECTORY = "kil3.cdr_directory";
    private static final String IVR_CALL_STATUS = "ivr_recipient_status";
    private static final String CDR_FILE_MODIFIED = "cdr_file_modified";
    private static final String CDR_PROCESS_SLOTTING = "cdr_process_slotting";
    private Logger logger = LoggerFactory.getLogger(CDRService.class);
    private Map<CallStatus, Integer> slotIncrements;
    private Map<CallStage, CallStage> nextStage;
    private RecipientDataService recipientDataService;
    private CallHistoryDataService recipientHistoryDataService;
    SettingsFacade settingsFacade;
    private EventRelay eventRelay;



    class DirWatcherThread extends Thread {
        Path path;
        WatchService watchService;
        WatchKey watchKey;

        DirWatcherThread() {
            String cdrDir = settingsFacade.getProperty(CDR_DIRECTORY);
            path = Paths.get(cdrDir);
            try {
                watchService = FileSystems.getDefault().newWatchService();
                watchKey = path.register(watchService, ENTRY_MODIFY);
            } catch (IOException e) {
                logger.error("Unable to watch {} directory: {}", cdrDir, e.getMessage());
            }
        }

        public void run() {
            logger.info("DirWatcherThread.run()");
            while (true) {
                try {
                    watchKey = this.watchService.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                for (WatchEvent<?> event : watchKey.pollEvents()) {
                    WatchEvent<Path> watchEvent = (WatchEvent<Path>) event;
                    String file = path.resolve(watchEvent.context()).toString();
                    logger.info("{} {}", event.kind().name(), file);

                    Map<String, Object> eventParams = new HashMap<>();
                    eventParams.put("path", file);
                    MotechEvent motechEvent = new MotechEvent(CDR_FILE_MODIFIED, eventParams);
                    eventRelay.sendEventMessage(motechEvent);

                    watchKey.reset();
                }
            }
        }
    }


    private void setupDirWatching() throws IOException {
        new Thread(new DirWatcherThread()).start();
    }


    @Autowired
    public CDRService(@Qualifier("kil3Settings") SettingsFacade settingsFacade, EventRelay eventRelay,
                      RecipientDataService recipientDataService, CallHistoryDataService recipientHistoryDataService)
            throws IOException {
        this.settingsFacade = settingsFacade;
        this.eventRelay = eventRelay;
        this.recipientDataService = recipientDataService;
        this.recipientHistoryDataService = recipientHistoryDataService;
        setupDirWatching();
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
        recipientHistoryDataService.create(recipientHistory);
    }


    private void processCallDetailRecord(String recipientID, CallStatus callStatus) {
        logger.debug("processCallDetailRecord(recipientID={}, callStatus={})", recipientID, callStatus);

        Recipient recipient = recipientDataService.findById(Long.parseLong(recipientID));

        String day = recipient.getDay();
        String slot = recipient.getSlot();

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


    @MotechListener(subjects = { IVR_CALL_STATUS })
    public void handleStatusEvent(MotechEvent event) {
        logger.debug("handleStatusEvent(event={})", event.toString());

        Object o = event.getParameters().get("provider_extra_data");
        if (o instanceof HashMap) {
            Map<String, String> providerExtraData = (Map<String, String>) o;
            String recipientID = providerExtraData.get("externalID");
            CallStatus callStatus = CallStatus.valueOf(event.getParameters().get("recipient_status").toString());
            processCallDetailRecord(recipientID, callStatus);
        }
    }


    @MotechListener(subjects = {CDR_PROCESS_SLOTTING})
    public void processSlotting(MotechEvent event) {
        logger.debug("processSlotting(event={})", event.toString());

        String line = (String)event.getParameters().get("CDR");
        CallDetailRecord cdr = CallDetailRecord.fromString(line);
        logger.debug("Processing slotting for {}...", cdr);

        Recipient recipient = recipientDataService.findById(Long.parseLong(cdr.getRecipient()));
        logger.debug("Recipient before: {}", recipient);
        String day = recipient.getDay();
        String slot = recipient.getSlot();
        CallStatus callStatus = cdr.getCallStatus();

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
        logger.debug("Recipient after: {}", recipient);
        addCallHistory(recipient, callStatus, RecipientStatus.AC);
    }


    @MotechListener(subjects = { CDR_FILE_MODIFIED })
    public void handleFileEvent(MotechEvent event) {
        logger.info("handleFileEvent(event={})", event.toString());

        String path = (String)event.getParameters().get("path");

        try(BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            int lineCount = 0;
            while ((line = br.readLine()) != null) {
                try {
                    if (Strings.isNullOrEmpty(line)) {
                        logger.debug("{}({}): Skipping blank line", path, lineCount + 1);
                        continue;
                    }
                    Map<String, Object> eventParams = new HashMap<>();
                    CallDetailRecord.validate(line);
                    eventParams.put("CDR", line);
                    MotechEvent motechEvent = new MotechEvent(CDR_PROCESS_SLOTTING, eventParams);
                    eventRelay.sendEventMessage(motechEvent);
                } catch (Exception e) {
                    logger.error("{}({}): invalid CDR format", path, lineCount+1);
                }
                lineCount++;
            }
            logger.info("Read {} {}", lineCount, lineCount == 1 ? "line" : "lines");
        } catch (IOException e) {
            logger.error("Error while reading {}: {}", path, e.getMessage());
        }
    }
}
