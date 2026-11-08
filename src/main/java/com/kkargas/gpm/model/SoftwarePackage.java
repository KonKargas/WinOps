package com.kkargas.gpm.model;

public class SoftwarePackage {

    private String id;
    private String name;
    private String category;
    private String description;
    private boolean installed;


    public SoftwarePackage() {
    }


    public SoftwarePackage(
            String id,
            String name,
            String category,
            String description) {

        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
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


    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }


    public String getDescription() {
        return description;
    }

    public void setDescription(
            String description) {

        this.description = description;
    }


    public boolean isInstalled() {
        return installed;
    }

    public void setInstalled(
            boolean installed) {

        this.installed = installed;
    }
}