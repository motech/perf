package org.motechproject.kil2.database;

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

    @Field
    private String expectedDeliveryDate;

    @Field
    private String slot;

    @Field
    private Boolean isActive;

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

    public String getExpectedDeliveryDate() {
        return expectedDeliveryDate;
    }

    public void setExpectedDeliveryDate(String expectedDeliveryDate) {
        this.expectedDeliveryDate = expectedDeliveryDate;
    }

    public String getSlot() {
        return slot;
    }

    public void setSlot(String slot) {
        this.slot = slot;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Recipient(String externalId, String phoneNumber, String expectedDeliveryDate, String slot, Boolean
            isActive) {

        this.externalId = externalId;
        this.phoneNumber = phoneNumber;
        this.expectedDeliveryDate = expectedDeliveryDate;
        this.slot = slot;
        this.isActive = isActive;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Recipient)) {
            return false;
        }

        Recipient recipient = (Recipient) o;

        if (!slot.equals(recipient.slot)) {
            return false;
        }
        if (!expectedDeliveryDate.equals(recipient.expectedDeliveryDate)) {
            return false;
        }
        if (!externalId.equals(recipient.externalId)) {
            return false;
        }
        if (!isActive.equals(recipient.isActive)) {
            return false;
        }
        if (!phoneNumber.equals(recipient.phoneNumber)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = externalId.hashCode();
        result = 31 * result + phoneNumber.hashCode();
        result = 31 * result + expectedDeliveryDate.hashCode();
        result = 31 * result + slot.hashCode();
        result = 31 * result + isActive.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Recipient{" +
                "externalId='" + externalId + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", expectedDeliveryDate='" + expectedDeliveryDate + '\'' +
                ", slot='" + slot + '\'' +
                ", isActive=" + isActive +
                '}';
    }
}
