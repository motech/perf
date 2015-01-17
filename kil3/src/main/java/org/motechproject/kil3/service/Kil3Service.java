package org.motechproject.kil3.service;

public interface Kil3Service {
    String createCallFile(String slot, String day);
    String processCallDetailRecords(String slot, String day);
    String getRecipients();
}
