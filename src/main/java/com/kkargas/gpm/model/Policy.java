package com.kkargas.gpm.model;

import com.kkargas.gpm.enums.PolicyState;

public class Policy {

    private String id;
    private String name;
    private String description;
    private boolean requiresAdmin;

    private String scope;

    private String registryPath;
    private String registryValue;
    private String registryType;
    private String enabledValue;

    private String configuredValue;

    public String getConfiguredValue() { return configuredValue; }
    public void setConfiguredValue(String configuredValue) { this.configuredValue = configuredValue; }

    private PolicyState policyState = PolicyState.NOT_CONFIGURED;


    public Policy() {
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


    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


    public boolean isRequiresAdmin() {
        return requiresAdmin;
    }

    public void setRequiresAdmin(boolean requiresAdmin) {
        this.requiresAdmin = requiresAdmin;
    }


    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }


    public String getRegistryPath() {
        return registryPath;
    }

    public void setRegistryPath(String registryPath) {
        this.registryPath = registryPath;
    }


    public String getRegistryValue() {
        return registryValue;
    }

    public void setRegistryValue(String registryValue) {
        this.registryValue = registryValue;
    }


    public String getRegistryType() {
        return registryType;
    }

    public void setRegistryType(String registryType) {
        this.registryType = registryType;
    }


    public String getEnabledValue() {
        return enabledValue;
    }

    public void setEnabledValue(String enabledValue) {
        this.enabledValue = enabledValue;
    }


    public PolicyState getPolicyState() {
        return policyState;
    }

    public void setPolicyState(PolicyState policyState) {
        this.policyState = policyState;
    }
}