package org.motechproject.mockma.web;

import org.motechproject.mockma.service.MockmaService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;

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
}
