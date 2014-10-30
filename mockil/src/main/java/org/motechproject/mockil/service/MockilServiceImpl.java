package org.motechproject.mockil.service;

import org.motechproject.event.listener.EventRelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service("mockilService")
public class MockilServiceImpl implements MockilService {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private EventRelay eventRelay;

    @Autowired
    public MockilServiceImpl(EventRelay eventRelay) {
        this.eventRelay = eventRelay;
    }

    public void enroll(Map<String, String> params) {
        logger.debug("enroll({})", params.toString());
        if (params.containsKey("lmp")) {
            //enroll
        } else {
            throw new IllegalArgumentException("Missing last menstrual period (lmp) argument.");
        }

    }

}
