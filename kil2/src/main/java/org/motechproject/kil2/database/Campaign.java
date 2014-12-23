package org.motechproject.kil2.database;

import org.motechproject.mds.annotations.Entity;
import org.motechproject.mds.annotations.Field;

import javax.jdo.annotations.Column;

@Entity
public class Campaign {
    @Field
    @Column(name="day", jdbcType="VARCHAR", length=1)
    private String day;

    @Field
    @Column(name="slot", jdbcType="VARCHAR", length=2)
    private String slot;

    public Campaign(String day, String slot) {
        this.day = day;
        this.slot = slot;
    }

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public String getSlot() {
        return slot;
    }

    public void setSlot(String slot) {
        this.slot = slot;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Campaign that = (Campaign) o;

        if (!day.equals(that.day)) return false;
        if (!slot.equals(that.slot)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result =  day.hashCode();
        result = 31 * result + slot.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Campaign{" +
                ", day='" + day + '\'' +
                ", slot='" + slot + '\'' +
                '}';
    }
}
