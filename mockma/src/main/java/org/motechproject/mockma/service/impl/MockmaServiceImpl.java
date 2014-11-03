package org.motechproject.mockma.service.impl;

import org.motechproject.mockma.service.MockmaService;
import org.springframework.stereotype.Service;

/**
 * Simple implementation of the {@link org.motechproject.mockma.service.MockmaService} interface.
 */
@Service("helloWorldService")
public class MockmaServiceImpl implements MockmaService {

    @Override
    public String sayHello() {
        return "Hello World";
    }

}
