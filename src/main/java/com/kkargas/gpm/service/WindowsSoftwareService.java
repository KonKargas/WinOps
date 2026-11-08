package com.kkargas.gpm.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Service;

import com.kkargas.gpm.model.InstalledApplication;
import com.kkargas.gpm.model.SoftwarePackage;
import com.kkargas.gpm.model.SoftwareInstallation;
import com.kkargas.gpm.model.SoftwareUninstallation;
import com.kkargas.gpm.model.SoftwareUpdate;
import com.kkargas.gpm.model.SoftwareInstallation.Status;


@Service
public class WindowsSoftwareService {

    private static final long WINGET_CHECK_TTL_NANOS =
            java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
    private volatile long wingetCheckExpiresAt;
    private volatile Boolean cachedWingetAvailable;


    private final Map<String, SoftwareInstallation> installations =
            new ConcurrentHashMap<>();

    private final ExecutorService installationExecutor =
            Executors.newFixedThreadPool(2);


    private final Map<String, SoftwareUninstallation> uninstallations =
            new ConcurrentHashMap<>();

    private final ExecutorService uninstallationExecutor =
            Executors.newFixedThreadPool(2);


    private static final String UPDATE_ALL_JOB_ID =
            "__UPDATE_ALL__";

    private final Map<String, SoftwareUpdate> updates =
            new ConcurrentHashMap<>();

    private final ExecutorService updateExecutor =
            Executors.newFixedThreadPool(2);



    /*
     * =========================================================
     * WINGET AVAILABLE
     * =========================================================
     */

    public synchronized boolean isWingetAvailable() {

        long now = System.nanoTime();
        if (cachedWingetAvailable != null
                && now - wingetCheckExpiresAt < 0) {
            return cachedWingetAvailable;
        }
        boolean available = probeWingetAvailability();
        cachedWingetAvailable = available;
        wingetCheckExpiresAt = System.nanoTime() + WINGET_CHECK_TTL_NANOS;
        return available;
    }

    private boolean probeWingetAvailability() {

        try {

            ProcessBuilder processBuilder =
                    new ProcessBuilder(
                            "winget.exe",
                            "--version"
                    );


            processBuilder.redirectErrorStream(
                    true
            );


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
     * WINGET VERSION
     * =========================================================
     */

    public String getWingetVersion() {

        if (!isWingetAvailable()) {
            return "Not available";
        }


        List<String> output =
                executeWinget(
                        "--version"
                );


        if (output.isEmpty()) {
            return "Unknown";
        }


        return output.get(0).trim();
    }


    /*
     * =========================================================
     * INSTALLED APPLICATIONS
     * =========================================================
     */

    public List<InstalledApplication>
            getInstalledApplications() {

        List<InstalledApplication> applications =
                new ArrayList<>();


        if (!isWingetAvailable()) {
            return applications;
        }


        List<String> output =
                executeWinget(
                        "list",
                        "--accept-source-agreements",
                        "--disable-interactivity"
                );


        boolean tableStarted =
                false;


        for (String line : output) {

            if (line == null
                    || line.isBlank()) {

                continue;
            }


            String trimmed =
                    line.trim();


            /*
             * WinGet prints a separator below
             * the table headers.
             */

            if (!tableStarted) {

                if (trimmed.matches("-{5,}.*")) {

                    tableStarted = true;
                }

                continue;
            }


            InstalledApplication application =
                    parseInstalledApplication(
                            line
                    );


            if (application != null) {

                applications.add(
                        application
                );
            }
        }


        return applications;
    }


    /*
     * =========================================================
     * PARSE INSTALLED APPLICATION
     * =========================================================
     */

    private InstalledApplication
            parseInstalledApplication(
                    String line) {

        /*
         * Typical WinGet output:
         *
         * Name          Id             Version     Available Source
         * ---------------------------------------------------------
         * Git           Git.Git        2.50.1      2.51.0    winget
         *
         * WinGet uses aligned columns rather than
         * a machine-readable format here.
         *
         * Splitting on 2+ spaces keeps application
         * names containing normal spaces intact.
         */

        String[] columns =
                line.trim()
                        .split("\\s{2,}");


        if (columns.length < 3) {
            return null;
        }


        String name =
                columns[0].trim();

        String id =
                columns[1].trim();

        String version =
                columns[2].trim();


        String availableVersion =
                null;

        String source =
                null;


        /*
         * Five columns:
         *
         * Name
         * Id
         * Version
         * Available
         * Source
         */

        if (columns.length >= 5) {

            availableVersion =
                    columns[3].trim();

            source =
                    columns[4].trim();
        }


        /*
         * Four columns normally means:
         *
         * Name
         * Id
         * Version
         * Source
         */

        else if (columns.length == 4) {

            source =
                    columns[3].trim();
        }


        return new InstalledApplication(
                id,
                name,
                version,
                availableVersion,
                source
        );
    }


    /*
     * =========================================================
     * COUNT AVAILABLE UPDATES
     * =========================================================
     */

    public long getAvailableUpdateCount(
            List<InstalledApplication> applications) {

        return applications.stream()
                .filter(
                        InstalledApplication
                                ::isUpdateAvailable
                )
                .count();
    }


    /*
     * =========================================================
     * WINGET SOURCES
     * =========================================================
     */

    public List<String> getWingetSources() {

        List<String> sources =
                new ArrayList<>();


        if (!isWingetAvailable()) {
            return sources;
        }


        List<String> output =
                executeWinget(
                        "source",
                        "list",
                        "--disable-interactivity"
                );


        boolean tableStarted =
                false;


        for (String line : output) {

            if (line == null
                    || line.isBlank()) {

                continue;
            }


            String trimmed =
                    line.trim();


            if (!tableStarted) {

                if (trimmed.matches("-{5,}.*")) {

                    tableStarted = true;
                }

                continue;
            }


            String[] columns =
                    trimmed.split("\\s{2,}");


            if (columns.length > 0
                    && !columns[0].isBlank()) {

                sources.add(
                        columns[0].trim()
                );
            }
        }


        return sources;
    }


    /*
     * =========================================================
     * EXECUTE WINGET
     * =========================================================
     */

    private List<String> executeWinget(
            String... arguments) {

        List<String> command =
                new ArrayList<>();


        command.add(
                "winget.exe"
        );


        for (String argument : arguments) {

            command.add(argument);
        }


        ProcessBuilder processBuilder =
                new ProcessBuilder(command);


        processBuilder.redirectErrorStream(
                true
        );


        List<String> output =
                new ArrayList<>();


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
                        "WinGet command failed."
                );
            }


            return output;


        } catch (IOException e) {

            throw new RuntimeException(
                    "Failed to execute WinGet.",
                    e
            );


        } catch (InterruptedException e) {

            Thread.currentThread()
                    .interrupt();


            throw new RuntimeException(
                    "WinGet execution interrupted.",
                    e
            );
        }
    }
    
    /*
     * =========================================================
     * SOFTWARE CATALOG
     * =========================================================
     */

    public List<SoftwarePackage> getSoftwareCatalog() {
        return getSoftwareCatalog(getInstalledApplications());
    }

    public List<SoftwarePackage> getSoftwareCatalog(
            List<InstalledApplication> installed) {
        List<SoftwarePackage> packages = createSoftwareCatalog();
        markInstalledPackages(packages, installed);
        return packages;
    }
    
    /*
     * =========================================================
     * MARK INSTALLED PACKAGES
     * =========================================================
     */

    private void markInstalledPackages(
            List<SoftwarePackage> packages,
            List<InstalledApplication> installed) {


        for (SoftwarePackage softwarePackage : packages) {

            boolean installedPackage =
                    installed.stream()
                            .anyMatch(app ->
                                    app.getId() != null
                                    && app.getId()
                                            .equalsIgnoreCase(
                                                    softwarePackage.getId()
                                            )
                            );


            softwarePackage.setInstalled(
                    installedPackage
            );
        }
    }
    
    /*
     * =========================================================
     * INSTALL SOFTWARE PACKAGE
     * =========================================================
     */

    public SoftwareInstallation startInstallation(String packageId) {
        SoftwarePackage softwarePackage = findCatalogPackage(packageId);
        if (softwarePackage == null) return null;

        SoftwareInstallation existing = installations.get(packageId);
        if (existing != null && !existing.isFinished()) return existing;

        SoftwareInstallation installation =
                new SoftwareInstallation(softwarePackage.getId(), softwarePackage.getName());

        installations.put(packageId, installation);
        installationExecutor.submit(() -> runInstallation(softwarePackage, installation));
        return installation;
    }

    public SoftwareInstallation getInstallation(String packageId) {
        return installations.get(packageId);
    }

    private void runInstallation(SoftwarePackage softwarePackage,
                                 SoftwareInstallation installation) {
        installation.setStatus(Status.PREPARING);
        installation.setMessage("Preparing installation...");

        boolean success = executeWingetInstallation(softwarePackage, installation);

        if (success) {
            installation.setStatus(Status.COMPLETED);
            installation.setMessage("Installed successfully.");
        } else {
            installation.setStatus(Status.FAILED);
            if (installation.getMessage() == null
                    || installation.getMessage().isBlank()
                    || installation.getMessage().equals("Installing application...")) {
                installation.setMessage("Installation failed.");
            }
        }
    }


    /*
     * =========================================================
     * UNINSTALL SOFTWARE PACKAGE
     * =========================================================
     */

    public SoftwareUninstallation startUninstallation(
            String packageId) {

        InstalledApplication application =
                findInstalledApplication(packageId);

        if (application == null) {
            return null;
        }

        SoftwareUninstallation existing =
                uninstallations.get(packageId);

        if (existing != null
                && !existing.isFinished()) {

            return existing;
        }

        SoftwareUninstallation uninstallation =
                new SoftwareUninstallation(
                        application.getId(),
                        application.getName()
                );

        uninstallations.put(
                packageId,
                uninstallation
        );

        uninstallationExecutor.submit(
                () -> runUninstallation(
                        application,
                        uninstallation
                )
        );

        return uninstallation;
    }

    public SoftwareUninstallation getUninstallation(
            String packageId) {

        return uninstallations.get(packageId);
    }

    private InstalledApplication findInstalledApplication(
            String packageId) {

        if (packageId == null
                || packageId.isBlank()) {

            return null;
        }

        return getInstalledApplications()
                .stream()
                .filter(application ->
                        application.getId() != null
                        && application.getId()
                                .equalsIgnoreCase(packageId)
                )
                .findFirst()
                .orElse(null);
    }

    private void runUninstallation(
            InstalledApplication application,
            SoftwareUninstallation uninstallation) {

        uninstallation.setStatus(
                SoftwareUninstallation.Status.PREPARING
        );

        uninstallation.setMessage(
                "Preparing removal..."
        );

        boolean success =
                executeWingetUninstallation(
                        application,
                        uninstallation
                );

        if (success) {

            uninstallation.setStatus(
                    SoftwareUninstallation.Status.COMPLETED
            );

            uninstallation.setMessage(
                    "Removed successfully."
            );

        } else {

            uninstallation.setStatus(
                    SoftwareUninstallation.Status.FAILED
            );

            if (uninstallation.getMessage() == null
                    || uninstallation.getMessage().isBlank()
                    || uninstallation.getMessage()
                            .equals("Uninstalling application...")) {

                uninstallation.setMessage(
                        "Uninstallation failed."
                );
            }
        }
    }

    private boolean executeWingetUninstallation(
            InstalledApplication application,
            SoftwareUninstallation uninstallation) {

        List<String> command =
                new ArrayList<>();

        command.add("winget.exe");
        command.add("uninstall");
        command.add("--id");
        command.add(application.getId());
        command.add("--exact");
        command.add("--silent");
        command.add("--accept-source-agreements");
        command.add("--disable-interactivity");

        /*
         * If WinGet reported a concrete source for this installed
         * package, keep the uninstall lookup scoped to that source.
         */
        if (application.getSource() != null
                && !application.getSource().isBlank()
                && !application.getSource()
                        .equalsIgnoreCase("Unknown")) {

            command.add("--source");
            command.add(application.getSource());
        }

        System.out.println();
        System.out.println("========== WINGET UNINSTALL ==========");
        System.out.println(String.join(" ", command));
        System.out.println("======================================");

        ProcessBuilder processBuilder =
                new ProcessBuilder(command);

        processBuilder.redirectErrorStream(true);

        try {

            Process process =
                    processBuilder.start();

            uninstallation.setStatus(
                    SoftwareUninstallation.Status.UNINSTALLING
            );

            uninstallation.setMessage(
                    "Uninstalling application..."
            );

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

                    System.out.println(
                            "[WinGet Uninstall] " + line
                    );

                    updateUninstallationState(
                            uninstallation,
                            line
                    );
                }
            }

            int exitCode =
                    process.waitFor();

            System.out.println(
                    "[WinGet Uninstall] Exit code: "
                    + exitCode
            );

            System.out.println(
                    "======================================"
            );

            return exitCode == 0;

        } catch (IOException e) {

            uninstallation.setMessage(
                    "Failed to start Windows Package Manager."
            );

            e.printStackTrace();

            return false;

        } catch (InterruptedException e) {

            Thread.currentThread()
                    .interrupt();

            uninstallation.setMessage(
                    "Uninstallation interrupted."
            );

            return false;
        }
    }

    private void updateUninstallationState(
            SoftwareUninstallation uninstallation,
            String outputLine) {

        if (outputLine == null
                || outputLine.isBlank()) {

            return;
        }

        String normalized =
                outputLine.toLowerCase();

        if (normalized.contains("uninstall")
                || normalized.contains("removing")
                || normalized.contains("successfully uninstalled")) {

            uninstallation.setStatus(
                    SoftwareUninstallation.Status.UNINSTALLING
            );

            uninstallation.setMessage(
                    "Uninstalling application..."
            );
        }
    }


    /*
     * =========================================================
     * SOFTWARE UPDATES
     * =========================================================
     */

    public SoftwareUpdate startUpdate(
            String packageId) {

        InstalledApplication application =
                findInstalledApplication(packageId);

        if (application == null
                || !application.isUpdateAvailable()) {

            return null;
        }

        SoftwareUpdate existing =
                updates.get(packageId);

        if (existing != null
                && !existing.isFinished()) {

            return existing;
        }

        SoftwareUpdate update =
                new SoftwareUpdate(
                        packageId,
                        application.getId(),
                        application.getName(),
                        false
                );

        updates.put(
                packageId,
                update
        );

        updateExecutor.submit(
                () -> runPackageUpdate(
                        application,
                        update
                )
        );

        return update;
    }

    public SoftwareUpdate getUpdate(
            String packageId) {

        return updates.get(packageId);
    }

    public SoftwareUpdate startUpdateAll() {

        List<InstalledApplication> applications =
                getInstalledApplications();

        boolean updatesAvailable =
                applications.stream()
                        .anyMatch(
                                InstalledApplication
                                        ::isUpdateAvailable
                        );

        if (!updatesAvailable) {
            return null;
        }

        SoftwareUpdate existing =
                updates.get(UPDATE_ALL_JOB_ID);

        if (existing != null
                && !existing.isFinished()) {

            return existing;
        }

        SoftwareUpdate update =
                new SoftwareUpdate(
                        UPDATE_ALL_JOB_ID,
                        null,
                        "All applications",
                        true
                );

        updates.put(
                UPDATE_ALL_JOB_ID,
                update
        );

        updateExecutor.submit(
                () -> runUpdateAll(update)
        );

        return update;
    }

    public SoftwareUpdate getUpdateAll() {

        return updates.get(
                UPDATE_ALL_JOB_ID
        );
    }

    private void runPackageUpdate(
            InstalledApplication application,
            SoftwareUpdate update) {

        update.setStatus(
                SoftwareUpdate.Status.PREPARING
        );

        update.setMessage(
                "Preparing update..."
        );

        boolean success =
                executeWingetUpdate(
                        application,
                        update
                );

        if (success) {

            update.setStatus(
                    SoftwareUpdate.Status.COMPLETED
            );

            update.setMessage(
                    "Updated successfully."
            );

        } else {

            update.setStatus(
                    SoftwareUpdate.Status.FAILED
            );

            String currentMessage =
                    update.getMessage();

            if (currentMessage == null
                    || currentMessage.isBlank()
                    || currentMessage.equals("Preparing update...")
                    || currentMessage.equals("Updating application...")) {

                update.setMessage(
                        "The update could not be completed."
                );
            }
        }
    }

    private void runUpdateAll(
            SoftwareUpdate update) {

        update.setStatus(
                SoftwareUpdate.Status.PREPARING
        );

        update.setMessage(
                "Preparing all updates..."
        );

        boolean success =
                executeWingetUpdateAll(update);

        if (success) {

            update.setStatus(
                    SoftwareUpdate.Status.COMPLETED
            );

            update.setMessage(
                    "All available updates completed."
            );

        } else {

            update.setStatus(
                    SoftwareUpdate.Status.FAILED
            );

            if (update.getMessage() == null
                    || update.getMessage().isBlank()
                    || update.getMessage()
                            .equals("Updating applications...")) {

                update.setMessage(
                        "Update All failed."
                );
            }
        }
    }

    private boolean executeWingetUpdate(
            InstalledApplication application,
            SoftwareUpdate update) {

        List<String> command =
                new ArrayList<>();

        command.add("winget.exe");
        command.add("upgrade");
        command.add("--id");
        command.add(application.getId());
        command.add("--exact");

        if (application.getSource() != null
                && !application.getSource().isBlank()
                && !application.getSource()
                        .equalsIgnoreCase("Unknown")) {

            command.add("--source");
            command.add(application.getSource());
        }

        command.add("--silent");
        command.add("--accept-source-agreements");
        command.add("--accept-package-agreements");
        command.add("--disable-interactivity");

        return executeUpdateProcess(
                command,
                update,
                "application"
        );
    }

    private boolean executeWingetUpdateAll(
            SoftwareUpdate update) {

        List<String> command =
                new ArrayList<>();

        command.add("winget.exe");
        command.add("upgrade");
        command.add("--all");
        command.add("--silent");
        command.add("--accept-source-agreements");
        command.add("--accept-package-agreements");
        command.add("--disable-interactivity");

        return executeUpdateProcess(
                command,
                update,
                "applications"
        );
    }

    private boolean executeUpdateProcess(
            List<String> command,
            SoftwareUpdate update,
            String targetLabel) {

        System.out.println();
        System.out.println("========== WINGET UPDATE ==========");
        System.out.println(String.join(" ", command));
        System.out.println("===================================");

        ProcessBuilder processBuilder =
                new ProcessBuilder(command);

        processBuilder.redirectErrorStream(true);

        try {

            Process process =
                    processBuilder.start();

            update.setStatus(
                    SoftwareUpdate.Status.UPDATING
            );

            update.setMessage(
                    targetLabel.equals("applications")
                            ? "Updating applications..."
                            : "Updating application..."
            );

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

                    System.out.println(
                            "[WinGet Update] " + line
                    );

                    updateUpdateState(
                            update,
                            line,
                            targetLabel
                    );
                }
            }

            int exitCode =
                    process.waitFor();

            System.out.println(
                    "[WinGet Update] Exit code: "
                    + exitCode
            );

            System.out.println(
                    "==================================="
            );

            return exitCode == 0;

        } catch (IOException e) {

            update.setMessage(
                    "Failed to start Windows Package Manager."
            );

            e.printStackTrace();

            return false;

        } catch (InterruptedException e) {

            Thread.currentThread()
                    .interrupt();

            update.setMessage(
                    "Update interrupted."
            );

            return false;
        }
    }

    private void updateUpdateState(
            SoftwareUpdate update,
            String outputLine,
            String targetLabel) {

        if (outputLine == null
                || outputLine.isBlank()) {

            return;
        }

        String normalized =
                outputLine.toLowerCase();


        /*
         * =========================================================
         * SPECIAL WINGET UPDATE ERRORS
         * =========================================================
         */

        if (normalized.contains(
                "install technology is different")) {

            update.setMessage(
                    "Reinstallation required. "
                    + "The newer version uses a different installation "
                    + "technology. Uninstall the current version and "
                    + "then install the latest version."
            );

            return;
        }


        if (normalized.contains(
                "please uninstall the package and install the newer version")) {

            update.setMessage(
                    "Reinstallation required. "
                    + "Uninstall the current version and then install "
                    + "the latest version."
            );

            return;
        }


        /*
         * =========================================================
         * COMMON WINGET ERRORS
         * =========================================================
         */

        if (normalized.contains("no applicable upgrade found")
                || normalized.contains("no available upgrade found")
                || normalized.contains("no newer package versions are available")) {

            update.setMessage(
                    "No applicable update is currently available."
            );

            return;
        }


        if (normalized.contains("installer failed")
                || normalized.contains("installation failed")) {

            update.setMessage(
                    "The application installer failed while applying the update."
            );

            return;
        }


        if (normalized.contains("access is denied")
                || normalized.contains("access denied")) {

            update.setMessage(
                    "Access was denied while applying the update."
            );

            return;
        }


        if (normalized.contains("another installation is already in progress")) {

            update.setMessage(
                    "Another installation is already in progress."
            );

            return;
        }


        /*
         * =========================================================
         * NORMAL UPDATE PROGRESS
         * =========================================================
         */

        if (normalized.contains("download")
                || normalized.contains("install")
                || normalized.contains("upgrade")
                || normalized.contains("successfully")) {

            update.setStatus(
                    SoftwareUpdate.Status.UPDATING
            );

            /*
             * Do not overwrite a specific error message
             * that may already have been detected.
             */

            String currentMessage =
                    update.getMessage();

            if (currentMessage == null
                    || currentMessage.isBlank()
                    || currentMessage.equals("Preparing update...")
                    || currentMessage.equals("Preparing all updates...")
                    || currentMessage.equals("Updating application...")
                    || currentMessage.equals("Updating applications...")) {

                update.setMessage(
                        targetLabel.equals("applications")
                                ? "Updating applications..."
                                : "Updating application..."
                );
            }
        }
    }


    /*
     * =========================================================
     * ACTIVE SOFTWARE OPERATIONS
     * =========================================================
     */

    public Map<String, Object> getActiveOperations() {

        Map<String, Object> result = new HashMap<>();

        result.put(
                "installations",
                installations.values().stream()
                        .filter(operation -> !operation.isFinished())
                        .toList()
        );

        result.put(
                "uninstallations",
                uninstallations.values().stream()
                        .filter(operation -> !operation.isFinished())
                        .toList()
        );

        result.put(
                "updates",
                updates.values().stream()
                        .filter(operation -> !operation.isFinished())
                        .toList()
        );

        return result;
    }


    /*
     * =========================================================
     * FIND CATALOG PACKAGE
     * =========================================================
     */

    private SoftwarePackage findCatalogPackage(
            String packageId) {

        if (packageId == null
                || packageId.isBlank()) {

            return null;
        }


        return createSoftwareCatalog()
                .stream()
                .filter(softwarePackage ->
                        softwarePackage.getId()
                                .equals(packageId)
                )
                .findFirst()
                .orElse(null);
    }
    
    private List<SoftwarePackage> createSoftwareCatalog() {

        List<SoftwarePackage> packages =
                new ArrayList<>();



        /*
         * Browsers
         */

        packages.add(
                new SoftwarePackage(
                        "Google.Chrome",
                        "Google Chrome",
                        "Browsers",
                        "Google web browser."
                )
        );

        packages.add(
                new SoftwarePackage(
                        "Mozilla.Firefox",
                        "Mozilla Firefox",
                        "Browsers",
                        "Open-source web browser."
                )
        );


        /*
         * Development
         */

        packages.add(
                new SoftwarePackage(
                        "Git.Git",
                        "Git",
                        "Development",
                        "Distributed version control system."
                )
        );

        packages.add(
                new SoftwarePackage(
                        "Microsoft.VisualStudioCode",
                        "Visual Studio Code",
                        "Development",
                        "Lightweight source code editor."
                )
        );

        packages.add(
                new SoftwarePackage(
                        "Notepad++.Notepad++",
                        "Notepad++",
                        "Development",
                        "Lightweight text and source code editor."
                )
        );


        /*
         * Utilities
         */

        packages.add(
                new SoftwarePackage(
                        "7zip.7zip",
                        "7-Zip",
                        "Utilities",
                        "File compression and archive utility."
                )
        );

        packages.add(
                new SoftwarePackage(
                        "Microsoft.PowerToys",
                        "Microsoft PowerToys",
                        "Utilities",
                        "Advanced Windows productivity utilities."
                )
        );

        packages.add(
                new SoftwarePackage(
                        "voidtools.Everything",
                        "Everything",
                        "Utilities",
                        "Fast Windows file search utility."
                )
        );


        /*
         * Communication
         */

        packages.add(
                new SoftwarePackage(
                        "Discord.Discord",
                        "Discord",
                        "Communication",
                        "Voice, video and messaging application."
                )
        );

        packages.add(
                new SoftwarePackage(
                        "Zoom.Zoom",
                        "Zoom",
                        "Communication",
                        "Video conferencing application."
                )
        );


        /*
         * Media
         */

        packages.add(
                new SoftwarePackage(
                        "VideoLAN.VLC",
                        "VLC Media Player",
                        "Media",
                        "Open-source multimedia player."
                )
        );

        return packages;
    }
	    
	    /*
	     * =========================================================
	     * EXECUTE WINGET COMMAND
	     * =========================================================
	     */

	    private boolean executeWingetInstallation(
            SoftwarePackage softwarePackage,
            SoftwareInstallation installation) {

        List<String> command = new ArrayList<>();
        command.add("winget.exe");
        command.add("install");
        command.add("--id");
        command.add(softwarePackage.getId());
        command.add("--exact");
        command.add("--source");
        command.add("winget");
        command.add("--silent");
        command.add("--accept-source-agreements");
        command.add("--accept-package-agreements");
        command.add("--disable-interactivity");

        System.out.println();
        System.out.println("========== WINGET COMMAND ==========");
        System.out.println(String.join(" ", command));
        System.out.println("====================================");

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[WinGet] " + line);
                    updateInstallationState(installation, line);
                }
            }

            int exitCode = process.waitFor();
            System.out.println("[WinGet] Exit code: " + exitCode);
            System.out.println("====================================");
            return exitCode == 0;

        } catch (IOException e) {
            installation.setMessage("Failed to start Windows Package Manager.");
            e.printStackTrace();
            return false;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            installation.setMessage("Installation interrupted.");
            return false;
        }
    }

    private void updateInstallationState(
            SoftwareInstallation installation,
            String outputLine) {

        if (outputLine == null || outputLine.isBlank()) return;

        String normalized = outputLine.toLowerCase();

        if (normalized.contains("download")
                || normalized.contains("mb /")
                || normalized.contains("kb /")
                || normalized.contains("gb /")) {
            installation.setStatus(Status.DOWNLOADING);
            installation.setMessage("Downloading application...");
            return;
        }

        if (normalized.contains("installing")
                || normalized.contains("starting package install")
                || normalized.contains("successfully installed")) {
            installation.setStatus(Status.INSTALLING);
            installation.setMessage("Installing application...");
        }
    }
    
    /*
     * =========================================================
     * REFRESH WINGET SOURCES
     * =========================================================
     */

    public boolean refreshWingetSources() {

        if (!isWingetAvailable()) {
            return false;
        }

        try {

            executeWinget(
                    "source",
                    "update",
                    "--disable-interactivity"
            );

            return true;

        } catch (RuntimeException e) {

            System.err.println(
                    "Failed to refresh WinGet sources: "
                    + e.getMessage()
            );

            return false;
        }
    }
    
    /*
     * =========================================================
     * OPEN WINDOWS INSTALLED APPS
     * =========================================================
     */

    public boolean openInstalledApps() {

        try {

            new ProcessBuilder(
                    "cmd.exe",
                    "/c",
                    "start",
                    "",
                    "ms-settings:appsfeatures"
            ).start();

            return true;

        } catch (IOException e) {

            System.err.println(
                    "Failed to open Windows Installed Apps: "
                    + e.getMessage()
            );

            return false;
        }
    }
    
    /*
     * =========================================================
     * WINGET DIAGNOSTICS
     * =========================================================
     */

    public Map<String, Object> runWingetDiagnostics() {

        Map<String, Object> diagnostics =
                new LinkedHashMap<>();

        boolean available =
                isWingetAvailable();

        diagnostics.put(
                "available",
                available
        );


        if (!available) {

            diagnostics.put(
                    "healthy",
                    false
            );

            diagnostics.put(
                    "version",
                    "Unavailable"
            );

            diagnostics.put(
                    "sourceCount",
                    0
            );

            diagnostics.put(
                    "sources",
                    List.of()
            );

            diagnostics.put(
                    "message",
                    "Windows Package Manager is not available."
            );

            return diagnostics;
        }


        String version =
                getWingetVersion();

        List<String> sources =
                getWingetSources();


        diagnostics.put(
                "version",
                version
        );

        diagnostics.put(
                "sourceCount",
                sources.size()
        );

        diagnostics.put(
                "sources",
                sources
        );


        if (sources.isEmpty()) {

            diagnostics.put(
                    "healthy",
                    false
            );

            diagnostics.put(
                    "message",
                    "WinGet is available, but no package sources were detected."
            );

            return diagnostics;
        }


        diagnostics.put(
                "healthy",
                true
        );

        diagnostics.put(
                "message",
                "WinGet is available and package sources are configured."
        );

        return diagnostics;
    }
}
