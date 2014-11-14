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

    private final static long MILLIS_PER_SECOND = 1000;


    @Autowired
    public MockilController(MockilService mockilService) {
        this.mockilService = mockilService;
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
        return doEnrollMany(campaignName, number);
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
        return doEnrollMany(campaignName, number);
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
        long start = System.currentTimeMillis();
        StringBuilder ids = new StringBuilder("");
        if (number > 0) {
            ids.append(mockilService.enroll(campaignName));

            for (int i = 1; i < number; i++) {
                ids.append(",");
                ids.append(mockilService.enroll(campaignName));
            }
        }

        if (logger.isDebugEnabled()) {
            long millis = System.currentTimeMillis() - start;
            float rate = (float) number * MILLIS_PER_SECOND / millis;
            String plural = rate == 1 ? "" : "s";
            logger.debug("{} enrollment{} / second", rate, plural);
        }

        return ids.toString();
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
     * /call/{externalId}
     *
     * {externalId}: uniquely identifies who to call
     *
     * initiates outbound call
     * returns externalId
     *
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/call/{externalId}")
    public String call(@PathVariable String externalId) {
        return mockilService.makeOutboundCall(externalId);
    }


    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    @ResponseBody
    @ExceptionHandler(Exception.class)
    public String handleException(Exception e) {
        return e.getMessage();
    }
}
