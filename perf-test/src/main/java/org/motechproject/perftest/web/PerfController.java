package org.motechproject.perftest.web;

import org.motechproject.perftest.service.PerfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Responds to HTTP queries to {motech-server}/module/perf-test/** and does corresponding stuff
 */
@Controller
public class PerfController {

    private PerfService perfService;

    @Autowired
    public PerfController(PerfService perfService) {
        this.perfService = perfService;
    }

    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/do-nothing")
    public String doNothing() {
        return perfService.doNothing();
    }

    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/send-event")
    public String sendEvent() {
        return perfService.doSendEvent();
    }

    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/send-and-receive-event")
    public String sendReceiveEvent() {
        return perfService.doSendAndReceiveEvent();
    }
}
