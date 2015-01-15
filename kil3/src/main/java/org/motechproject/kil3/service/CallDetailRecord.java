package org.motechproject.kil3.service;

import org.motechproject.kil3.database.CallStatus;

import java.io.Serializable;

public class CallDetailRecord implements Serializable {
    private static final long serialVersionUID = -1706017553940100679L;
    private String id;
    private String recipient;
    private String number;
    private CallStatus callStatus;

    public CallDetailRecord(String id, String recipient, String number, CallStatus callStatus) {
        this.id = id;
        this.recipient = recipient;
        this.number = number;
        this.callStatus = callStatus;
    }

    public static void validate(String string) throws Exception {
        String[] fields = string.split("\\s*,\\s*");
        if (fields.length != 4) {
            throw new Exception("Invalid CDR");
        }
        CallStatus.valueOf(fields[3]);
    }

    public static CallDetailRecord fromString(String string) {
        String[] fields = string.split("\\s*,\\s*");
        return new CallDetailRecord(fields[0], fields[1], fields[2], CallStatus.valueOf(fields[3]));
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public CallStatus getCallStatus() {
        return callStatus;
    }

    public void setCallStatus(CallStatus callStatus) {
        this.callStatus = callStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CallDetailRecord that = (CallDetailRecord) o;

        if (!id.equals(that.id)) return false;
        if (!number.equals(that.number)) return false;
        if (!recipient.equals(that.recipient)) return false;
        if (!callStatus.equals(that.callStatus)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + recipient.hashCode();
        result = 31 * result + number.hashCode();
        result = 31 * result + callStatus.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "CallDetailRecord{" +
                "id='" + id + '\'' +
                ", recipient='" + recipient + '\'' +
                ", number='" + number + '\'' +
                ", callStatus='" + callStatus + '\'' +
                '}';
    }
}
