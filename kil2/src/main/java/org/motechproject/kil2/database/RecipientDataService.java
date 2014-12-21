package org.motechproject.kil2.database;

import org.motechproject.mds.annotations.Lookup;
import org.motechproject.mds.annotations.LookupField;
import org.motechproject.mds.service.MotechDataService;

import java.util.List;

public interface RecipientDataService extends MotechDataService<Recipient> {

    @Lookup
    List<Recipient> findBySlotDayStatus(
            @LookupField(name = "slot") String slot,
            @LookupField(name = "day") String day,
            @LookupField(name = "status") Status status);

    long countFindBySlotDayStatus(
            @LookupField(name = "slot") String slot,
            @LookupField(name = "day") String day,
            @LookupField(name = "status") Status status);
}