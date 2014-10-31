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

/**
 * Responds to HTTP queries to {motech-server}/module/mockil/enroll and enrolls an expecting mother
 */
@Controller
public class MockilController {

    private MockilService mockilService;

    @Autowired
    public MockilController(MockilService mockilService) {
        this.mockilService = mockilService;
    }

    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/create/{campaignName}/{delay}")
    public String create(@PathVariable String campaignName, @PathVariable String delay) {
        mockilService.create(campaignName, delay);
        return "OK";
    }

    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/delete/{campaignName}")
    public String delete(@PathVariable String campaignName) {
        mockilService.delete(campaignName);
        return "OK";
    }

    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/enroll/{campaignName}")
    public String enroll(@PathVariable String campaignName) {
        mockilService.enroll(campaignName);
        return "OK";
    }

    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/enroll/{campaignName}/{number}")
    public String enrollMany(@PathVariable String campaignName, @PathVariable int number) {
        mockilService.enrollMany(campaignName, number);
        return "OK";
    }

    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    @ResponseBody
    @ExceptionHandler(Exception.class)
    public String handleException(Exception e) {
        return e.getMessage();
    }
}
