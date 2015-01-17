package org.motechproject.kil3.service;

/**
 * Deals with setting & meeting expectations in order to get performance statistics
 */
public interface ExpectationService {
    void setExpectations(String jobId, long count);
    void meetExpectation(String jobId);
}
