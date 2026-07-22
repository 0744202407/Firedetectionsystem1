package com.example.firedetectionstystem1;

import java.util.Date;

public class AlertModel {
    private String status;
    private String location;
    private Object timestamp;
    private Long smokeLevel;

    public AlertModel() {}

    public AlertModel(String status, String location, Object timestamp, Long smokeLevel) {
        this.status = status;
        this.location = location;
        this.timestamp = timestamp;
        this.smokeLevel = smokeLevel;
    }

    public String getStatus() { return status; }
    public String getLocation() { return location; }
    public Object getTimestamp() { return timestamp; }
    public Long getSmokeLevel() { return smokeLevel; }
}
