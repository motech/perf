package org.motechproject.mockma.service.impl;

import org.motechproject.mockma.service.MockmaService;
import org.motechproject.mtraining.domain.Bookmark;
import org.motechproject.mtraining.service.ActivityService;
import org.motechproject.mtraining.service.BookmarkService;
import org.motechproject.mtraining.service.MTrainingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    public MockmaServiceImpl(MTrainingService mTrainingService, BookmarkService bookmarkService, ActivityService activityService) {
        this.mTrainingService = mTrainingService;
        this.bookmarkService = bookmarkService;
        this.activityService = activityService;
    }

    @Override
    public String sayHello() {
        logger.info("They said hello and I said world?");
        return "Hello World";
    }

    @Override
    public String getNextUnit(Long userId) {

        Bookmark latest = this.bookmarkService.getLatestBookmarkByUserId(userId.toString());
        return latest != null ? mTrainingService.getCourseById(Long.parseLong(latest.getCourseIdentifier())).toString() : null;
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
