package com.kkargas.gpm.controller;

import java.util.List;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;

import com.kkargas.gpm.model.NetworkAdapter;
import com.kkargas.gpm.service.WindowsNetworkService;

@Controller
public class NetworkController {

    private final WindowsNetworkService
            windowsNetworkService;


    public NetworkController(
            WindowsNetworkService windowsNetworkService) {

        this.windowsNetworkService =
                windowsNetworkService;
    }


    /*
     * =========================================================
     * NETWORK DIAGNOSTICS
     * =========================================================
     */

    @GetMapping("/network/tools/ping")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, String> ping(@RequestParam String host) {
        return diagnosticResponse(() -> windowsNetworkService.pingHost(host));
    }

    @GetMapping("/network/tools/dns")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, String> dnsLookup(@RequestParam String host) {
        return diagnosticResponse(() -> windowsNetworkService.lookupDns(host));
    }

    @GetMapping("/network/tools/ipconfig")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, String> ipConfiguration() {
        return diagnosticResponse(windowsNetworkService::getIpConfiguration);
    }

    @PostMapping("/network/tools/connections")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, String> openConnections() {
        return diagnosticResponse(() -> {
            windowsNetworkService.openNetworkConnections();
            return "Network Connections requested.";
        });
    }

    private java.util.Map<String, String> diagnosticResponse(
            java.util.function.Supplier<String> task) {
        try {
            return java.util.Map.of("status", "ok", "output", task.get());
        } catch (IllegalArgumentException e) {
            return java.util.Map.of("status", "error", "output", e.getMessage());
        } catch (IllegalStateException e) {
            return java.util.Map.of("status", "error", "output", e.getMessage());
        }
    }

    /*
     * =========================================================
     * NETWORK PAGE
     * =========================================================
     */

    @GetMapping("/network/adapters/status")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, Boolean> adapterStatus() {
        return java.util.Map.of(
                "loading", windowsNetworkService.isAdapterDetailsLoading(),
                "ready", windowsNetworkService.areAdapterDetailsReady());
    }

    @GetMapping("/network")
    public String viewNetwork(
            @RequestParam(
                    required = false
            ) String adapter,
            Model model) {


        List<NetworkAdapter> adapters =
                windowsNetworkService
                        .getNetworkAdaptersFast();


        NetworkAdapter selectedAdapter =
                null;


        if (!adapters.isEmpty()) {

            if (adapter != null
                    && !adapter.isBlank()) {

                selectedAdapter =
                        adapters.stream()
                                .filter(item ->
                                        item.getName()
                                                .equals(adapter))
                                .findFirst()
                                .orElse(null);
            }


            /*
             * If no adapter was selected,
             * prefer an active adapter.
             */

            if (selectedAdapter == null) {

                selectedAdapter =
                        adapters.stream()
                                .filter(item ->
                                        "Up".equalsIgnoreCase(
                                                item.getStatus()
                                        ))
                                .findFirst()
                                .orElse(
                                        adapters.get(0)
                                );
            }
        }


        model.addAttribute("adapterDetailsLoading", windowsNetworkService.isAdapterDetailsLoading());
        model.addAttribute("adapterDetailsReady", windowsNetworkService.areAdapterDetailsReady());

        model.addAttribute(
                "adapters",
                adapters
        );


        model.addAttribute(
                "selectedAdapter",
                selectedAdapter
        );


        return "Network";
    }
    
    /*
     * =========================================================
     * ENABLE DHCP
     * =========================================================
     */

    @PostMapping("/network/dhcp")
    public String enableDhcp(
            @RequestParam String adapter) {
        windowsNetworkService.invalidateAdapterCache();

        boolean success =
                windowsNetworkService
                        .enableDhcp(adapter);


        return "redirect:/network?adapter="
                + encodeParameter(adapter)
                + "&dhcpSuccess="
                + success;
    }


    /*
     * =========================================================
     * STATIC IPV4
     * =========================================================
     */

    @PostMapping("/network/static-ip")
    public String configureStaticIp(
            @RequestParam String adapter,
            @RequestParam String ipAddress,
            @RequestParam String subnetMask,
            @RequestParam String defaultGateway,
            @RequestParam String primaryDns,
            @RequestParam(
                    required = false
            ) String secondaryDns) {
        windowsNetworkService.invalidateAdapterCache();


        boolean success =
                windowsNetworkService
                        .configureStaticIpv4(
                                adapter,
                                ipAddress,
                                subnetMask,
                                defaultGateway,
                                primaryDns,
                                secondaryDns
                        );


        return "redirect:/network?adapter="
                + encodeParameter(adapter)
                + "&staticSuccess="
                + success;
    }
    
    private String encodeParameter(
            String value) {

        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }
    
    /*
     * =========================================================
     * ENABLE ADAPTER
     * =========================================================
     */

    @PostMapping("/network/adapter/enable")
    public String enableAdapter(
            @RequestParam String adapter) {
        windowsNetworkService.invalidateAdapterCache();

        boolean success =
                windowsNetworkService
                        .enableAdapter(adapter);


        return networkRedirect(
                adapter,
                "adapterEnabled",
                success
        );
    }


    /*
     * =========================================================
     * DISABLE ADAPTER
     * =========================================================
     */

    @PostMapping("/network/adapter/disable")
    public String disableAdapter(
            @RequestParam String adapter) {
        windowsNetworkService.invalidateAdapterCache();

        boolean success =
                windowsNetworkService
                        .disableAdapter(adapter);


        return networkRedirect(
                adapter,
                "adapterDisabled",
                success
        );
    }


    /*
     * =========================================================
     * RELEASE DHCP
     * =========================================================
     */

    @PostMapping("/network/dhcp/release")
    public String releaseDhcp(
            @RequestParam String adapter) {
        windowsNetworkService.invalidateAdapterCache();

        boolean success =
                windowsNetworkService
                        .releaseDhcp(adapter);


        return networkRedirect(
                adapter,
                "dhcpReleased",
                success
        );
    }


    /*
     * =========================================================
     * RENEW DHCP
     * =========================================================
     */

    @PostMapping("/network/dhcp/renew")
    public String renewDhcp(
            @RequestParam String adapter) {
        windowsNetworkService.invalidateAdapterCache();

        boolean success =
                windowsNetworkService
                        .renewDhcp(adapter);


        return networkRedirect(
                adapter,
                "dhcpRenewed",
                success
        );
    }


    /*
     * =========================================================
     * FLUSH DNS
     * =========================================================
     */

    @PostMapping("/network/dns/flush")
    public String flushDns(
            @RequestParam String adapter) {
        windowsNetworkService.invalidateAdapterCache();

        boolean success =
                windowsNetworkService
                        .flushDns();


        return networkRedirect(
                adapter,
                "dnsFlushed",
                success
        );
    }


    /*
     * =========================================================
     * RESET NETWORK STACK
     * =========================================================
     */

    @PostMapping("/network/reset")
    public String resetNetworkStack(
            @RequestParam String adapter) {
        windowsNetworkService.invalidateAdapterCache();

        boolean success =
                windowsNetworkService
                        .resetNetworkStack();


        return networkRedirect(
                adapter,
                "networkReset",
                success
        );
    }
    
    /*
     * =========================================================
     * NETWORK REDIRECT
     * =========================================================
     */

    private String networkRedirect(
            String adapter,
            String parameter,
            boolean success) {

        return "redirect:/network?adapter="
                + encodeParameter(adapter)
                + "&"
                + parameter
                + "="
                + success;
    }
}