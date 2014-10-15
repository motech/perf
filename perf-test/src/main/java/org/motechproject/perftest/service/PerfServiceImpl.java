package org.motechproject.perftest.service;

import org.motechproject.event.MotechEvent;
import org.motechproject.event.listener.EventRelay;
import org.motechproject.event.listener.annotations.MotechListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service("perfService")
public class PerfServiceImpl implements PerfService {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private EventRelay eventRelay;
    private final Set<String> eventNumbers = new HashSet<String>();
    private long eventCount = 0;

    private class PerfTimer {
        private String name;
        private long startTime;

        public PerfTimer(String name) {
            this.name = name;
            startTime = System.currentTimeMillis();
        }

        public String elapsedMillis() {
            return String.format("%s: %d", name, System.currentTimeMillis() - startTime);
        }
    }

    @Autowired
    public PerfServiceImpl(EventRelay eventRelay) {
        this.eventRelay = eventRelay;
    }

    public String doNothing() {
        logger.debug("/do-nothing");
        PerfTimer timer = new PerfTimer("do-nothing");

        // nothing

        return timer.elapsedMillis();
    }

    @MotechListener(subjects = { "perftest_send_event" })
    public void handleSendEvent(MotechEvent event) {
        logger.debug("Notifier.handleSendEvent()");
    }

    public String doSendEvent() {
        logger.debug("/send-event");
        PerfTimer timer = new PerfTimer("send-event");

        Map<String, Object> eventParams = new HashMap<String, Object>();
        eventParams.put("foo", "bar");
        MotechEvent event = new MotechEvent("perftest_send_event", eventParams);
        eventRelay.sendEventMessage(event);

        return timer.elapsedMillis();
    }

    @MotechListener(subjects = { "perftest_send_and_receive_event" })
    public void handleSendAndReceiveEvent(MotechEvent event) {
        logger.debug("Notifier.handleSendAndReceiveEvent()");
        String eventNumber = (String) event.getParameters().get("eventNumber");
        synchronized (eventNumbers) {
            eventNumbers.remove(eventNumber);
        }
    }

    public String doSendAndReceiveEvent() {
        logger.debug("/send-receive-event");
        PerfTimer timer = new PerfTimer("send-receive-event");
        String eventNumber;

        synchronized (eventNumbers) {
            eventCount++;
            eventNumber = String.format("%d", eventCount);
            eventNumbers.add(eventNumber);
            logger.debug("Added eventNumber " + eventNumber);
        }

        Map<String, Object> eventParams = new HashMap<String, Object>();
        eventParams.put("eventNumber", eventNumber);
        MotechEvent event = new MotechEvent("perftest_send_and_receive_event", eventParams);
        eventRelay.sendEventMessage(event);

        while (true) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            synchronized (eventNumbers) {
                if (!eventNumbers.contains(eventNumber)) {
                    break;
                }
            }
        }

        return timer.elapsedMillis();
    }
}
