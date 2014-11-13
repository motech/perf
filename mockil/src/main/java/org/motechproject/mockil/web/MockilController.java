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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Random;

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
    private Random rand = new Random();

    private final static long MILLIS_PER_SECOND = 1000;


    @Autowired
    public MockilController(MockilService mockilService) {
        this.mockilService = mockilService;
        Map<String, Integer> maxIds = mockilService.getMaxIds();
        campaignNum = maxIds.get("maxCampaignId");
        externalIdNum = maxIds.get("maxExternalId");
        try {
            InetAddress ip = InetAddress.getLocalHost();
            hostName = ip.getHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException("Could not get instance host name: " + e.toString(), e);
        }
    }


    private synchronized String getNextCampaignName() {
        return String.format("%s-C%d", hostName, ++campaignNum);
    }


    private synchronized String getNextExternalId() {
        return String.format("%s-E%d", hostName, ++externalIdNum);
    }


    private String randomPhoneNumber() {
        return String.format("%03d %03d %04d", rand.nextInt(1000), rand.nextInt(1000), rand.nextInt(10000));
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
        String campaignName = getNextCampaignName();
        mockilService.createOffset(campaignName, period);
        return campaignName;
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
        String campaignName = getNextCampaignName();
        mockilService.createOffset(campaignName, period);
        String externalId = getNextExternalId();
        mockilService.enroll(campaignName, externalId, randomPhoneNumber());
        return String.format("%s, %s", campaignName, externalId);
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
        String campaignName = getNextCampaignName();
        mockilService.createAbsolute(campaignName, dateOrPeriod);
        return campaignName;
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
        String campaignName = getNextCampaignName();
        mockilService.createAbsolute(campaignName, dateOrPeriod);
        String externalId = getNextExternalId();
        mockilService.enroll(campaignName, externalId, randomPhoneNumber());
        return String.format("%s, %s", campaignName, externalId);
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
        mockilService.delete(campaignName);
        return String.format("DELETED %s", campaignName);
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
        String externalId = getNextExternalId();
        mockilService.enroll(campaignName, externalId, randomPhoneNumber());
        return externalId;
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
        String ids = "";
        String sep = "";
        long start = System.currentTimeMillis();
        for (int i=0 ; i<number ; i++) {
            String externalId = getNextExternalId();
            mockilService.enroll(campaignName, externalId, randomPhoneNumber());
            ids += sep + externalId;
            if (sep.isEmpty()) {
                sep = ", ";
            }
        }
        if (logger.isDebugEnabled()) {
            long millis = System.currentTimeMillis() - start;
            float rate = (float) number * MILLIS_PER_SECOND / millis;
            String plural = rate == 1 ? "" : "s";
            logger.debug("{} enrollment{} / second", rate, plural);
        }
        return ids;
    }


    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    @ResponseBody
    @ExceptionHandler(Exception.class)
    public String handleException(Exception e) {
        return e.getMessage();
    }
}
