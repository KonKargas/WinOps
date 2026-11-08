package com.kkargas.gpm.model;

public class WindowsTimeZone {

    private String id;
    private String displayName;


    public WindowsTimeZone() {
    }


    public WindowsTimeZone(
            String id,
            String displayName) {

        this.id = id;
        this.displayName = displayName;
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }


    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(
            String displayName) {

        this.displayName = displayName;
    }
}