package com.kkargas.gpm.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.kkargas.gpm.model.NetworkAdapter;


@Service
public class WindowsNetworkService {

    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(WindowsNetworkService.class);

    private String jsonText(com.fasterxml.jackson.databind.JsonNode values, int index) {
        com.fasterxml.jackson.databind.JsonNode node = values.get(index);
        return node == null || node.isNull() ? "" : node.asText("");
    }


    /*
     * =========================================================
     * GET NETWORK ADAPTERS
     * =========================================================
     */

    private final java.util.concurrent.ExecutorService adapterLoader =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "winops-network-adapters");
                thread.setDaemon(true);
                return thread;
            });

    private volatile List<NetworkAdapter> detailedAdapters = List.of();
    private volatile long detailedAt = 0;
    private final java.util.concurrent.atomic.AtomicBoolean loadingAdapters =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final long ADAPTER_CACHE_NANOS =
            java.util.concurrent.TimeUnit.SECONDS.toNanos(15);

    public boolean isAdapterDetailsLoading() {
        return loadingAdapters.get();
    }

    public boolean areAdapterDetailsReady() {
        return detailedAt != 0;
    }

    public void invalidateAdapterCache() {
        detailedAt = 0;
    }

    /** Fast native snapshot, no PowerShell process on the request thread. */
    private List<NetworkAdapter> nativeAdapters() {
        List<NetworkAdapter> result = new ArrayList<>();
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces =
                    java.net.NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return result;
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface network = interfaces.nextElement();
                if (network.isLoopback() || network.isVirtual()) continue;
                NetworkAdapter item = new NetworkAdapter();
                item.setName(network.getDisplayName());
                item.setInterfaceDescription(network.getName());
                item.setStatus(network.isUp() ? "Up" : "Disconnected");
                byte[] mac = network.getHardwareAddress();
                if (mac != null) {
                    StringBuilder value = new StringBuilder();
                    for (byte octet : mac) {
                        if (!value.isEmpty()) value.append('-');
                        value.append(String.format("%02X", octet & 0xff));
                    }
                    item.setMacAddress(value.toString());
                }
                item.setIpv4Address("Not available");
                item.setSubnetMask("Not available");
                item.setDefaultGateway("Not available");
                item.setPrimaryDns("Not available");
                item.setSecondaryDns("Not available");
                for (java.net.InterfaceAddress address : network.getInterfaceAddresses()) {
                    if (address.getAddress() instanceof java.net.Inet4Address) {
                        item.setIpv4Address(address.getAddress().getHostAddress());
                        item.setSubnetMask(prefixToSubnetMask(
                                String.valueOf(address.getNetworkPrefixLength())));
                        break;
                    }
                }
                result.add(item);
            }
        } catch (java.net.SocketException ex) {
            logger.warn("Native network snapshot failed", ex);
        }
        result.sort(java.util.Comparator.comparing(NetworkAdapter::getName,
                String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public List<NetworkAdapter> getNetworkAdaptersFast() {
        long now = System.nanoTime();
        List<NetworkAdapter> cached = detailedAdapters;
        if (detailedAt != 0 && now - detailedAt < ADAPTER_CACHE_NANOS) {
            return cached;
        }
        if (loadingAdapters.compareAndSet(false, true)) {
            adapterLoader.execute(() -> {
                try {
                    List<NetworkAdapter> fresh = getNetworkAdapters();
                    if (!fresh.isEmpty()) {
                        detailedAdapters = List.copyOf(fresh);
                        detailedAt = System.nanoTime();
                    }
                } catch (RuntimeException ex) {
                    logger.warn("Background network refresh failed", ex);
                } finally {
                    loadingAdapters.set(false);
                }
            });
        }
        return detailedAt != 0 ? cached : nativeAdapters();
    }

    public List<NetworkAdapter> getNetworkAdapters() {

        List<NetworkAdapter> adapters =
                new ArrayList<>();


        // One PowerShell process retrieves all adapter details.
        String command = """
                $ErrorActionPreference = 'Stop'
                Get-NetAdapter | Where-Object { $_.HardwareInterface -eq $true } |
                    Sort-Object Name | ForEach-Object {
                        $adapter = $_
                        $config = Get-NetIPConfiguration -InterfaceIndex $adapter.ifIndex -ErrorAction SilentlyContinue
                        $ipv4 = $config.IPv4Address | Select-Object -First 1
                        $gateway = $config.IPv4DefaultGateway | Select-Object -First 1
                        $dns = @($config.DNSServer.ServerAddresses | Where-Object { $_ -match '^\\d+\\.\\d+\\.\\d+\\.\\d+$' })
                        $ipInterface = Get-NetIPInterface -InterfaceIndex $adapter.ifIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue | Select-Object -First 1
                        ConvertTo-Json -InputObject @(
                            $adapter.Name
                            $adapter.InterfaceDescription
                            [string]$adapter.Status
                            $adapter.MacAddress
                            $ipv4.IPAddress
                            $ipv4.PrefixLength
                            $gateway.NextHop
                            $(if ($dns.Count -gt 0) { $dns[0] } else { '' })
                            $(if ($dns.Count -gt 1) { $dns[1] } else { '' })
                            [string]$ipInterface.Dhcp
                        ) -Compress -Depth 3
                    }
                """;

        long started = System.nanoTime();
        List<String> output = executePowerShell(command);
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();

        for (String line : output) {
            try {
                com.fasterxml.jackson.databind.JsonNode values = mapper.readTree(line);
                if (!values.isArray() || values.size() < 10) {
                    continue;
                }
                NetworkAdapter adapter = new NetworkAdapter();
                adapter.setName(jsonText(values, 0));
                adapter.setInterfaceDescription(jsonText(values, 1));
                adapter.setStatus(jsonText(values, 2));
                adapter.setMacAddress(jsonText(values, 3));
                adapter.setIpv4Address(emptyToNotAvailable(jsonText(values, 4)));
                adapter.setSubnetMask(prefixToSubnetMask(jsonText(values, 5)));
                adapter.setDefaultGateway(emptyToNotAvailable(jsonText(values, 6)));
                adapter.setPrimaryDns(emptyToNotAvailable(jsonText(values, 7)));
                adapter.setSecondaryDns(emptyToNotAvailable(jsonText(values, 8)));
                adapter.setDhcpEnabled("Enabled".equalsIgnoreCase(jsonText(values, 9)));
                adapters.add(adapter);
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException("Could not parse network adapter information", ex);
            }
        }

        logger.info("Network adapters collected in {} ms",
                (System.nanoTime() - started) / 1_000_000);

        return adapters;
    }


    /*
     * =========================================================
     * GET NETWORK ADAPTER
     * =========================================================
     */

    public NetworkAdapter getNetworkAdapter(
            String adapterName) {

        return getNetworkAdapters()
                .stream()
                .filter(adapter ->
                        adapter.getName()
                                .equals(adapterName))
                .findFirst()
                .orElse(null);
    }


    /*
     * =========================================================
     * IP CONFIGURATION
     * =========================================================
     */

    private void loadIpConfiguration(
            NetworkAdapter adapter) {

        String safeName =
                escapePowerShell(
                        adapter.getName()
                );


        String command =
                "$config = Get-NetIPConfiguration "
                + "-InterfaceAlias '"
                + safeName
                + "' -ErrorAction SilentlyContinue; "

                + "if ($null -ne $config) { "

                + "$ipv4 = $config.IPv4Address "
                + "| Select-Object -First 1; "

                + "$gateway = $config.IPv4DefaultGateway "
                + "| Select-Object -First 1; "

                + "$dns = $config.DNSServer.ServerAddresses "
                + "| Where-Object { $_ -match '^\\d+\\.\\d+\\.\\d+\\.\\d+$' }; "

                + "[string]::Join('|', @("
                + "$ipv4.IPAddress, "
                + "$ipv4.PrefixLength, "
                + "$gateway.NextHop, "
                + "($dns | Select-Object -First 1), "
                + "($dns | Select-Object -Skip 1 -First 1)"
                + ")) "

                + "}";


        List<String> output =
                executePowerShell(command);


        if (output.isEmpty()) {

            setEmptyIpConfiguration(adapter);

            return;
        }


        String[] values =
                output.get(0)
                        .split("\\|", -1);


        if (values.length < 5) {

            setEmptyIpConfiguration(adapter);

            return;
        }


        adapter.setIpv4Address(
                emptyToNotAvailable(values[0])
        );


        adapter.setSubnetMask(
                prefixToSubnetMask(
                        values[1]
                )
        );


        adapter.setDefaultGateway(
                emptyToNotAvailable(values[2])
        );


        adapter.setPrimaryDns(
                emptyToNotAvailable(values[3])
        );


        adapter.setSecondaryDns(
                emptyToNotAvailable(values[4])
        );
    }


    /*
     * =========================================================
     * DHCP STATE
     * =========================================================
     */

    private void loadDhcpState(
            NetworkAdapter adapter) {

        String safeName =
                escapePowerShell(
                        adapter.getName()
                );


        String command =
                "(Get-NetIPInterface "
                + "-InterfaceAlias '"
                + safeName
                + "' "
                + "-AddressFamily IPv4 "
                + "-ErrorAction SilentlyContinue "
                + "| Select-Object -First 1 "
                + "-ExpandProperty Dhcp)";


        List<String> output =
                executePowerShell(command);


        boolean enabled =
                !output.isEmpty()
                && output.get(0)
                        .trim()
                        .equalsIgnoreCase("Enabled");


        adapter.setDhcpEnabled(enabled);
    }


    /*
     * =========================================================
     * EMPTY IP CONFIGURATION
     * =========================================================
     */

    private void setEmptyIpConfiguration(
            NetworkAdapter adapter) {

        adapter.setIpv4Address(
                "Not available"
        );

        adapter.setSubnetMask(
                "Not available"
        );

        adapter.setDefaultGateway(
                "Not available"
        );

        adapter.setPrimaryDns(
                "Not available"
        );

        adapter.setSecondaryDns(
                "Not available"
        );
    }


    /*
     * =========================================================
     * PREFIX TO SUBNET MASK
     * =========================================================
     */

    private String prefixToSubnetMask(
            String prefixValue) {

        if (prefixValue == null
                || prefixValue.isBlank()) {

            return "Not available";
        }


        try {

            int prefix =
                    Integer.parseInt(
                            prefixValue.trim()
                    );


            if (prefix < 0
                    || prefix > 32) {

                return "Not available";
            }


            long mask =
                    prefix == 0
                    ? 0
                    : 0xFFFFFFFFL
                        << (32 - prefix);


            return String.format(
                    "%d.%d.%d.%d",
                    (mask >>> 24) & 0xFF,
                    (mask >>> 16) & 0xFF,
                    (mask >>> 8) & 0xFF,
                    mask & 0xFF
            );


        } catch (NumberFormatException e) {

            return "Not available";
        }
    }


    /*
     * =========================================================
     * EMPTY VALUE
     * =========================================================
     */

    private String emptyToNotAvailable(
            String value) {

        if (value == null
                || value.isBlank()) {

            return "Not available";
        }


        return value.trim();
    }


    /*
     * =========================================================
     * POWERSHELL ESCAPING
     * =========================================================
     */

    private String escapePowerShell(
            String value) {

        return value.replace(
                "'",
                "''"
        );
    }


    /*
     * =========================================================
     * EXECUTE POWERSHELL
     * =========================================================
     */

    private List<String> executePowerShell(
            String command) {

        List<String> output =
                new ArrayList<>();


        ProcessBuilder processBuilder =
                new ProcessBuilder(
                        "powershell.exe",
                        "-NoProfile",
                        "-NonInteractive",
                        "-Command",
                        command
                );


        processBuilder.redirectErrorStream(
                true
        );


        try {

            Process process =
                    processBuilder.start();


            try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        process.getInputStream(),
                                        StandardCharsets.UTF_8
                                )
                        )
            ) {

                String line;


                while ((line =
                        reader.readLine()) != null) {

                    if (!line.isBlank()) {

                        output.add(line);
                    }
                }
            }


            int exitCode =
                    process.waitFor();


            if (exitCode != 0) {

                throw new RuntimeException(
                        "PowerShell command failed: "
                        + String.join(
                                System.lineSeparator(),
                                output
                        )
                );
            }


            return output;


        } catch (IOException e) {

            throw new RuntimeException(
                    "Failed to execute PowerShell.",
                    e
            );


        } catch (InterruptedException e) {

            Thread.currentThread()
                    .interrupt();


            throw new RuntimeException(
                    "PowerShell execution interrupted.",
                    e
            );
        }
    }
    
    /*
     * =========================================================
     * ENABLE DHCP
     * =========================================================
     */

    public boolean enableDhcp(
            String adapterName) {

        if (!adapterExists(adapterName)) {
            return false;
        }

        String safeName =
                escapePowerShell(adapterName);


        String command =
                "$alias = '" + safeName + "'; "

                + "Set-NetIPInterface "
                + "-InterfaceAlias $alias "
                + "-AddressFamily IPv4 "
                + "-Dhcp Enabled "
                + "-ErrorAction Stop; "

                + "Set-DnsClientServerAddress "
                + "-InterfaceAlias $alias "
                + "-ResetServerAddresses "
                + "-ErrorAction Stop; ";


        return executePowerShellCommand(command);
    }


    /*
     * =========================================================
     * STATIC IPV4 CONFIGURATION
     * =========================================================
     */

    public boolean configureStaticIpv4(
            String adapterName,
            String ipAddress,
            String subnetMask,
            String defaultGateway,
            String primaryDns,
            String secondaryDns) {

        if (!adapterExists(adapterName)) {
            return false;
        }


        if (!isValidIpv4(ipAddress)
                || !isValidSubnetMask(subnetMask)
                || !isValidIpv4(defaultGateway)
                || !isValidIpv4(primaryDns)) {

            return false;
        }


        if (secondaryDns != null
                && !secondaryDns.isBlank()
                && !isValidIpv4(secondaryDns)) {

            return false;
        }


        int prefixLength =
                subnetMaskToPrefix(subnetMask);


        if (prefixLength < 0) {
            return false;
        }


        String safeName =
                escapePowerShell(adapterName);


        StringBuilder command =
                new StringBuilder();


        command.append(
                "$alias = '"
                + safeName
                + "'; "
        );


        /*
         * Disable DHCP first.
         */

        command.append(
                "Set-NetIPInterface "
                + "-InterfaceAlias $alias "
                + "-AddressFamily IPv4 "
                + "-Dhcp Disabled "
                + "-ErrorAction Stop; "
        );


        /*
         * Remove existing manually configured IPv4
         * addresses from this adapter.
         */

        command.append(
                "Get-NetIPAddress "
                + "-InterfaceAlias $alias "
                + "-AddressFamily IPv4 "
                + "-ErrorAction SilentlyContinue "
                + "| Where-Object { "
                + "$_.PrefixOrigin -eq 'Manual' "
                + "} "
                + "| Remove-NetIPAddress "
                + "-Confirm:$false "
                + "-ErrorAction SilentlyContinue; "
        );


        /*
         * Remove existing IPv4 default route.
         */

        command.append(
                "Get-NetRoute "
                + "-InterfaceAlias $alias "
                + "-AddressFamily IPv4 "
                + "-DestinationPrefix '0.0.0.0/0' "
                + "-ErrorAction SilentlyContinue "
                + "| Remove-NetRoute "
                + "-Confirm:$false "
                + "-ErrorAction SilentlyContinue; "
        );


        /*
         * Create static IPv4 configuration.
         */

        command.append(
                "New-NetIPAddress "
                + "-InterfaceAlias $alias "
                + "-IPAddress '"
                + ipAddress
                + "' "
                + "-PrefixLength "
                + prefixLength
                + " "
                + "-DefaultGateway '"
                + defaultGateway
                + "' "
                + "-AddressFamily IPv4 "
                + "-ErrorAction Stop; "
        );


        /*
         * Configure DNS.
         */

        if (secondaryDns != null
                && !secondaryDns.isBlank()) {

            command.append(
                    "Set-DnsClientServerAddress "
                    + "-InterfaceAlias $alias "
                    + "-ServerAddresses @('"
                    + primaryDns
                    + "','"
                    + secondaryDns
                    + "') "
                    + "-ErrorAction Stop; "
            );

        } else {

            command.append(
                    "Set-DnsClientServerAddress "
                    + "-InterfaceAlias $alias "
                    + "-ServerAddresses @('"
                    + primaryDns
                    + "') "
                    + "-ErrorAction Stop; "
            );
        }


        return executePowerShellCommand(
                command.toString()
        );
    }
    
    /*
     * =========================================================
     * ADAPTER VALIDATION
     * =========================================================
     */

    private boolean adapterExists(
            String adapterName) {

        if (adapterName == null
                || adapterName.isBlank()) {

            return false;
        }


        return getNetworkAdapters()
                .stream()
                .anyMatch(adapter ->
                        adapter.getName()
                                .equals(adapterName));
    }


    /*
     * =========================================================
     * IPV4 VALIDATION
     * =========================================================
     */

    private boolean isValidIpv4(
            String address) {

        if (address == null
                || address.isBlank()) {

            return false;
        }


        String[] parts =
                address.trim()
                        .split("\\.", -1);


        if (parts.length != 4) {
            return false;
        }


        for (String part : parts) {

            try {

                if (part.isBlank()) {
                    return false;
                }


                int value =
                        Integer.parseInt(part);


                if (value < 0
                        || value > 255) {

                    return false;
                }

            } catch (NumberFormatException e) {

                return false;
            }
        }


        return true;
    }


    /*
     * =========================================================
     * SUBNET MASK VALIDATION
     * =========================================================
     */

    private boolean isValidSubnetMask(
            String subnetMask) {

        return subnetMaskToPrefix(
                subnetMask
        ) >= 0;
    }


    /*
     * =========================================================
     * SUBNET MASK TO PREFIX
     * =========================================================
     */

    private int subnetMaskToPrefix(
            String subnetMask) {

        if (!isBasicIpv4Format(subnetMask)) {
            return -1;
        }


        String[] parts =
                subnetMask.split("\\.");


        long mask = 0;


        for (String part : parts) {

            mask =
                    (mask << 8)
                    | Integer.parseInt(part);
        }


        boolean zeroFound =
                false;

        int prefix =
                0;


        for (int bit = 31;
             bit >= 0;
             bit--) {

            boolean one =
                    ((mask >> bit) & 1) == 1;


            if (one) {

                if (zeroFound) {
                    return -1;
                }

                prefix++;

            } else {

                zeroFound = true;
            }
        }


        return prefix;
    }


    private boolean isBasicIpv4Format(
            String value) {

        if (value == null
                || value.isBlank()) {

            return false;
        }


        String[] parts =
                value.trim()
                        .split("\\.", -1);


        if (parts.length != 4) {
            return false;
        }


        for (String part : parts) {

            try {

                int number =
                        Integer.parseInt(part);


                if (number < 0
                        || number > 255) {

                    return false;
                }

            } catch (NumberFormatException e) {

                return false;
            }
        }


        return true;
    }
    
    /*
     * =========================================================
     * EXECUTE POWERSHELL COMMAND
     * =========================================================
     */

    private boolean executePowerShellCommand(
            String command) {

        ProcessBuilder processBuilder =
                new ProcessBuilder(
                        "powershell.exe",
                        "-NoProfile",
                        "-NonInteractive",
                        "-Command",
                        command
                );


        processBuilder.redirectErrorStream(true);


        try {

            Process process =
                    processBuilder.start();


            try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        process.getInputStream(),
                                        StandardCharsets.UTF_8
                                )
                        )
            ) {

                while (reader.readLine() != null) {
                    // Consume output.
                }
            }


            int exitCode =
                    process.waitFor();


            return exitCode == 0;


        } catch (IOException e) {

            return false;


        } catch (InterruptedException e) {

            Thread.currentThread()
                    .interrupt();

            return false;
        }
    }
    
    /*
     * =========================================================
     * ENABLE NETWORK ADAPTER
     * =========================================================
     */

    public boolean enableAdapter(
            String adapterName) {

        if (!adapterExists(adapterName)) {
            return false;
        }


        String safeName =
                escapePowerShell(adapterName);


        String command =
                "Enable-NetAdapter "
                + "-Name '" + safeName + "' "
                + "-Confirm:$false "
                + "-ErrorAction Stop";


        return executePowerShellCommand(command);
    }


    /*
     * =========================================================
     * DISABLE NETWORK ADAPTER
     * =========================================================
     */

    public boolean disableAdapter(
            String adapterName) {

        if (!adapterExists(adapterName)) {
            return false;
        }


        String safeName =
                escapePowerShell(adapterName);


        String command =
                "Disable-NetAdapter "
                + "-Name '" + safeName + "' "
                + "-Confirm:$false "
                + "-ErrorAction Stop";


        return executePowerShellCommand(command);
    }


    /*
     * =========================================================
     * RELEASE DHCP
     * =========================================================
     */

    public boolean releaseDhcp(
            String adapterName) {

        if (!adapterExists(adapterName)) {
            return false;
        }


        NetworkAdapter adapter =
                getNetworkAdapter(adapterName);


        if (adapter == null
                || !adapter.isDhcpEnabled()) {

            return false;
        }


        return executeCommand(
                "ipconfig.exe",
                "/release",
                adapterName
        );
    }


    /*
     * =========================================================
     * RENEW DHCP
     * =========================================================
     */

    public boolean renewDhcp(
            String adapterName) {

        if (!adapterExists(adapterName)) {
            return false;
        }


        NetworkAdapter adapter =
                getNetworkAdapter(adapterName);


        if (adapter == null
                || !adapter.isDhcpEnabled()) {

            return false;
        }


        return executeCommand(
                "ipconfig.exe",
                "/renew",
                adapterName
        );
    }


    /*
     * =========================================================
     * FLUSH DNS
     * =========================================================
     */

    public boolean flushDns() {

        return executeCommand(
                "ipconfig.exe",
                "/flushdns"
        );
    }


    /*
     * =========================================================
     * RESET NETWORK STACK
     * =========================================================
     */

    public boolean resetNetworkStack() {

        boolean winsockSuccess =
                executeCommand(
                        "netsh.exe",
                        "winsock",
                        "reset"
                );


        boolean ipSuccess =
                executeCommand(
                        "netsh.exe",
                        "int",
                        "ip",
                        "reset"
                );


        return winsockSuccess
                && ipSuccess;
    }
    
    /*
     * =========================================================
     * READ-ONLY NETWORK DIAGNOSTICS
     * =========================================================
     */

    public String pingHost(String host) {
        String target = validateDiagnosticHost(host);
        return runDiagnostic(15, "ping.exe", "-n", "4", "-w", "1500", target);
    }

    public String lookupDns(String host) {
        String target = validateDiagnosticHost(host);
        return runDiagnostic(15, "nslookup.exe", target);
    }

    public String getIpConfiguration() {
        return runDiagnostic(15, "ipconfig.exe", "/all");
    }

    public void openNetworkConnections() {
        try {
            new ProcessBuilder("control.exe", "ncpa.cpl").start();
        } catch (IOException e) {
            throw new IllegalStateException("Could not open Network Connections.", e);
        }
    }

    private String validateDiagnosticHost(String host) {
        if (host == null) {
            throw new IllegalArgumentException("Enter a host or IP address.");
        }
        String target = host.trim();
        if (target.isEmpty() || target.length() > 253
                || !target.matches("[A-Za-z0-9.:_-]+")
                || target.startsWith("-") || target.startsWith("/")) {
            throw new IllegalArgumentException("Enter a valid hostname or IP address.");
        }
        return target;
    }

    private String runDiagnostic(int timeoutSeconds, String... command) {
        java.nio.file.Path output = null;
        Process process = null;
        try {
            output = java.nio.file.Files.createTempFile("winops-network-", ".txt");
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile())
                    .start();
            boolean finished = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                throw new IllegalStateException("Diagnostic timed out.");
            }
            // Bound the output returned to the browser.
            byte[] bytes;
            try (java.io.InputStream in = java.nio.file.Files.newInputStream(output)) {
                bytes = in.readNBytes(32768);
            }
            String result = new String(bytes, java.nio.charset.Charset.defaultCharset());
            if (process.exitValue() != 0) {
                return "Command exited with code " + process.exitValue() + ".\n" + result;
            }
            return result.isBlank() ? "No output returned." : result;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to run network diagnostic.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            throw new IllegalStateException("Network diagnostic interrupted.", e);
        } finally {
            if (output != null) {
                try { java.nio.file.Files.deleteIfExists(output); }
                catch (IOException ignored) { /* Best effort cleanup. */ }
            }
        }
    }

    /*
     * =========================================================
     * EXECUTE WINDOWS COMMAND
     * =========================================================
     */

    private boolean executeCommand(
            String... command) {

        ProcessBuilder processBuilder =
                new ProcessBuilder(command);


        processBuilder.redirectErrorStream(true);


        try {

            Process process =
                    processBuilder.start();


            try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        process.getInputStream(),
                                        StandardCharsets.UTF_8
                                )
                        )
            ) {

                while (reader.readLine() != null) {
                    // Consume command output.
                }
            }


            int exitCode =
                    process.waitFor();


            return exitCode == 0;


        } catch (IOException e) {

            return false;


        } catch (InterruptedException e) {

            Thread.currentThread()
                    .interrupt();

            return false;
        }
    }
}