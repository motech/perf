package org.motechproject.kil3.service;

public class CallDetailRecord {
    private String id;
    private String number;
    private String status;

    protected CallDetailRecord(String id, String number, String status) {
        this.id = id;
        this.number = number;
        this.status = status;
    }

    public static final CallDetailRecord fromString(String string) throws Exception {
        String[] fields = string.split("\\s*,\\s*");
        if (fields.length != 3) {
            throw new Exception("Invalid CDR");
        }
        return new CallDetailRecord(fields[0], fields[1], fields[2]);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CallDetailRecord that = (CallDetailRecord) o;

        if (!id.equals(that.id)) return false;
        if (!number.equals(that.number)) return false;
        if (!status.equals(that.status)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + number.hashCode();
        result = 31 * result + status.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "CallDetailRecord{" +
                "id='" + id + '\'' +
                ", number='" + number + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
