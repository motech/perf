package org.motechproject.instanceinfo.web;

import org.motechproject.instanceinfo.service.InfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Responds to HTTP queries to {motech-server}/module/instance-info/**
 */
@Controller
public class InfoController {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private InfoService infoService;

    @Autowired
    public InfoController(InfoService infoService) {
        this.infoService = infoService;
    }

    /**
     * Returns the current instance's name
     * @return a string containing the instance name
     */
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/name")
    public String handle() {
        return infoService.getName();
    }
}
