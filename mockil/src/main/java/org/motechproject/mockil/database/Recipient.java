package org.motechproject.mockil.database;

import org.motechproject.mds.annotations.Entity;
import org.motechproject.mds.annotations.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Entity
public class Recipient {

    private static final Logger LOGGER = LoggerFactory.getLogger(Recipient.class);

    @Field
    private String externalId;

    @Field
    private String phoneNumber;

    public Recipient(String externalId, String phoneNumber) {
        this.externalId = externalId;
        this.phoneNumber = phoneNumber;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Recipient)) {
            return false;
        }

        Recipient that = (Recipient) o;

        if (!externalId.equals(that.externalId)) {
            return false;
        }
        if (!phoneNumber.equals(that.phoneNumber)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = externalId.hashCode();
        result = 31 * result + phoneNumber.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "PregnantWoman{" +
                "externalId='" + externalId + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                '}';
    }
}
