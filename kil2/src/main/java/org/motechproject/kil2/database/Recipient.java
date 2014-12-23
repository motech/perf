package org.motechproject.kil2.database;

import org.motechproject.mds.annotations.Entity;
import org.motechproject.mds.annotations.Field;

import javax.jdo.annotations.Column;
import javax.jdo.annotations.Index;

@Entity
@Index(name="ACTIVE_SLOT_DAY", members={"day", "slot", "status"})
public class Recipient {

    @Field
    @Column(name="day", jdbcType="VARCHAR", length=1)
    private String day;

    @Field
    @Column(name="slot", jdbcType="VARCHAR", length=2)
    private String slot;

    @Field
    @Column(name="status", jdbcType="VARCHAR", length=9)
    private Status status;

    @Field
    @Column(name="phone", jdbcType="VARCHAR", length=10)
    private String phone;

    @Field
    @Column(name="expectedDeliveryDate", jdbcType="VARCHAR", length=2)
    private String expectedDeliveryDate;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = this.status;
    }

    public Recipient(String phone, String expectedDeliveryDate, String slot, String day, Status status) {
        this.phone = phone;
        this.expectedDeliveryDate = expectedDeliveryDate;
        this.slot = slot;
        this.day = day;
        this.status = status;
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
        if (!status.equals(recipient.status)) {
            return false;
        }
        if (!phone.equals(recipient.phone)) {
            return false;
        }
        if (!slot.equals(recipient.slot)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = phone.hashCode();
        result = 31 * result + expectedDeliveryDate.hashCode();
        result = 31 * result + slot.hashCode();
        result = 31 * result + day.hashCode();
        result = 31 * result + status.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Recipient{" +
                "phone='" + phone + '\'' +
                ", expectedDeliveryDate='" + expectedDeliveryDate + '\'' +
                ", slot='" + slot + '\'' +
                ", day='" + day + '\'' +
                ", status=" + status +
                '}';
    }
}
