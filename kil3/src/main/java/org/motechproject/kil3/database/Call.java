package org.motechproject.kil3.database;

import org.motechproject.mds.annotations.Entity;
import org.motechproject.mds.annotations.Field;

import javax.jdo.annotations.Column;
import javax.jdo.annotations.Index;

@Entity
@Index(name="RECIPIENT_DAY_SLOT", members={"day", "slot"})
public class Call {
    @Field
    @Column(name="initialDay", jdbcType="VARCHAR", length=1)
    private String initialDay;

    @Field
    @Column(name="day", jdbcType="VARCHAR", length=1)
    private String day;

    @Field
    @Column(name="initialSlot", jdbcType="VARCHAR", length=2)
    private String initialSlot;

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

    public Call(String initialDay, String day, String initialSlot, String slot, CallStage callStage, String phone, String language, String expectedDeliveryDate) {
        this.initialDay = initialDay;
        this.day = day;
        this.initialSlot = initialSlot;
        this.slot = slot;
        this.callStage = callStage;
        this.phone = phone;
        this.language = language;
        this.expectedDeliveryDate = expectedDeliveryDate;
    }

    public String getInitialDay() {
        return initialDay;
    }

    public void setInitialDay(String initialDay) {
        this.initialDay = initialDay;
    }

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public String getInitialSlot() {
        return initialSlot;
    }

    public void setInitialSlot(String initialSlot) {
        this.initialSlot = initialSlot;
    }

    public String getSlot() {
        return slot;
    }

    public void setSlot(String slot) {
        this.slot = slot;
    }

    public CallStage getCallStage() {
        return callStage;
    }

    public void setCallStage(CallStage callStage) {
        this.callStage = callStage;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getExpectedDeliveryDate() {
        return expectedDeliveryDate;
    }

    public void setExpectedDeliveryDate(String expectedDeliveryDate) {
        this.expectedDeliveryDate = expectedDeliveryDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }

        Call call = (Call) o;

        if (callStage != call.callStage) { return false; }
        if (!day.equals(call.day)) { return false; }
        if (!expectedDeliveryDate.equals(call.expectedDeliveryDate)) { return false; }
        if (!initialDay.equals(call.initialDay)) { return false; }
        if (!initialSlot.equals(call.initialSlot)) { return false; }
        if (!language.equals(call.language)) { return false; }
        if (!phone.equals(call.phone)) { return false; }
        if (!slot.equals(call.slot)) { return false; }

        return true;
    }

    @Override
    public int hashCode() {
        int result = initialDay.hashCode();
        result = 31 * result + day.hashCode();
        result = 31 * result + initialSlot.hashCode();
        result = 31 * result + slot.hashCode();
        result = 31 * result + callStage.hashCode();
        result = 31 * result + phone.hashCode();
        result = 31 * result + language.hashCode();
        result = 31 * result + expectedDeliveryDate.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Call{" +
                "initialDay='" + initialDay + '\'' +
                ", day='" + day + '\'' +
                ", initialSlot='" + initialSlot + '\'' +
                ", slot='" + slot + '\'' +
                ", callStage=" + callStage +
                ", phone='" + phone + '\'' +
                ", language='" + language + '\'' +
                ", expectedDeliveryDate='" + expectedDeliveryDate + '\'' +
                '}';
    }
}
