package com.kkargas.gpm.model;

public class SoftwareUpdate {

    public enum Status {
        PREPARING,
        UPDATING,
        COMPLETED,
        FAILED
    }

    private final String jobId;
    private final String packageId;
    private final String packageName;
    private final boolean updateAll;

    private volatile Status status;
    private volatile String message;

    public SoftwareUpdate(
            String jobId,
            String packageId,
            String packageName,
            boolean updateAll) {

        this.jobId = jobId;
        this.packageId = packageId;
        this.packageName = packageName;
        this.updateAll = updateAll;
        this.status = Status.PREPARING;
        this.message = "Preparing update...";
    }

    public String getJobId() {
        return jobId;
    }

    public String getPackageId() {
        return packageId;
    }

    public String getPackageName() {
        return packageName;
    }

    public boolean isUpdateAll() {
        return updateAll;
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
