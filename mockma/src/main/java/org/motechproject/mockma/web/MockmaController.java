package org.motechproject.mockma.web;

import org.motechproject.mockma.service.MockmaService;
import org.motechproject.mtraining.domain.Bookmark;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Controller for HelloWorld message and bundle status.
 */
@Controller
public class MockmaController {

    @Autowired
    private MockmaService mockmaService;

    private static final String OK = "OK";

    @RequestMapping("/web-api/status")
    @ResponseBody
    public String status() {
        return OK;
    }

    @RequestMapping("/sayHello")
    @ResponseBody
    public String sayHello() {
        return String.format("{\"message\":\"%s\"}", mockmaService.sayHello());
    }

    @RequestMapping("/getNextUnit")
    @ResponseBody
    public String getNextUnit(Long userId) {
        return mockmaService.getNextUnit(userId);
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping("updateProgress")
    @ResponseBody
    public void updateProgress(Bookmark bookmark) {
        mockmaService.setBookmark(bookmark);
    }
}
