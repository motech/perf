package org.motechproject.kil2.service;

import org.motechproject.event.MotechEvent;
import org.motechproject.event.listener.annotations.MotechListener;
import org.motechproject.kil2.database.Call;
import org.motechproject.kil2.database.CallDataService;
import org.motechproject.kil2.database.CallStage;
import org.motechproject.kil2.database.CallStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service("CDRService")
public class CDRService {

    private static final String IVR_CALL_STATUS = "ivr_call_status";
    private Logger logger = LoggerFactory.getLogger(CDRService.class);
    private Map<CallStatus, Integer> slotIncrements;
    private Map<CallStage, CallStage> nextStage;
    private CallDataService callDataService;

    @Autowired
    public CDRService(CallDataService callDataService) {
        this.callDataService = callDataService;

        slotIncrements = new HashMap<>();
        slotIncrements.put(CallStatus.NA, 2);
        slotIncrements.put(CallStatus.NA, 2);
        slotIncrements.put(CallStatus.NA, 2);
        slotIncrements.put(CallStatus.NA, 2);

        nextStage = new HashMap<>();
        nextStage.put(CallStage.FB, CallStage.R1);
        nextStage.put(CallStage.R1, CallStage.R2);
        nextStage.put(CallStage.R2, CallStage.R3);
        nextStage.put(CallStage.R3, CallStage.AB);
    }

    private String dayFromDaySlot(String daySlot) {
        return daySlot.substring(0, 1);
    }

    private String slotFromDaySlot(String daySlot) {
        return daySlot.substring(1, 2);
    }


    private String daySlot(String day, String slot) {
        return day+slot;
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
        int s = (Integer.valueOf(slot) + count) % 6;
        return String.format("%d", s);
    }

    private String nextDay(String day, String slot, CallStatus callStatus) {
        int count = callStatusSlotIncrement(callStatus);
        int s = Integer.valueOf(slot) + count;
        int d = (Integer.valueOf(day) + (s > 6 ? 1 : 0)) % 7;
        return String.format("%d", d);
    }

    private void processCallDetailRecord(String callID, CallStatus callStatus) {
        logger.debug("processCallDetailRecord(callID={}, callStatus={})", callID, callStatus);

        //todo: add something to callHistory

        Call call = callDataService.findById(Long.parseLong(callID));
        String day = call.getDay();
        String slot = call.getSlot();

        if (CallStatus.OK == callStatus) {
            if (call.getInitialDay().equals(day) && call.getInitialSlot().equals(slot)) {
                return;
            } else {
                call.setDay(call.getInitialDay());
                call.setSlot(call.getInitialSlot());
            }
        } else {
            switch (call.getCallStage()) {
                case FB:
                    call.setCallStage(CallStage.R1);
                    call.setDay(nextDay(day, slot, callStatus));
                    call.setSlot(nextSlot(slot, callStatus));
                    break;

                case R1:
                    call.setCallStage(CallStage.R2);
                    call.setDay(nextDay(day, slot, callStatus));
                    call.setSlot(nextSlot(slot, callStatus));
                    break;

                case R2:
                    call.setCallStage(CallStage.R3);
                    call.setDay(nextDay(day, slot, callStatus));
                    call.setSlot(nextSlot(slot, callStatus));
                    break;

                case R3:
                    call.setCallStage(CallStage.FB);
                    call.setDay(call.getInitialDay());
                    call.setSlot(call.getInitialSlot());
                    break;
            }
        }
        callDataService.update(call);
    }


    @MotechListener(subjects = { IVR_CALL_STATUS })
    public void handleStatusEvent(MotechEvent event) {
        logger.debug(event.toString());

        Object o = event.getParameters().get("provider_extra_data");
        if (o instanceof HashMap) {
            Map<String, String> providerExtraData = (Map<String, String>) o;
            String callID = providerExtraData.get("externalID");
            CallStatus callStatus = CallStatus.valueOf(event.getParameters().get("call_status").toString());
            processCallDetailRecord(callID, callStatus);
        }
    }
}
