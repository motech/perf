package org.motechproject.mockma.service.impl;

import org.motechproject.mockma.service.MockmaService;
import org.motechproject.mtraining.domain.Bookmark;
import org.motechproject.mtraining.domain.Course;
import org.motechproject.mtraining.service.ActivityService;
import org.motechproject.mtraining.service.BookmarkService;
import org.motechproject.mtraining.service.MTrainingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Simple implementation of the {@link org.motechproject.mockma.service.MockmaService} interface.
 */
@Service("mockmaService")
public class MockmaServiceImpl implements MockmaService {

    private MTrainingService mTrainingService;
    private BookmarkService bookmarkService;
    private ActivityService activityService;

    @Autowired
    public MockmaServiceImpl(MTrainingService mTrainingService, BookmarkService bookmarkService, ActivityService activityService) {
        this.mTrainingService = mTrainingService;
        this.bookmarkService = bookmarkService;
        this.activityService = activityService;
    }

    @Override
    public String sayHello() {
        return "Hello World";
    }

    @Override
    public String getNextUnit(Long userId) {

        Bookmark latest = this.bookmarkService.getLatestBookmarkByUserId(userId.toString());
        return mTrainingService.getCourseById(Long.parseLong(latest.getCourseIdentifier())).toString();
    }

    @Override
    public void setBookmark(Bookmark bookmark) {

        bookmarkService.updateBookmark(bookmark);
    }

    @Override
    public boolean checkNewUser(Long userId) {

        return this.activityService.getAllActivityForUser(userId.toString()).isEmpty() &&
                this.bookmarkService.getAllBookmarksForUser(userId.toString()).isEmpty();
    }

}
