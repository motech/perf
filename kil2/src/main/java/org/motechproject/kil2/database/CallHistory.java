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
}
