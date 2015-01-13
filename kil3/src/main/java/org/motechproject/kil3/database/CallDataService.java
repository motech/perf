package org.motechproject.kil3.database;

import org.motechproject.mds.annotations.Lookup;
import org.motechproject.mds.annotations.LookupField;
import org.motechproject.mds.query.QueryParams;
import org.motechproject.mds.service.MotechDataService;

import java.util.List;

public interface CallDataService extends MotechDataService<Call> {

    @Lookup
    List<Call> findByDaySlot(
            @LookupField(name = "day") String day,
            @LookupField(name = "slot") String slot);

    @Lookup
    List<Call> findByDaySlot(
            @LookupField(name = "day") String day,
            @LookupField(name = "slot") String slot,
            QueryParams queryParams);

    long countFindByDaySlot(
            @LookupField(name = "day") String day,
            @LookupField(name = "slot") String slot);
}
