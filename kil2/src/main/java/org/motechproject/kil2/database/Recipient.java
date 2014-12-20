package org.motechproject.kil2.database;

import org.motechproject.mds.annotations.Entity;
import org.motechproject.mds.annotations.Field;

import javax.jdo.annotations.Column;
import javax.jdo.annotations.Index;

@Entity
@Index(name="ACTIVE_SLOT_DAY", members={"slot","day","isActive"})
public class Recipient {

    @Field
    private String phoneNumber;

    @Field
    private String expectedDeliveryDate;

    @Field
    @Column(name="slot", jdbcType="VARCHAR", length=2)
    private String slot;

    @Field
    @Column(name="day", jdbcType="VARCHAR", length=1)
    private String day;

    @Field
    private Boolean isActive;


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

    public Recipient(String phoneNumber, String expectedDeliveryDate, String slot, String day, Boolean isActive) {
        this.phoneNumber = phoneNumber;
        this.expectedDeliveryDate = expectedDeliveryDate;
        this.slot = slot;
        this.day = day;
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

        if (!day.equals(recipient.day)) {
            return false;
        }
        if (!expectedDeliveryDate.equals(recipient.expectedDeliveryDate)) {
            return false;
        }
        if (!isActive.equals(recipient.isActive)) {
            return false;
        }
        if (!phoneNumber.equals(recipient.phoneNumber)) {
            return false;
        }
        if (!slot.equals(recipient.slot)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = phoneNumber.hashCode();
        result = 31 * result + expectedDeliveryDate.hashCode();
        result = 31 * result + slot.hashCode();
        result = 31 * result + day.hashCode();
        result = 31 * result + isActive.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Recipient{" +
                "phoneNumber='" + phoneNumber + '\'' +
                ", expectedDeliveryDate='" + expectedDeliveryDate + '\'' +
                ", slot='" + slot + '\'' +
                ", day='" + day + '\'' +
                ", isActive=" + isActive +
                '}';
    }
}
