package org.motechproject.kil3.database;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.motechproject.mds.annotations.Entity;
import org.motechproject.mds.annotations.Field;
import org.motechproject.mds.annotations.Ignore;

import javax.jdo.annotations.Column;
import javax.jdo.annotations.Index;

@Entity
@Index(name="RECIPIENT_DAY_STATUS", members={"day", "status"})
public class Recipient {
    @Field
    @Column(name="status", jdbcType="VARCHAR", length=2)
    private RecipientStatus status;

    @Field
    @Column(name="initialDay", jdbcType="VARCHAR", length=1)
    private String initialDay;

    @Field
    @Column(name="day", jdbcType="VARCHAR", length=1)
    private String day;

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

    public Recipient(String initialDay, String day, String initialSlot, String slot, CallStage callStage, String phone, String language, String expectedDeliveryDate) {
        this.initialDay = initialDay;
        this.day = day;
        this.callStage = callStage;
        this.phone = phone;
        this.language = language;
        this.expectedDeliveryDate = expectedDeliveryDate;
    }

    public RecipientStatus getStatus() {
        return status;
    }

    public void setStatus(RecipientStatus status) {
        this.status = status;
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


    @Ignore
    public String pregnancyWeek() {
        int year =  Integer.valueOf(this.expectedDeliveryDate.substring(0, 4));
        int month =  Integer.valueOf(this.expectedDeliveryDate.substring(4, 6));
        int day =  Integer.valueOf(this.expectedDeliveryDate.substring(6, 8));
        DateTime expectedDeliveryDate = new DateTime(year, month, day, 0, 0);
        DateTime dtConception = expectedDeliveryDate.minusMonths(9);
        Period period = new Period(dtConception, DateTime.now());
        int week = period.getWeeks();
        return String.format("%d", week);
    }


    @Ignore
    public void incDay() {
        int d = Integer.valueOf(day);
        day = String.format("%d", d >= 7 ? 1 : d+1);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }

        Recipient recipient = (Recipient) o;

        if (callStage != recipient.callStage) { return false; }
        if (!day.equals(recipient.day)) { return false; }
        if (!expectedDeliveryDate.equals(recipient.expectedDeliveryDate)) { return false; }
        if (!initialDay.equals(recipient.initialDay)) { return false; }
        if (!language.equals(recipient.language)) { return false; }
        if (!phone.equals(recipient.phone)) { return false; }

        return true;
    }

    @Override
    public int hashCode() {
        int result = status.hashCode();
        result = 31 * result + initialDay.hashCode();
        result = 31 * result + day.hashCode();
        result = 31 * result + callStage.hashCode();
        result = 31 * result + phone.hashCode();
        result = 31 * result + language.hashCode();
        result = 31 * result + expectedDeliveryDate.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Recipient{" +
                "status='" + status + '\'' +
                ", initialDay='" + initialDay + '\'' +
                ", day='" + day + '\'' +
                ", callStage=" + callStage +
                ", phone='" + phone + '\'' +
                ", language='" + language + '\'' +
                ", expectedDeliveryDate='" + expectedDeliveryDate + '\'' +
                '}';
    }
}
