package org.motechproject.kil2.database;

import org.motechproject.mds.annotations.Entity;
import org.motechproject.mds.annotations.Field;

import javax.jdo.annotations.Column;
import javax.jdo.annotations.Index;

@Entity
public class CallHistory {
    @Field
    @Column(name="day", jdbcType="VARCHAR", length=1)
    private String day;

    @Field
    @Column(name="slot", jdbcType="VARCHAR", length=2)
    private String slot;

    @Field
    @Column(name="callStage", jdbcType="VARCHAR", length=9)
    private CallStage callStage;

    @Field
    @Column(name="phone", jdbcType="VARCHAR", length=10)
    private String phone;

    @Field
    @Column(name="language", jdbcType="VARCHAR", length=2)
    private String language;

    @Field
    @Column(name="expectedDeliveryDate", jdbcType="VARCHAR", length=8)
    private String expectedDeliveryDate;

    @Field
    @Column(name="callStatus", jdbcType="VARCHAR", length=2)
    private CallStatus callStatus;

    @Field
    @Column(name="recipientStatus", jdbcType="VARCHAR", length=2)
    private RecipientStatus recipientStatus;

    public CallHistory(String day, String slot, CallStage callStage, String phone, String language, String expectedDeliveryDate, CallStatus callStatus, RecipientStatus recipientStatus) {
        this.day = day;
        this.slot = slot;
        this.callStage = callStage;
        this.phone = phone;
        this.language = language;
        this.expectedDeliveryDate = expectedDeliveryDate;
        this.callStatus = callStatus;
        this.recipientStatus = recipientStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CallHistory that = (CallHistory) o;

        if (callStage != that.callStage) {
            return false;
        }
        if (callStatus != that.callStatus) {
            return false;
        }
        if (!day.equals(that.day)) {
            return false;
        }
        if (!expectedDeliveryDate.equals(that.expectedDeliveryDate)) {
            return false;
        }
        if (!language.equals(that.language)) {
            return false;
        }
        if (!phone.equals(that.phone)) {
            return false;
        }
        if (recipientStatus != that.recipientStatus) {
            return false;
        }
        if (!slot.equals(that.slot)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = day.hashCode();
        result = 31 * result + slot.hashCode();
        result = 31 * result + callStage.hashCode();
        result = 31 * result + phone.hashCode();
        result = 31 * result + language.hashCode();
        result = 31 * result + expectedDeliveryDate.hashCode();
        result = 31 * result + callStatus.hashCode();
        result = 31 * result + recipientStatus.hashCode();
        return result;
    }
}
