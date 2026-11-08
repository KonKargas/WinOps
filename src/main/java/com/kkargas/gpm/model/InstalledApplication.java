package com.kkargas.gpm.model;

public class InstalledApplication {

    private String id;
    private String name;

    private String version;
    private String availableVersion;

    private String source;


    public InstalledApplication() {
    }


    public InstalledApplication(
            String id,
            String name,
            String version,
            String availableVersion,
            String source) {

        this.id = id;
        this.name = name;
        this.version = version;
        this.availableVersion =
                availableVersion;
        this.source = source;
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public String getVersion() {
        return version;
    }

    public void setVersion(
            String version) {

        this.version = version;
    }


    public String getAvailableVersion() {
        return availableVersion;
    }

    public void setAvailableVersion(
            String availableVersion) {

        this.availableVersion =
                availableVersion;
    }


    public String getSource() {
        return source;
    }

    public void setSource(
            String source) {

        this.source = source;
    }


    public boolean isUpdateAvailable() {

        return availableVersion != null
                && !availableVersion.isBlank();
    }
}