package com.kkargas.gpm.model;

public class SoftwareUninstallation {

    public enum Status {
        PREPARING,
        UNINSTALLING,
        COMPLETED,
        FAILED
    }

    private final String packageId;
    private final String packageName;

    private volatile Status status;
    private volatile String message;

    public SoftwareUninstallation(
            String packageId,
            String packageName) {

        this.packageId = packageId;
        this.packageName = packageName;
        this.status = Status.PREPARING;
        this.message = "Preparing removal...";
    }

    public String getPackageId() {
        return packageId;
    }

    public String getPackageName() {
        return packageName;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isFinished() {
        return status == Status.COMPLETED
                || status == Status.FAILED;
    }
}
