package org.motechproject.mockma.service;

import org.motechproject.mtraining.domain.Bookmark;

/**
 * Simple example of a service interface.
 */
public interface MockmaService {

    String sayHello();

    /* Get next unit */
    String getNextUnit(Long userId);

    /* Set bookmark */
    void setBookmark(Bookmark bookmark);
}
