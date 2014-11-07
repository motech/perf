package org.motechproject.mockil.web;

import org.motechproject.mockil.service.MockilService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Map;

/**
 * Responds to HTTP queries to {motech-server}/module/mockil/enroll and enrolls an expecting mother
 */
@Controller
public class MockilController {

    private MockilService mockilService;
    private int campaignNum;
    private int externalIdNum;


    @Autowired
    public MockilController(MockilService mockilService) {
        this.mockilService = mockilService;
        Map<String, Integer> maxIds = mockilService.getMaxIds();
        campaignNum = maxIds.get("maxCampaignId");
        externalIdNum = maxIds.get("maxExternalId");
    }


    private synchronized String getNextCampaignName() {
        return String.format("C%d", ++campaignNum);
    }


    private synchronized String getNextExternalId() {
        return String.format("E%d", ++externalIdNum);
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
        mockilService.enroll(campaignName, externalId);
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
        mockilService.enroll(campaignName, externalId);
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
        mockilService.enroll(campaignName, externalId);
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
        for (int i=0 ; i<number ; i++) {
            String externalId = getNextExternalId();
            mockilService.enroll(campaignName, externalId);
            ids += sep + externalId;
            if (sep.isEmpty()) {
                sep = ", ";
            }
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
