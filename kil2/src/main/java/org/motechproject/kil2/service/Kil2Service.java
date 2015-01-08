package org.motechproject.kil2.service;

public interface Kil2Service {
    String scheduleJob(String dateOrPeriod, String slot, String day);
    String getStatus();
    String deleteJob (long id);
    String reset();
    String listJobs();
}
