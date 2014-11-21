package org.motechproject.mockma.web;

import org.motechproject.mockma.service.MockmaService;
import org.motechproject.mtraining.domain.Bookmark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
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

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final String OK = "OK";

    @RequestMapping("/status")
    @ResponseBody
    public String status() {

        logger.debug("Called status in MockmaController");
        return OK;
    }

    @RequestMapping("/getNextUnit")
    @ResponseBody
    public String getNextUnit(String userId) {

        logger.debug(String.format("Getting next unit for %s", userId));
        return mockmaService.getNextUnit(userId);
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping("/updateProgress")
    @ResponseBody
    public void updateProgress(Bookmark bookmark) {

        logger.debug(String.format("Updating progress with bookmark %s", bookmark.toString()));
        mockmaService.setBookmark(bookmark);
    }

    @RequestMapping("/checkUser")
    @ResponseBody
    public Boolean checkUser(String userId) {

        logger.debug(String.format("Check for new user with id %s", userId));
        return mockmaService.checkNewUser(userId);
    }
}
