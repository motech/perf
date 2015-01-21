package org.motechproject.kil3.service;

public interface Kil3Service {
    String createCallFile(String day);
    String processCallDetailRecords(String day);
    String getRecipients();
}
