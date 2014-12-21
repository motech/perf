package org.motechproject.kil2.web;

import org.motechproject.kil2.service.Kil2Service;
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
 * Responds to HTTP queries to {motech-server}/module/kil2/*** endpoints
 */
@Controller
public class Kil2Controller {

    private Kil2Service kil2Service;
    private int campaignNum;
    private int externalIdNum;
    private String hostName;
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private final RequestMappingHandlerMapping handlerMapping;

    private final static long MILLIS_PER_SECOND = 1000;


    @Autowired
    public Kil2Controller(Kil2Service kil2Service, RequestMappingHandlerMapping handlerMapping) {
        this.kil2Service = kil2Service;
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
     * /create-campaign/{slot}/{day}/{dateOrPeriod}
     *
     * {dateOrPeriod}: yyyy-mm-dd-hh-mm or 5minutes
     * {slot}: slot
     * {day}: day
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/create-campaign/{slot}/{day}/{dateOrPeriod}")
    public String createCampaign(@PathVariable String dateOrPeriod, @PathVariable String slot,
                                 @PathVariable String day) {
        return kil2Service.createCampaign(dateOrPeriod, slot, day);
    }



    /*
     * /status
     *
     *
     * returns overall status
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/status")
    public String getExpectations() {
        return kil2Service.getStatus();
    }


    /*
     * /delete-campaigns
     *
     * removes all campaigns & enrollments & clears schedule
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/delete-campaigns")
    public String deleteCampaigns() {
        return kil2Service.deleteCampaigns();
    }


    /*
     * /reset-expectations
     *
     * resets expectations to zero
     * returns 'OK'
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/reset")
    public String reset() {
        return kil2Service.reset();
    }


    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    @ResponseBody
    @ExceptionHandler(Exception.class)
    public String handleException(Exception e) {
        return String.format("### EXCEPTION ###: %s", e.toString());
    }
}