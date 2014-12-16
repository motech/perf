package org.motechproject.kil2.database;

import org.motechproject.mds.annotations.Lookup;
import org.motechproject.mds.annotations.LookupField;
import org.motechproject.mds.service.MotechDataService;

import java.util.List;

public interface RecipientDataService extends MotechDataService<Recipient> {
    @Lookup
    Recipient findByExternalId(@LookupField(name = "externalId") String externalId);

    @Lookup
    List<Recipient> findBySlot(@LookupField(name = "slot") String slot);

    long countFindBySlot(@LookupField(name = "slot") String slot);
}
