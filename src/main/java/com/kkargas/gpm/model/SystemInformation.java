package com.kkargas.gpm.model;

public class SystemInformation {

    private String computerName;
    private String windowsEdition;
    private String windowsVersion;
    private String windowsBuild;
    private String architecture;

    private String processor;
    private String installedRam;

    private String manufacturer;
    private String model;

    private String biosVersion;

    private String systemDrive;
    private String systemDriveFreeSpace;

    private String uptime;
    
    private String timeZoneId;
    private String timeZoneDisplayName;
    
    private String activePowerPlan;
    private String activePowerPlanGuid;

    private int displayTimeoutAc;
    private int sleepTimeoutAc;

    private boolean hibernateEnabled;


    public SystemInformation() {
    }


    public String getComputerName() {
        return computerName;
    }

    public void setComputerName(String computerName) {
        this.computerName = computerName;
    }


    public String getWindowsEdition() {
        return windowsEdition;
    }

    public void setWindowsEdition(String windowsEdition) {
        this.windowsEdition = windowsEdition;
    }


    public String getWindowsVersion() {
        return windowsVersion;
    }

    public void setWindowsVersion(String windowsVersion) {
        this.windowsVersion = windowsVersion;
    }


    public String getWindowsBuild() {
        return windowsBuild;
    }

    public void setWindowsBuild(String windowsBuild) {
        this.windowsBuild = windowsBuild;
    }


    public String getArchitecture() {
        return architecture;
    }

    public void setArchitecture(String architecture) {
        this.architecture = architecture;
    }


    public String getProcessor() {
        return processor;
    }

    public void setProcessor(String processor) {
        this.processor = processor;
    }


    public String getInstalledRam() {
        return installedRam;
    }

    public void setInstalledRam(String installedRam) {
        this.installedRam = installedRam;
    }


    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }


    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }


    public String getBiosVersion() {
        return biosVersion;
    }

    public void setBiosVersion(String biosVersion) {
        this.biosVersion = biosVersion;
    }


    public String getSystemDrive() {
        return systemDrive;
    }

    public void setSystemDrive(String systemDrive) {
        this.systemDrive = systemDrive;
    }


    public String getSystemDriveFreeSpace() {
        return systemDriveFreeSpace;
    }

    public void setSystemDriveFreeSpace(
            String systemDriveFreeSpace) {

        this.systemDriveFreeSpace =
                systemDriveFreeSpace;
    }


    public String getUptime() {
        return uptime;
    }

    public void setUptime(String uptime) {
        this.uptime = uptime;
    }
    
    public String getTimeZoneId() {
        return timeZoneId;
    }

    public void setTimeZoneId(String timeZoneId) {
        this.timeZoneId = timeZoneId;
    }


    public String getTimeZoneDisplayName() {
        return timeZoneDisplayName;
    }

    public void setTimeZoneDisplayName(
            String timeZoneDisplayName) {

        this.timeZoneDisplayName =
                timeZoneDisplayName;
    }
    
    public String getActivePowerPlan() {
        return activePowerPlan;
    }

    public void setActivePowerPlan(String activePowerPlan) {
        this.activePowerPlan = activePowerPlan;
    }


    public String getActivePowerPlanGuid() {
        return activePowerPlanGuid;
    }

    public void setActivePowerPlanGuid(String activePowerPlanGuid) {
        this.activePowerPlanGuid = activePowerPlanGuid;
    }


    public int getDisplayTimeoutAc() {
        return displayTimeoutAc;
    }

    public void setDisplayTimeoutAc(int displayTimeoutAc) {
        this.displayTimeoutAc = displayTimeoutAc;
    }


    public int getSleepTimeoutAc() {
        return sleepTimeoutAc;
    }

    public void setSleepTimeoutAc(int sleepTimeoutAc) {
        this.sleepTimeoutAc = sleepTimeoutAc;
    }


    public boolean isHibernateEnabled() {
        return hibernateEnabled;
    }

    public void setHibernateEnabled(boolean hibernateEnabled) {
        this.hibernateEnabled = hibernateEnabled;
    }
}