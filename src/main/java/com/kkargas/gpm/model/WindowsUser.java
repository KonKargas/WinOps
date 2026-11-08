package com.kkargas.gpm.model;

public class WindowsUser {

    private String username;

    private String fullName;

    private String description;

    private boolean enabled;

    private boolean administrator;

    private boolean passwordRequired;

    private boolean passwordExpires;

    private boolean userMayChangePassword;


    public WindowsUser() {
    }


    public WindowsUser(
            String username,
            String fullName,
            String description,
            boolean enabled,
            boolean administrator,
            boolean passwordRequired,
            boolean passwordExpires,
            boolean userMayChangePassword) {

        this.username = username;
        this.fullName = fullName;
        this.description = description;
        this.enabled = enabled;
        this.administrator = administrator;
        this.passwordRequired = passwordRequired;
        this.passwordExpires = passwordExpires;
        this.userMayChangePassword = userMayChangePassword;
    }


    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }


    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }


    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }


    public boolean isAdministrator() {
        return administrator;
    }

    public void setAdministrator(boolean administrator) {
        this.administrator = administrator;
    }


    public boolean isPasswordRequired() {
        return passwordRequired;
    }

    public void setPasswordRequired(boolean passwordRequired) {
        this.passwordRequired = passwordRequired;
    }


    public boolean isPasswordExpires() {
        return passwordExpires;
    }

    public void setPasswordExpires(boolean passwordExpires) {
        this.passwordExpires = passwordExpires;
    }


    public boolean isUserMayChangePassword() {
        return userMayChangePassword;
    }

    public void setUserMayChangePassword(
            boolean userMayChangePassword) {

        this.userMayChangePassword =
                userMayChangePassword;
    }
}