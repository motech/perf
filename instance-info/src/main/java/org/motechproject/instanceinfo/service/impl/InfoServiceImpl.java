package org.motechproject.instanceinfo.service.impl;

import org.motechproject.instanceinfo.service.InfoService;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * See {@link org.motechproject.instanceinfo.service.InfoService}
 */
@Service("infoService")
public class InfoServiceImpl implements InfoService {

    public String getName() {
        String name;
        try {
            InetAddress ip = InetAddress.getLocalHost();
            name = ip.getHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException("Could not get instance name: " + e.toString(), e);
        }
        return name;
    }
}
