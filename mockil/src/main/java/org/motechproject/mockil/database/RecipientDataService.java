package org.motechproject.mockil.database;

import org.motechproject.mds.annotations.Lookup;
import org.motechproject.mds.annotations.LookupField;
import org.motechproject.mds.service.MotechDataService;

public interface RecipientDataService extends MotechDataService<Recipient> {
    @Lookup
    Recipient findByExternalId(@LookupField(name = "externalId") String externalId);
}
