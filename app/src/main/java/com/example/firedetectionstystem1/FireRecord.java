package com.example.firedetectionstystem1;

import java.util.Date;

public class FireRecord {
    private String location;
    private String intensity;
    private Date timestamp;
    private String status;

    public FireRecord() {} // Inahitajika na Firebase

    public FireRecord(String location, String intensity, Date timestamp, String status) {
        this.location = location;
        this.intensity = intensity;
        this.timestamp = timestamp;
        this.status = status;
    }

    // Getters and Setters
    public String getLocation() { return location; }
    public String getIntensity() { return intensity; }
    public Date getTimestamp() { return timestamp; }
    public String getStatus() { return status; }
}
