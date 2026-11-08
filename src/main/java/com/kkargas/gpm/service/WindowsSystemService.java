package com.kkargas.gpm.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletionException;
import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Service;

import com.kkargas.gpm.model.SystemInformation;

import com.kkargas.gpm.model.WindowsTimeZone;


@Service
public class WindowsSystemService {

    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(WindowsSystemService.class);

    private final ExecutorService systemQueryExecutor = Executors.newFixedThreadPool(4, task -> {
        Thread thread = new Thread(task, "winops-system-query");
        thread.setDaemon(true);
        return thread;
    });

    @PreDestroy
    public void shutdownSystemQueries() {
        systemQueryExecutor.shutdownNow();
    }


    // Time-zone definitions rarely change; avoid starting PowerShell on every visit.
    private volatile List<WindowsTimeZone> cachedTimeZones;



    /*
     * =========================================================
     * GET SYSTEM INFORMATION
     * =========================================================
     */

    public SystemInformation getSystemInformation() {

        SystemInformation information =
                new SystemInformation();


        information.setComputerName(
                getEnvironmentValue(
                        "COMPUTERNAME"
                )
        );


        information.setArchitecture(
                System.getProperty(
                        "os.arch"
                )
        );


        // Independent read-only Windows queries run concurrently.
        // Each task updates distinct fields on this request-local object.
        List<Runnable> queries = List.of(
                () -> loadWindowsInformation(information),
                () -> loadComputerInformation(information),
                () -> loadProcessorInformation(information),
                () -> loadMemoryInformation(information),
                () -> loadBiosInformation(information),
                () -> loadDiskInformation(information),
                () -> loadUptime(information),
                () -> loadTimeZoneInformation(information),
                () -> loadPowerInformation(information)
        );

        long started = System.nanoTime();
        CompletableFuture<?>[] tasks = queries.stream()
                .map(query -> CompletableFuture.runAsync(query, systemQueryExecutor))
                .toArray(CompletableFuture[]::new);

        try {
            CompletableFuture.allOf(tasks).join();
        } catch (CompletionException ex) {
            throw new IllegalStateException("Failed to load Windows system information", ex.getCause());
        }
        logger.info("System information collected in {} ms",
                (System.nanoTime() - started) / 1_000_000);

        return information;
    }
    
    /*
     * =========================================================
     * RENAME COMPUTER
     * =========================================================
     */

    public boolean renameComputer(
            String newComputerName) {

        if (newComputerName == null
                || newComputerName.isBlank()) {

            throw new IllegalArgumentException(
                    "Computer name cannot be empty."
            );
        }


        String name =
                newComputerName.trim();


        if (name.length() > 63) {

            throw new IllegalArgumentException(
                    "Computer name is too long."
            );
        }


        if (!name.matches(
                "^[A-Za-z0-9-]+$")) {

            throw new IllegalArgumentException(
                    "Computer name may contain only "
                    + "letters, numbers and hyphens."
            );
        }


        if (name.matches("^\\d+$")) {

            throw new IllegalArgumentException(
                    "Computer name cannot contain "
                    + "only numbers."
            );
        }


        String currentComputerName =
                System.getenv("COMPUTERNAME");


        if (currentComputerName != null
                && currentComputerName.equalsIgnoreCase(name)) {

            return false;
        }


        String safeName =
                escapePowerShell(name);


        executePowerShell(
                "Rename-Computer "
                + "-NewName '"
                + safeName
                + "' "
                + "-Force"
        );


        return true;
    }
    
    
    private String escapePowerShell(
            String value) {

        return value.replace(
                "'",
                "''"
        );
    }
    
    /*
     * =========================================================
     * CHANGE TIME ZONE
     * =========================================================
     */

    public void changeTimeZone(
            String timeZoneId) {

        if (timeZoneId == null
                || timeZoneId.isBlank()) {

            throw new IllegalArgumentException(
                    "Time zone cannot be empty."
            );
        }


        WindowsTimeZone selectedTimeZone =
                getAvailableTimeZones()
                        .stream()
                        .filter(timeZone ->
                                timeZone.getId()
                                        .equals(timeZoneId))
                        .findFirst()
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Invalid Windows time zone."
                                )
                        );


        String safeId =
                escapePowerShell(
                        selectedTimeZone.getId()
                );


        executePowerShell(
                "Set-TimeZone -Id '"
                + safeId
                + "'"
        );
    }
    
    /*
     * =========================================================
     * AVAILABLE TIME ZONES
     * =========================================================
     */

    public synchronized List<WindowsTimeZone> getAvailableTimeZones() {

        if (cachedTimeZones != null) {
            return cachedTimeZones;
        }


        List<WindowsTimeZone> timeZones =
                new ArrayList<>();


        String command =
                "Get-TimeZone -ListAvailable "
                + "| Sort-Object BaseUtcOffset, DisplayName "
                + "| ForEach-Object { "
                + "[string]::Join('|', @("
                + "$_.Id, "
                + "$_.DisplayName"
                + ")) "
                + "}";


        List<String> output =
                executePowerShell(command);


        for (String line : output) {

            String[] values =
                    line.split("\\|", 2);


            if (values.length >= 2) {

                timeZones.add(
                        new WindowsTimeZone(
                                values[0].trim(),
                                values[1].trim()
                        )
                );
            }
        }


        // Do not cache an unsuccessful/empty response.
        if (!timeZones.isEmpty()) {
            cachedTimeZones = List.copyOf(timeZones);
        }
        return timeZones;
    }
    
    /*
     * =========================================================
     * TIME ZONE INFORMATION
     * =========================================================
     */

    private void loadTimeZoneInformation(
            SystemInformation information) {

        String command =
                "$tz = Get-TimeZone; "
                + "[string]::Join('|', @("
                + "$tz.Id, "
                + "$tz.DisplayName"
                + "))";


        List<String> output =
                executePowerShell(command);


        if (!output.isEmpty()) {

            String[] values =
                    output.get(0)
                            .split("\\|", 2);


            if (values.length >= 2) {

                information.setTimeZoneId(
                        values[0].trim()
                );

                information.setTimeZoneDisplayName(
                        values[1].trim()
                );
            }
        }
    }
    
    /*
     * =========================================================
     * POWER INFORMATION
     * =========================================================
     */

    private void loadPowerInformation(
            SystemInformation information) {

        loadActivePowerPlan(information);
        loadPowerTimeouts(information);
        loadHibernateState(information);
    }


    /*
     * =========================================================
     * ACTIVE POWER PLAN
     * =========================================================
     */

    private void loadActivePowerPlan(
            SystemInformation information) {

        List<String> output =
                executeCommand(
                        "powercfg.exe",
                        "/getactivescheme"
                );


        if (output.isEmpty()) {
            return;
        }


        String line =
                output.get(0).trim();


        /*
         * Typical output:
         *
         * Power Scheme GUID:
         * 381b4222-f694-41f0-9685-ff5bb260df2e
         * (Balanced)
         */

        int guidStart =
                line.indexOf(":");


        int nameStart =
                line.lastIndexOf("(");


        int nameEnd =
                line.lastIndexOf(")");


        if (guidStart >= 0) {

            String remaining =
                    line.substring(
                            guidStart + 1
                    ).trim();


            String[] parts =
                    remaining.split("\\s+");


            if (parts.length > 0) {

                information.setActivePowerPlanGuid(
                        parts[0].trim()
                );
            }
        }


        if (nameStart >= 0
                && nameEnd > nameStart) {

            information.setActivePowerPlan(
                    line.substring(
                            nameStart + 1,
                            nameEnd
                    ).trim()
            );

        } else {

            information.setActivePowerPlan(
                    "Unknown"
            );
        }
    }

    /*
     * =========================================================
     * POWER TIMEOUTS
     * =========================================================
     */

    private void loadPowerTimeouts(
            SystemInformation information) {

        /*
         * Display timeout
         *
         * SUB_VIDEO
         * VIDEOIDLE
         */

        List<String> displayOutput =
                executeCommand(
                        "powercfg.exe",
                        "/query",
                        "SCHEME_CURRENT",
                        "SUB_VIDEO",
                        "VIDEOIDLE"
                );


        int displaySeconds =
                extractCurrentAcPowerValue(
                        displayOutput
                );


        information.setDisplayTimeoutAc(
                displaySeconds / 60
        );


        /*
         * Sleep timeout
         *
         * SUB_SLEEP
         * STANDBYIDLE
         */

        List<String> sleepOutput =
                executeCommand(
                        "powercfg.exe",
                        "/query",
                        "SCHEME_CURRENT",
                        "SUB_SLEEP",
                        "STANDBYIDLE"
                );


        int sleepSeconds =
                extractCurrentAcPowerValue(
                        sleepOutput
                );


        information.setSleepTimeoutAc(
                sleepSeconds / 60
        );
    }
    
    /*
     * =========================================================
     * EXTRACT AC POWER VALUE
     * =========================================================
     */

    private int extractCurrentAcPowerValue(
            List<String> output) {

        for (String line : output) {

            String trimmed =
                    line.trim();


            if (trimmed.startsWith(
                    "Current AC Power Setting Index:")) {

                String value =
                        trimmed.substring(
                                trimmed.indexOf(":") + 1
                        ).trim();


                if (value.startsWith("0x")
                        || value.startsWith("0X")) {

                    value =
                            value.substring(2);
                }


                try {

                    return Integer.parseUnsignedInt(
                            value,
                            16
                    );

                } catch (NumberFormatException e) {

                    throw new RuntimeException(
                            "Invalid power configuration value: "
                            + value,
                            e
                    );
                }
            }
        }


        throw new RuntimeException(
                "Could not read the current AC power setting."
        );
    }


    /*
     * =========================================================
     * HIBERNATE STATE
     * =========================================================
     */

    private void loadHibernateState(
            SystemInformation information) {

        String command =
                "Test-Path "
                + "'C:\\hiberfil.sys'";


        List<String> output =
                executePowerShell(command);


        boolean enabled =
                !output.isEmpty()
                && Boolean.parseBoolean(
                        output.get(0).trim()
                );


        information.setHibernateEnabled(
                enabled
        );
    }


    /*
     * =========================================================
     * WINDOWS INFORMATION
     * =========================================================
     */

    private void loadWindowsInformation(
            SystemInformation information) {

        String command =
                "$os = Get-CimInstance Win32_OperatingSystem; "
                + "[string]::Join('|', @("
                + "$os.Caption, "
                + "$os.Version, "
                + "$os.BuildNumber"
                + "))";


        List<String> output =
                executePowerShell(command);


        if (!output.isEmpty()) {

            String[] values =
                    output.get(0)
                            .split("\\|", -1);


            if (values.length >= 3) {

                information.setWindowsEdition(
                        values[0].trim()
                );

                information.setWindowsVersion(
                        values[1].trim()
                );

                information.setWindowsBuild(
                        values[2].trim()
                );
            }
        }
    }


    /*
     * =========================================================
     * COMPUTER INFORMATION
     * =========================================================
     */

    private void loadComputerInformation(
            SystemInformation information) {

        String command =
                "$computer = "
                + "Get-CimInstance Win32_ComputerSystem; "
                + "[string]::Join('|', @("
                + "$computer.Manufacturer, "
                + "$computer.Model"
                + "))";


        List<String> output =
                executePowerShell(command);


        if (!output.isEmpty()) {

            String[] values =
                    output.get(0)
                            .split("\\|", -1);


            if (values.length >= 2) {

                information.setManufacturer(
                        values[0].trim()
                );

                information.setModel(
                        values[1].trim()
                );
            }
        }
    }


    /*
     * =========================================================
     * PROCESSOR
     * =========================================================
     */

    private void loadProcessorInformation(
            SystemInformation information) {

        String command =
                "(Get-CimInstance Win32_Processor "
                + "| Select-Object -First 1 "
                + "-ExpandProperty Name)";


        List<String> output =
                executePowerShell(command);


        if (!output.isEmpty()) {

            information.setProcessor(
                    output.get(0).trim()
            );
        }
    }


    /*
     * =========================================================
     * MEMORY
     * =========================================================
     */

    private void loadMemoryInformation(
            SystemInformation information) {

        String command =
                "$memory = "
                + "(Get-CimInstance "
                + "Win32_ComputerSystem)"
                + ".TotalPhysicalMemory; "
                + "[math]::Round("
                + "$memory / 1GB, 2)";


        List<String> output =
                executePowerShell(command);


        if (!output.isEmpty()) {

            information.setInstalledRam(
                    output.get(0).trim()
                    + " GB"
            );
        }
    }


    /*
     * =========================================================
     * BIOS
     * =========================================================
     */

    private void loadBiosInformation(
            SystemInformation information) {

        String command =
                "(Get-CimInstance Win32_BIOS "
                + "| Select-Object -First 1 "
                + "-ExpandProperty SMBIOSBIOSVersion)";


        List<String> output =
                executePowerShell(command);


        if (!output.isEmpty()) {

            information.setBiosVersion(
                    output.get(0).trim()
            );
        }
    }


    /*
     * =========================================================
     * SYSTEM DRIVE
     * =========================================================
     */

    private void loadDiskInformation(
            SystemInformation information) {

        String command =
                "$os = Get-CimInstance "
                + "Win32_OperatingSystem; "
                + "$drive = Get-CimInstance "
                + "Win32_LogicalDisk "
                + "-Filter \"DeviceID='$($os.SystemDrive)'\"; "
                + "[string]::Join('|', @("
                + "$os.SystemDrive, "
                + "[math]::Round("
                + "$drive.FreeSpace / 1GB, 2)"
                + "))";


        List<String> output =
                executePowerShell(command);


        if (!output.isEmpty()) {

            String[] values =
                    output.get(0)
                            .split("\\|", -1);


            if (values.length >= 2) {

                information.setSystemDrive(
                        values[0].trim()
                );

                information.setSystemDriveFreeSpace(
                        values[1].trim()
                        + " GB"
                );
            }
        }
    }


    /*
     * =========================================================
     * UPTIME
     * =========================================================
     */

    private void loadUptime(
            SystemInformation information) {

        String command =
                "$os = Get-CimInstance Win32_OperatingSystem; "
                + "$uptime = (Get-Date) - $os.LastBootUpTime; "
                + "Write-Output ("
                + "$uptime.Days.ToString() + 'd ' + "
                + "$uptime.Hours.ToString() + 'h ' + "
                + "$uptime.Minutes.ToString() + 'm'"
                + ")";


        List<String> output =
                executePowerShell(command);


        if (!output.isEmpty()) {

            information.setUptime(
                    output.get(0).trim()
            );
        }
    }


    /*
     * =========================================================
     * OPEN MSCONFIG
     * =========================================================
     */

    public void openMsConfig() {

        startProcess(
                "msconfig.exe"
        );
    }


    /*
     * =========================================================
     * OPEN ENVIRONMENT VARIABLES
     * =========================================================
     */

    public void openEnvironmentVariables() {

        startProcess(
                "SystemPropertiesAdvanced.exe"
        );
    }
    
    /*
     * =========================================================
     * OPEN DEBLOATING
     * =========================================================
     */

    public void openDebloating() {

        startProcess(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                "iwr -useb https://christitus.com/win | iex"
        );
    }
    
    /*
     * =========================================================
     * RESTART TO UEFI FIRMWARE
     * =========================================================
     */

    public void restartToFirmware() {

        startProcess(
                "shutdown.exe",
                "/r",
                "/fw",
                "/t",
                "0"
        );
    }
    
    /*
     * =========================================================
     * OPEN SYSTEM RESTORE
     * =========================================================
     */

    public void openSystemRestore() {

        startProcess(
                "rstrui.exe"
        );
    }
    
    /*
     * =========================================================
     * OPEN SYSTEM PROTECTION
     * =========================================================
     */

    public void openSystemProtection() {

        startProcess(
                "SystemPropertiesProtection.exe"
        );
    }


    /*
     * =========================================================
     * RUN SYSTEM FILE CHECKER
     * =========================================================
     */

    public void runSystemFileChecker() {

        startProcess(
                "cmd.exe",
                "/c",
                "start",
                "\"System File Checker\"",
                "cmd.exe",
                "/k",
                "sfc /scannow"
        );
    }


    /*
     * =========================================================
     * RUN DISM RESTORE HEALTH
     * =========================================================
     */

    public void runDismRestoreHealth() {

        startProcess(
                "cmd.exe",
                "/c",
                "start",
                "\"DISM Restore Health\"",
                "cmd.exe",
                "/k",
                "DISM /Online /Cleanup-Image /RestoreHealth"
        );
    }
    
    /*
     * =========================================================
     * SYSTEM ACTIONS
     * =========================================================
     */

    public void lockComputer() {

        startProcess(
                "rundll32.exe",
                "user32.dll,LockWorkStation"
        );
    }


    public void sleepComputer() {

        executePowerShell(
                "Add-Type -AssemblyName System.Windows.Forms; "
                + "[System.Windows.Forms.Application]::SetSuspendState("
                + "'Suspend', $false, $false)"
        );
    }


    public void restartComputer() {

        startProcess(
                "shutdown.exe",
                "/r",
                "/t",
                "0"
        );
    }


    public void shutdownComputer() {

        startProcess(
                "shutdown.exe",
                "/s",
                "/t",
                "0"
        );
    }


    /*
     * =========================================================
     * START WINDOWS PROCESS
     * =========================================================
     */

    private void startProcess(
            String... command) {

        try {

            new ProcessBuilder(command)
                    .start();

        } catch (IOException e) {

            throw new RuntimeException(
                    "Failed to start Windows process.",
                    e
            );
        }
    }


    /*
     * =========================================================
     * ENVIRONMENT VALUE
     * =========================================================
     */

    private String getEnvironmentValue(
            String name) {

        String value =
                System.getenv(name);


        if (value == null
                || value.isBlank()) {

            return "Unknown";
        }


        return value;
    }
    
    /*
     * =========================================================
     * EXECUTE WINDOWS COMMAND
     * =========================================================
     */

    private List<String> executeCommand(
            String... command) {

        List<String> output =
                new ArrayList<>();


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

                String line;


                while ((line = reader.readLine()) != null) {

                    if (!line.isBlank()) {
                        output.add(line);
                    }
                }
            }


            int exitCode =
                    process.waitFor();


            if (exitCode != 0) {

                throw new RuntimeException(
                        "Windows command failed: "
                        + String.join(
                                System.lineSeparator(),
                                output
                        )
                );
            }


            return output;


        } catch (IOException e) {

            throw new RuntimeException(
                    "Failed to execute Windows command.",
                    e
            );


        } catch (InterruptedException e) {

            Thread.currentThread()
                    .interrupt();


            throw new RuntimeException(
                    "Windows command interrupted.",
                    e
            );
        }
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

                    output.add(line);
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
     * CHANGE POWER PLAN
     * =========================================================
     */

    public void changePowerPlan(
            String powerPlan) {

        String guid;


        switch (powerPlan) {

            case "BALANCED" ->
                    guid =
                        "381b4222-f694-41f0-9685-ff5bb260df2e";

            case "HIGH_PERFORMANCE" ->
                    guid =
                        "8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c";

            case "POWER_SAVER" ->
                    guid =
                        "a1841308-3541-4fab-bc81-f71556f20b4a";

            default ->
                    throw new IllegalArgumentException(
                            "Invalid power plan."
                    );
        }


        executeCommand(
                "powercfg.exe",
                "/setactive",
                guid
        );
    }
    
    /*
     * =========================================================
     * DISPLAY TIMEOUT
     * =========================================================
     */

    public void changeDisplayTimeout(
            int minutes) {

        validateTimeout(minutes);


        executeCommand(
                "powercfg.exe",
                "/change",
                "monitor-timeout-ac",
                String.valueOf(minutes)
        );
    }
    
    /*
     * =========================================================
     * SLEEP TIMEOUT
     * =========================================================
     */

    public void changeSleepTimeout(
            int minutes) {

        validateTimeout(minutes);


        executeCommand(
                "powercfg.exe",
                "/change",
                "standby-timeout-ac",
                String.valueOf(minutes)
        );
    }
    
    private void validateTimeout(
            int minutes) {

        if (minutes < 0
                || minutes > 1440) {

            throw new IllegalArgumentException(
                    "Timeout must be between "
                    + "0 and 1440 minutes."
            );
        }
    }
    
    /*
     * =========================================================
     * HIBERNATE
     * =========================================================
     */

    public void setHibernateEnabled(
            boolean enabled) {

        executeCommand(
                "powercfg.exe",
                "/hibernate",
                enabled ? "on" : "off"
        );
    }
}