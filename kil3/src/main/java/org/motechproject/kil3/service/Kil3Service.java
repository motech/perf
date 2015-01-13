package org.motechproject.kil3.service;

public interface Kil3Service {
    String scheduleJob(String dateOrPeriod, String slot, String day);
    String getStatus();
    String deleteJob (long id);
    String deleteAllJobs ();
    String reload();
    String listJobs();
}
