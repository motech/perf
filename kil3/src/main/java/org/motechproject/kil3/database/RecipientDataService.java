package org.motechproject.kil3.database;

import org.motechproject.mds.annotations.Lookup;
import org.motechproject.mds.annotations.LookupField;
import org.motechproject.mds.query.QueryParams;
import org.motechproject.mds.service.MotechDataService;

import java.util.List;

public interface RecipientDataService extends MotechDataService<Recipient> {

    @Lookup
    List<Recipient> findByDay(
            @LookupField(name = "day") String day);

    @Lookup
    List<Recipient> findByDay(
            @LookupField(name = "day") String day,
            QueryParams queryParams);

    long countFindByDay(
            @LookupField(name = "day") String day);
}
