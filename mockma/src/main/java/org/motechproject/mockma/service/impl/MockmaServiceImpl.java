package org.motechproject.mockma.service.impl;

import org.motechproject.mockma.service.MockmaService;
import org.motechproject.mtraining.domain.Bookmark;
import org.motechproject.mtraining.service.BookmarkService;
import org.motechproject.mtraining.service.MTrainingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Simple implementation of the {@link org.motechproject.mockma.service.MockmaService} interface.
 */
@Service("helloWorldService")
public class MockmaServiceImpl implements MockmaService {

    private MTrainingService mTrainingService;
    private BookmarkService bookmarkService;

    @Autowired
    public MockmaServiceImpl(MTrainingService mTrainingService, BookmarkService bookmarkService) {
        this.mTrainingService = mTrainingService;
    }

    @Override
    public String sayHello() {
        return "Hello World";
    }

    @Override
    public String getNextUnit(Long userId) {

        return "";
    }

    @Override
    public void setBookmark(Bookmark bookmark) {
        bookmarkService.updateBookmark(bookmark);
    }

}
