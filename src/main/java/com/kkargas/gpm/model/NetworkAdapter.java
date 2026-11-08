package com.kkargas.gpm.model;

public class NetworkAdapter {

    private String name;
    private String interfaceDescription;

    private String status;

    private String ipv4Address;
    private String subnetMask;
    private String defaultGateway;

    private String primaryDns;
    private String secondaryDns;

    private String macAddress;

    private boolean dhcpEnabled;


    public NetworkAdapter() {
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public String getInterfaceDescription() {
        return interfaceDescription;
    }

    public void setInterfaceDescription(
            String interfaceDescription) {

        this.interfaceDescription =
                interfaceDescription;
    }


    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }


    public String getIpv4Address() {
        return ipv4Address;
    }

    public void setIpv4Address(
            String ipv4Address) {

        this.ipv4Address =
                ipv4Address;
    }


    public String getSubnetMask() {
        return subnetMask;
    }

    public void setSubnetMask(
            String subnetMask) {

        this.subnetMask =
                subnetMask;
    }


    public String getDefaultGateway() {
        return defaultGateway;
    }

    public void setDefaultGateway(
            String defaultGateway) {

        this.defaultGateway =
                defaultGateway;
    }


    public String getPrimaryDns() {
        return primaryDns;
    }

    public void setPrimaryDns(
            String primaryDns) {

        this.primaryDns =
                primaryDns;
    }


    public String getSecondaryDns() {
        return secondaryDns;
    }

    public void setSecondaryDns(
            String secondaryDns) {

        this.secondaryDns =
                secondaryDns;
    }


    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(
            String macAddress) {

        this.macAddress =
                macAddress;
    }


    public boolean isDhcpEnabled() {
        return dhcpEnabled;
    }

    public void setDhcpEnabled(
            boolean dhcpEnabled) {

        this.dhcpEnabled =
                dhcpEnabled;
    }
}