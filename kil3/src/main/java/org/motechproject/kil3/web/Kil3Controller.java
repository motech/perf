package org.motechproject.kil3.web;

import org.motechproject.kil3.service.Kil3Service;
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
 * Responds to HTTP queries to {motech-server}/module/kil3/*** endpoints
 */
@Controller
public class Kil3Controller {

    private Kil3Service kil3Service;
    private int campaignNum;
    private int externalIdNum;
    private String hostName;
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private final RequestMappingHandlerMapping handlerMapping;

    private final static long MILLIS_PER_SECOND = 1000;



    @Autowired
    public Kil3Controller(Kil3Service kil3Service, RequestMappingHandlerMapping handlerMapping) {
        this.kil3Service = kil3Service;
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
     * /schedule-job/{day}/{slot}/{dateOrPeriod}
     *
     * {dateOrPeriod}: yyyy-mm-dd-hh-mm or 5minutes
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/schedule-job/{day}/{slot}/{dateOrPeriod}")
    public String scheduleJob(@PathVariable String dateOrPeriod, @PathVariable String day,
                                 @PathVariable String slot) {
        return kil3Service.scheduleJob(dateOrPeriod, day, slot);
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
        return kil3Service.getStatus();
    }



    /*
     * /delete-job
     *
     * removes job with given id
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/delete-job/{id}")
    public String deleteCampaigns(@PathVariable Long id) {
        return kil3Service.deleteJob(id);
    }



    /*
     * /list-jobs
     *
     * returns job list
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/list-jobs")
    public String deleteCampaigns() {
        return kil3Service.listJobs();
    }



    /*
     * /reload
     *
     * reloads day list & slot list from the database
     *
     * returns 'OK'
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/reload")
    public String reload() {
        return kil3Service.reload();
    }



    /*
     * /delete-all-jobs
     *
     * deletes & unschedules all jobs
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/delete-all-jobs")
    public String deleteAllJobs() {
        return kil3Service.deleteAllJobs();
    }



    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    @ResponseBody
    @ExceptionHandler(Exception.class)
    public String handleException(Exception e) {
        return String.format("### EXCEPTION ###: %s", e.toString());
    }
}
