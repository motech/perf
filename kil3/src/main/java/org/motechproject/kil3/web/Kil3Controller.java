package org.motechproject.kil3.web;

import org.motechproject.kil3.service.Kil3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
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
     * /call/{day}
     *
     * Creates the call file for the given day
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/call/{day}")
    public String createCallFile(@PathVariable String day) {
        return kil3Service.createCallFile(day);
    }



    /*
     * /recipients
     *
     *
     * returns recipient counts
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/recipients")
    public String getRecipients() {
        return kil3Service.getRecipients();
    }



    /*
     * /cdr/{day}
     *
     * download and process call detail records for given day
     *
     * returns 'OK'
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/cdr/{day}")
    public String process(@PathVariable String day) {
        return kil3Service.processCallDetailRecords(day);
    }



    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    @ResponseBody
    @ExceptionHandler(Exception.class)
    public String handleException(Exception e) {
        return String.format("### EXCEPTION ###: %s", e.toString());
    }
}
