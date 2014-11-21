package org.motechproject.mockma.service;

import org.motechproject.mtraining.domain.Bookmark;
import org.motechproject.mtraining.domain.Course;

/**
 * Simple example of a service interface.
 */
public interface MockmaService {

    /* Get next unit */
    String getNextUnit(String userId);

    /* Set bookmark */
    void setBookmark(Bookmark bookmark);

    /* returns true if the userId does not exist in the system aka. new user */
    boolean checkNewUser(String userId);

    /* returns the next course for the user */
    String GetCourseString(String courseName);
}
