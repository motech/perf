package org.motechproject.mockil.web;

import org.motechproject.mockil.service.MockilService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;

/**
 * Responds to HTTP queries to {motech-server}/module/mockil/enroll and enrolls an expecting mother
 */
@Controller
public class MockilController {

    private MockilService mockilService;
    private int campaignNum;
    private int externalIdNum;
    private String hostName;
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private final RequestMappingHandlerMapping handlerMapping;

    private final static long MILLIS_PER_SECOND = 1000;


    @Autowired
    public MockilController(MockilService mockilService, RequestMappingHandlerMapping handlerMapping) {
        this.mockilService = mockilService;
        this.handlerMapping = handlerMapping;
    }


    /*
     *
     * displays all available commands
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/")
    public String showHelp() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry  : this.handlerMapping.getHandlerMethods().entrySet()) {
            sb.append(entry.getKey().getPatternsCondition().getPatterns().toArray()[0]);
            sb.append("\n");
        }
        return sb.toString();
    }



    /*
     * /create-offset/{period}
     *
     * {period}: 5minutes
     *
     * creates an offset campaign
     * returns new campaign name
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/create-offset/{period}")
    public String createOffset(@PathVariable String period) {
        return mockilService.createOffset(period);
    }


    /*
     * /create-enroll-offset/{period}
     *
     * {period}: 5minutes
     *
     * creates offset campaign
     * enrolls one enrollment
     * returns new campaign name & new enrollment external id
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/create-enroll-offset/{period}")
    public String createEnrollOffset(@PathVariable String period) {
        String campaignName = mockilService.createOffset(period);
        return mockilService.enroll(campaignName);
    }


    /*
     * /create-enroll-many-offset/{period}/{number}
     *
     * {period}: 5minutes
     * {number}: integer, number of enrollments
     *
     * creates offset campaign
     * enrolls {numbers} enrollment(s)
     * returns list of new enrollment external id(s)
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/create-enroll-many-offset/{period}")
    public String createEnrollManyOffset(@PathVariable String period, @PathVariable int number) {
        String campaignName = mockilService.createOffset(period);
        String enrollments = doEnrollMany(campaignName, number);
        return String.format("%s - %s", campaignName, enrollments);
    }


    /*
     * /create-absolute/{dateOrPeriod}
     *
     * {dateOrPeriod}: yyyy-mm-dd-hh-mm or 5minutes
     *
     * creates an absolute campaign
     * returns new campaign name
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/create-absolute/{dateOrPeriod}")
    public String createAbsolute(@PathVariable String dateOrPeriod) {
        return mockilService.createAbsolute(dateOrPeriod);
    }


    /*
     * /create-enroll-absolute/{dateOrPeriod}
     *
     * {dateOrPeriod}: yyyy-mm-dd-hh-mm or 5minutes
     *
     * creates an absolute campaign
     * enrolls one enrollment
     * returns new campaign name & new enrollment external id
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/create-enroll-absolute/{dateOrPeriod}")
    public String createEnrollAbsolute(@PathVariable String dateOrPeriod) {
        String campaignName = mockilService.createAbsolute(dateOrPeriod);
        return mockilService.enroll(campaignName);
    }


    /*
     * /create-enroll-many-absolute/{dateOrPeriod}/{number}
     *
     * {dateOrPeriod}: yyyy-mm-dd-hh-mm or 5minutes
     * {number}: integer, number of enrollments
     *
     * creates offset campaign
     * enrolls {numbers} enrollment(s)
     * returns list of new enrollment external id(s)
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/create-enroll-many-absolute/{dateOrPeriod}/{number}")
    public String createEnrollManyAbsolute(@PathVariable String dateOrPeriod, @PathVariable int number) {
        String campaignName = mockilService.createAbsolute(dateOrPeriod);
        String enrollments = doEnrollMany(campaignName, number);
        return String.format("%s - %s", campaignName, enrollments);
    }


    /*
     * /delete/{campaignName}
     *
     * {campaignName}: name
     *
     * deletes the given campaign (but the Motech code is buggy)
     * returns DELETED name
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/delete/{campaignName}")
    public String delete(@PathVariable String campaignName) {
        return mockilService.delete(campaignName);
    }


    /*
     * /enroll/{campaignName}
     *
     * {campaignName}: name
     *
     * enrolls one enrollment in {campaignName}
     * returns new enrollment external id
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/enroll/{campaignName}")
    public String enroll(@PathVariable String campaignName) {
        return mockilService.enroll(campaignName);
    }


    private String doEnrollMany(String campaignName, int number) {
        mockilService.setExpectations(number);
        long start = System.currentTimeMillis();
        String ret = "";

        if (number > 0) {
            ret += mockilService.enroll(campaignName);
            int i = 1;
            for (; i < number - 1; i++) {
                mockilService.enroll(campaignName);
            }
            if (i < number) {
                ret += " ... " + mockilService.enroll(campaignName);
            }
        }

        if (logger.isInfoEnabled()) {
            long millis = System.currentTimeMillis() - start;
            float rate = (float) number * MILLIS_PER_SECOND / millis;
            String plural = rate == 1 ? "" : "s";
            logger.info("{} enrollment{} / second", rate, plural);
        }

        return ret;
    }


    /*
     * /enroll/{campaignName}/{number}
     *
     * {campaignName}: name
     * {number}: integer, number of enrollments
     *
     * enrolls {number} enrollments in {campaignName}
     * returns new enrollment external ids
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/enroll/{campaignName}/{number}")
    public String enrollMany(@PathVariable String campaignName, @PathVariable int number) {
        return doEnrollMany(campaignName, number);
    }


    /*
     * /call
     *
     * initiates outbound call to random number
     * returns externalId
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/call")
    public String call() {
        return mockilService.makeOutboundCall();
    }


    /*
     * /expect/{number}
     *
     * {number}: integer, number of expected calls
     *
     * sets call expectations for {number} calls
     * returns {number}
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/expect/{number}")
    public String expect(@PathVariable int number) {
        return mockilService.setExpectations(number);
    }


    /*
     * /reset-expectations
     *
     *
     * sets call expectations to zero
     * returns OK
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/reset-expectations")
    public String expect() {
        return mockilService.resetExpectations();
    }


    /*
     * /expectations
     *
     *
     * returns current expectations
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/expectations")
    public String getExpectations() {
        return mockilService.getExpectations();
    }


    /*
     * /send-campaign-event
     *
     * sends a fired campaign message event with random external id
     * returns externalId
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/send-campaign-event")
    public String sendCampaignEvent() {
        return mockilService.sendCampaignEvent();
    }


    /*
     * /send-test-event
     *
     * sends a test event
     * returns OK
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/send-test-event")
    public String sendTestEvent() {
        return mockilService.sendTestEvent();
    }


    /*
     * /send-many-test-events/{number}
     *
     * sends a test event
     * returns OK
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/send-many-test-events/{number}")
    public String sendManyTestEvents(@PathVariable int number) {
        mockilService.setExpectations(number);
        long start = System.currentTimeMillis();

        for (int i=0 ; i < number; i++) {
            mockilService.sendTestEvent();
        }

        if (logger.isDebugEnabled()) {
            long millis = System.currentTimeMillis() - start;
            float rate = (float) number * MILLIS_PER_SECOND / millis;
            String plural = rate == 1 ? "" : "s";
            logger.info("{} enrollment{} / second", rate, plural);
        }

        return String.format("%d", number);
    }


    /*
     * /do-call
     *
     * will make calls
     * returns 'will call'
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/do-call")
    public String doCall() {
        return mockilService.doCall();
    }


    /*
     * /dont-call
     *
     * will not make calls
     * returns 'won't call'
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/dont-call")
    public String dontCall() {
        return mockilService.dontCall();
    }


    /*
     * /reset-all
     *
     * removes all campaigns & enrollments & clears schedule
     * returns 'OK'
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/reset-all")
    public String resetAll() {
        return mockilService.resetAll();
    }


    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    @ResponseBody
    @ExceptionHandler(Exception.class)
    public String handleException(Exception e) {
        return e.getMessage();
    }
}
