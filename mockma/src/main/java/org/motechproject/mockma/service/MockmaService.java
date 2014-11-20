package org.motechproject.mockma.service;

import org.motechproject.mtraining.domain.Bookmark;

/**
 * Simple example of a service interface.
 */
public interface MockmaService {

    String sayHello();

    /* Get next unit */
    String getNextUnit(String userId);

    /* Set bookmark */
    void setBookmark(Bookmark bookmark);

    /* returns true if the userId does not exist in the system aka. new user */
    boolean checkNewUser(String userId);
}
