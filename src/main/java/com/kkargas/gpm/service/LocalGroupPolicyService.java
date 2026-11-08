package com.kkargas.gpm.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.stereotype.Service;

import com.kkargas.gpm.enums.PolicyState;
import com.kkargas.gpm.model.Policy;

@Service
public class LocalGroupPolicyService {

    /*
     * Real Local Group Policy Registry.pol file
     * for Computer Configuration.
     */
    private static final Path MACHINE_REGISTRY_POL =
            Path.of(
                    System.getenv("WINDIR"),
                    "System32",
                    "GroupPolicy",
                    "Machine",
                    "Registry.pol"
            );


    private final Path lgpoExecutable;


    public LocalGroupPolicyService() {

        this.lgpoExecutable =
                extractLgpoExecutable();
    }


    /*
     * ============================================================
     * LGPO EXECUTABLE
     * ============================================================
     */


    /*
     * Extract LGPO.exe from:
     *
     * src/main/resources/tools/LGPO.exe
     *
     * into a temporary directory.
     */
    private Path extractLgpoExecutable() {

        try (InputStream inputStream =
                getClass().getResourceAsStream(
                        "/tools/LGPO.exe"
                )) {

            if (inputStream == null) {

                throw new IllegalStateException(
                        "LGPO.exe was not found "
                        + "in /resources/tools/"
                );
            }


            Path tempDirectory =
                    Files.createTempDirectory(
                            "gpm-lgpo-"
                    );


            Path executable =
                    tempDirectory.resolve(
                            "LGPO.exe"
                    );


            Files.copy(
                    inputStream,
                    executable
            );


            executable.toFile()
                      .deleteOnExit();

            tempDirectory.toFile()
                         .deleteOnExit();


            return executable;


        } catch (IOException e) {

            throw new IllegalStateException(
                    "Could not extract LGPO.exe",
                    e
            );
        }
    }


    /*
     * ============================================================
     * READ POLICY STATE
     * ============================================================
     */


    /*
     * Reads the REAL Machine Registry.pol using:
     *
     * LGPO.exe /parse /m Registry.pol
     */
    /**
     * Parse the machine policy file once for an entire page load.
     * Do not cache policy states: the Windows policy store remains authoritative.
     */
    public java.util.Map<String, PolicyState> getPolicyStates(
            java.util.List<Policy> policies) {

        java.util.Map<String, PolicyState> states = new java.util.HashMap<>();
        String parsed = null;

        if (Files.exists(MACHINE_REGISTRY_POL)) {
            ProcessBuilder builder = new ProcessBuilder(
                    lgpoExecutable.toString(), "/parse", "/m",
                    MACHINE_REGISTRY_POL.toString());
            builder.redirectErrorStream(true);
            try {
                Process process = builder.start();
                String output;
                try (InputStream stream = process.getInputStream()) {
                    output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
                if (process.waitFor() == 0) {
                    parsed = output;
                } else {
                    System.err.println("[LGPO] Unable to parse Registry.pol");
                }
            } catch (IOException e) {
                System.err.println("[LGPO] Unable to read Registry.pol: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        for (Policy policy : policies) {
            PolicyState state = PolicyState.NOT_CONFIGURED;
            if (parsed != null && "COMPUTER".equalsIgnoreCase(policy.getScope())) {
                state = determinePolicyState(parsed, policy);
            }
            states.put(policy.getId(), state);
        }
        return states;
    }


    /** Read the actual configured DWORD for a policy, including alternative values. */
    public String getConfiguredValue(Policy policy) {
        if (!"COMPUTER".equalsIgnoreCase(policy.getScope()) || !Files.exists(MACHINE_REGISTRY_POL)) return null;
        try {
            String output = parseMachineRegistryPol();
            String[] lines = output.split("\\R");
            for (int i = 0; i + 2 < lines.length; i++) {
                if (lines[i].trim().equalsIgnoreCase(policy.getRegistryPath())
                        && lines[i + 1].trim().equalsIgnoreCase(policy.getRegistryValue())) {
                    String value = lines[i + 2].trim();
                    String prefix = toLgpoType(policy.getRegistryType()) + ":";
                    if (value.regionMatches(true, 0, prefix, 0, prefix.length()))
                        return value.substring(prefix.length()).trim();
                }
            }
        } catch (Exception e) {
            System.err.println("[LGPO] Could not read configured value: " + e.getMessage());
        }
        return null;
    }

    public PolicyState getPolicyState(
            Policy policy) {

        /*
         * User policies will be implemented later.
         */
        if (!"COMPUTER".equalsIgnoreCase(
                policy.getScope())) {

            return PolicyState.NOT_CONFIGURED;
        }


        /*
         * No Registry.pol means that no registry-based
         * Computer policies currently exist.
         */
        if (!Files.exists(
                MACHINE_REGISTRY_POL)) {

            return PolicyState.NOT_CONFIGURED;
        }


        ProcessBuilder processBuilder =
                new ProcessBuilder(
                        lgpoExecutable.toString(),
                        "/parse",
                        "/m",
                        MACHINE_REGISTRY_POL.toString()
                );


        processBuilder.redirectErrorStream(
                true
        );


        try {

            Process process =
                    processBuilder.start();


            String output;

            try (InputStream inputStream =
                    process.getInputStream()) {

                output =
                        new String(
                                inputStream.readAllBytes(),
                                StandardCharsets.UTF_8
                        );
            }


            int exitCode =
                    process.waitFor();


            if (exitCode != 0) {

                System.err.println(
                        "[LGPO] Could not parse Registry.pol."
                );

                return PolicyState.NOT_CONFIGURED;
            }


            return determinePolicyState(
                    output,
                    policy
            );


        } catch (IOException e) {

            e.printStackTrace();

            return PolicyState.NOT_CONFIGURED;


        } catch (InterruptedException e) {

            Thread.currentThread()
                  .interrupt();

            return PolicyState.NOT_CONFIGURED;
        }
    }


    /*
     * Search parsed LGPO output for:
     *
     * Computer
     * RegistryPath
     * RegistryValue
     * TYPE:data
     */
    private PolicyState determinePolicyState(
            String output,
            Policy policy) {

        String[] lines =
                output.split("\\R");


        for (int i = 0;
             i < lines.length;
             i++) {


            String line =
                    lines[i].trim();


            /*
             * Find exact registry path.
             */
            if (!line.equalsIgnoreCase(
                    policy.getRegistryPath())) {

                continue;
            }


            /*
             * We need:
             *
             * current line     = RegistryPath
             * current + 1      = RegistryValue
             * current + 2      = TYPE:data
             */
            if (i + 2 >= lines.length) {

                continue;
            }


            String registryValue =
                    lines[i + 1]
                            .trim();


            String registryData =
                    lines[i + 2]
                            .trim();


            /*
             * Same registry key may contain many
             * independent policies.
             */
            if (!registryValue.equalsIgnoreCase(
                    policy.getRegistryValue())) {

                continue;
            }


            String expectedValue =
                    toLgpoType(
                            policy.getRegistryType()
                    )
                    + ":"
                    + policy.getEnabledValue();


            if (registryData.equalsIgnoreCase(
                    expectedValue)) {

                return PolicyState.ENABLED;
            }


            /*
             * Entry exists but does not contain
             * the enabled value.
             *
             * For now treat it as DISABLED.
             */
            return PolicyState.DISABLED;
        }


        /*
         * Exact registry path + value not found.
         */
        return PolicyState.NOT_CONFIGURED;
    }


    /*
     * ============================================================
     * APPLY POLICY
     * ============================================================
     */


    public boolean applyPolicy(
            Policy policy,
            PolicyState state) {

        try {

            boolean success;


            if (state ==
                    PolicyState.ENABLED) {

                success =
                        enablePolicy(
                                policy
                        );


            } else if (state ==
                    PolicyState.NOT_CONFIGURED) {

                success =
                        removePolicy(
                                policy
                        );


            } else {

                throw new UnsupportedOperationException(
                        "DISABLED state "
                        + "is not implemented yet."
                );
            }


            if (!success) {

                return false;
            }


            /*
             * Apply the new Local GPO state.
             */
            refreshGroupPolicy();


            return true;


        } catch (IOException e) {

            e.printStackTrace();

            return false;


        } catch (InterruptedException e) {

            Thread.currentThread()
                  .interrupt();

            return false;
        }
    }


    /*
     * ============================================================
     * ENABLE POLICY
     * ============================================================
     */


    /*
     * Enabling already works correctly using:
     *
     * LGPO.exe /t
     *
     * because here we WANT to merge/add one setting.
     */
    private boolean enablePolicy(
            Policy policy)
            throws IOException,
                   InterruptedException {

        Path tempFile = null;


        try {

            String lgpoText =
                    createEnabledLgpoText(
                            policy
                    );


            tempFile =
                    Files.createTempFile(
                            "gpm-enable-",
                            ".txt"
                    );


            Files.writeString(
                    tempFile,
                    lgpoText,
                    StandardCharsets.UTF_8
            );


            ProcessBuilder processBuilder =
                    new ProcessBuilder(
                            lgpoExecutable.toString(),
                            "/t",
                            tempFile.toString(),
                            "/v"
                    );


            processBuilder.redirectErrorStream(
                    true
            );


            Process process =
                    processBuilder.start();


            printProcessOutput(
                    process,
                    "[LGPO ENABLE] "
            );


            int exitCode =
                    process.waitFor();


            return exitCode == 0;


        } finally {

            if (tempFile != null) {

                Files.deleteIfExists(
                        tempFile
                );
            }
        }
    }


    /*
     * Create LGPO text for ENABLED state.
     */
    private String createEnabledLgpoText(
            Policy policy) {

        String scope =
                "USER".equalsIgnoreCase(
                        policy.getScope()
                )
                ? "User"
                : "Computer";


        return scope
                + "\r\n"
                + policy.getRegistryPath()
                + "\r\n"
                + policy.getRegistryValue()
                + "\r\n"
                + toLgpoType(
                        policy.getRegistryType()
                )
                + ":"
                + policy.getEnabledValue()
                + "\r\n";
    }


    /*
     * ============================================================
     * REMOVE POLICY / NOT CONFIGURED
     * ============================================================
     */


    /*
     * NOT_CONFIGURED cannot be implemented by simply
     * importing another Registry.pol with /m.
     *
     * /m merges settings.
     *
     * Therefore:
     *
     * 1. Parse the COMPLETE existing Registry.pol.
     * 2. Remove ONLY the requested policy block.
     * 3. Build a COMPLETE replacement Registry.pol.
     * 4. Backup the original Registry.pol.
     * 5. Replace the actual Registry.pol.
     * 6. gpupdate is executed by applyPolicy().
     */
    private boolean removePolicy(
            Policy policy)
            throws IOException,
                   InterruptedException {

        /*
         * Computer policies only for now.
         */
        if (!"COMPUTER".equalsIgnoreCase(
                policy.getScope())) {

            throw new UnsupportedOperationException(
                    "User policies "
                    + "are not implemented yet."
            );
        }


        /*
         * No policy file means there is
         * nothing to remove.
         */
        if (!Files.exists(
                MACHINE_REGISTRY_POL)) {

            return true;
        }


        /*
         * --------------------------------------------------------
         * STEP 1
         * Parse the COMPLETE current Registry.pol.
         * --------------------------------------------------------
         */
        String currentLgpoText =
                parseMachineRegistryPol();


        /*
         * --------------------------------------------------------
         * STEP 2
         * Remove ONLY the exact:
         *
         * scope + path + value
         *
         * belonging to this policy.
         * --------------------------------------------------------
         */
        String modifiedLgpoText =
                removePolicyBlock(
                        currentLgpoText,
                        policy
                );


        /*
         * If nothing changed, the requested policy
         * was already Not Configured.
         */
        if (modifiedLgpoText.equals(
                currentLgpoText)) {

            System.out.println(
                    "[LGPO REMOVE] Policy was already "
                    + "Not Configured: "
                    + policy.getName()
            );

            return true;
        }


        Path tempText = null;
        Path tempPol = null;


        try {

            /*
             * Temporary LGPO text containing all
             * policies except the one being removed.
             */
            tempText =
                    Files.createTempFile(
                            "gpm-rebuild-",
                            ".txt"
                    );


            /*
             * Temporary rebuilt Registry.pol.
             */
            tempPol =
                    Files.createTempFile(
                            "gpm-registry-",
                            ".pol"
                    );


            /*
             * LGPO /w creates the output file itself.
             */
            Files.deleteIfExists(
                    tempPol
            );


            Files.writeString(
                    tempText,
                    modifiedLgpoText,
                    StandardCharsets.UTF_8
            );


            /*
             * ----------------------------------------------------
             * STEP 3
             * Build COMPLETE new Registry.pol:
             *
             * LGPO.exe /r modified.txt /w new.pol
             * ----------------------------------------------------
             */
            ProcessBuilder buildProcess =
                    new ProcessBuilder(
                            lgpoExecutable.toString(),
                            "/r",
                            tempText.toString(),
                            "/w",
                            tempPol.toString(),
                            "/v"
                    );


            buildProcess.redirectErrorStream(
                    true
            );


            Process build =
                    buildProcess.start();


            printProcessOutput(
                    build,
                    "[LGPO REBUILD] "
            );


            int buildExitCode =
                    build.waitFor();


            if (buildExitCode != 0) {

                System.err.println(
                        "[LGPO REBUILD] "
                        + "Registry.pol rebuild failed."
                );

                return false;
            }


            /*
             * Make sure LGPO actually created
             * the new Registry.pol.
             */
            if (!Files.exists(tempPol)) {

                System.err.println(
                        "[LGPO REBUILD] "
                        + "Output Registry.pol "
                        + "was not created."
                );

                return false;
            }


            /*
             * ----------------------------------------------------
             * STEP 4
             * Backup current REAL Registry.pol.
             * ----------------------------------------------------
             */
            Path backupPol =
                    MACHINE_REGISTRY_POL
                            .resolveSibling(
                                    "Registry.pol.gpm-backup"
                            );


            Files.copy(
                    MACHINE_REGISTRY_POL,
                    backupPol,
                    StandardCopyOption.REPLACE_EXISTING
            );


            System.out.println(
                    "[LGPO BACKUP] Backup created: "
                    + backupPol
            );


            /*
             * ----------------------------------------------------
             * STEP 5
             * Replace the REAL Registry.pol.
             *
             * IMPORTANT:
             *
             * Do NOT use:
             *
             * LGPO.exe /m tempPol
             *
             * here.
             *
             * /m merges settings. A missing policy would
             * therefore remain in the existing Local GPO.
             *
             * We have already rebuilt the COMPLETE file,
             * so we replace Registry.pol directly.
             * ----------------------------------------------------
             */
            try {

                Files.copy(
                        tempPol,
                        MACHINE_REGISTRY_POL,
                        StandardCopyOption.REPLACE_EXISTING
                );


                System.out.println(
                        "[LGPO REPLACE] "
                        + "Machine Registry.pol "
                        + "replaced successfully."
                );


            } catch (IOException replaceException) {

                System.err.println(
                        "[LGPO REPLACE] "
                        + "Replacement failed."
                );


                /*
                 * Attempt rollback.
                 */
                if (Files.exists(
                        backupPol)) {

                    try {

                        Files.copy(
                                backupPol,
                                MACHINE_REGISTRY_POL,
                                StandardCopyOption.REPLACE_EXISTING
                        );


                        System.err.println(
                                "[LGPO ROLLBACK] "
                                + "Original Registry.pol "
                                + "restored."
                        );


                    } catch (IOException rollbackException) {

                        System.err.println(
                                "[LGPO ROLLBACK] "
                                + "CRITICAL: Could not "
                                + "restore Registry.pol."
                        );


                        rollbackException
                                .printStackTrace();
                    }
                }


                throw replaceException;
            }


            /*
             * Verify immediately that the removed
             * entry no longer exists in Registry.pol.
             */
            PolicyState stateAfterReplace =
                    getPolicyState(
                            policy
                    );


            System.out.println(
                    "[LGPO VERIFY] "
                    + policy.getName()
                    + " -> "
                    + stateAfterReplace
            );


            /*
             * Replacement is considered successful
             * only if the exact policy disappeared.
             */
            if (stateAfterReplace !=
                    PolicyState.NOT_CONFIGURED) {

                System.err.println(
                        "[LGPO VERIFY] "
                        + "Policy still exists after "
                        + "Registry.pol replacement."
                );


                /*
                 * Restore backup because the operation
                 * did not produce the expected state.
                 */
                Files.copy(
                        backupPol,
                        MACHINE_REGISTRY_POL,
                        StandardCopyOption.REPLACE_EXISTING
                );


                System.err.println(
                        "[LGPO ROLLBACK] "
                        + "Original Registry.pol restored."
                );


                return false;
            }


            return true;


        } finally {

            /*
             * Delete only our temporary files.
             *
             * Registry.pol.gpm-backup is intentionally
             * kept for safety.
             */
            if (tempText != null) {

                Files.deleteIfExists(
                        tempText
                );
            }


            if (tempPol != null) {

                Files.deleteIfExists(
                        tempPol
                );
            }
        }
    }


    /*
     * ============================================================
     * PARSE Registry.pol
     * ============================================================
     */


    private String parseMachineRegistryPol()
            throws IOException,
                   InterruptedException {

        ProcessBuilder processBuilder =
                new ProcessBuilder(
                        lgpoExecutable.toString(),
                        "/parse",
                        "/q",
                        "/m",
                        MACHINE_REGISTRY_POL.toString()
                );


        processBuilder.redirectErrorStream(
                true
        );


        Process process =
                processBuilder.start();


        String output;


        try (InputStream inputStream =
                process.getInputStream()) {

            output =
                    new String(
                            inputStream.readAllBytes(),
                            StandardCharsets.UTF_8
                    );
        }


        int exitCode =
                process.waitFor();


        if (exitCode != 0) {

            throw new IOException(
                    "LGPO could not parse Registry.pol"
            );
        }


        return output;
    }


    /*
     * ============================================================
     * REMOVE EXACT POLICY BLOCK
     * ============================================================
     */


    /*
     * Parsed LGPO blocks look like:
     *
     * Computer
     * RegistryPath
     * RegistryValue
     * TYPE:data
     *
     * We remove ONLY the block matching the exact
     * registry path + registry value.
     */
    private String removePolicyBlock(
            String lgpoText,
            Policy policy) {

        String[] lines =
                lgpoText.split("\\R");


        StringBuilder result =
                new StringBuilder();


        boolean removed = false;


        for (int i = 0;
             i < lines.length;) {


            /*
             * Is this the exact policy block?
             */
            if (i + 3 < lines.length
                    && lines[i]
                            .trim()
                            .equalsIgnoreCase(
                                    "Computer"
                            )
                    && lines[i + 1]
                            .trim()
                            .equalsIgnoreCase(
                                    policy.getRegistryPath()
                            )
                    && lines[i + 2]
                            .trim()
                            .equalsIgnoreCase(
                                    policy.getRegistryValue()
                            )) {


                System.out.println(
                        "[LGPO REMOVE] Removing: "
                        + policy.getRegistryPath()
                        + " -> "
                        + policy.getRegistryValue()
                );


                /*
                 * Skip:
                 *
                 * Computer
                 * Path
                 * Value
                 * TYPE:data
                 */
                i += 4;


                /*
                 * Also skip blank lines directly
                 * following this block.
                 */
                while (i < lines.length
                        && lines[i]
                                .trim()
                                .isEmpty()) {

                    i++;
                }


                removed = true;

                continue;
            }


            result.append(
                    lines[i]
            );

            result.append(
                    "\r\n"
            );


            i++;
        }


        if (!removed) {

            System.out.println(
                    "[LGPO REMOVE] Exact policy "
                    + "entry was not found: "
                    + policy.getRegistryPath()
                    + " -> "
                    + policy.getRegistryValue()
            );
        }


        return result.toString();
    }


    /*
     * ============================================================
     * PROCESS OUTPUT
     * ============================================================
     */


    private void printProcessOutput(
            Process process,
            String prefix)
            throws IOException {

        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                process.getInputStream()
                        )
                )) {

            String line;


            while ((line =
                    reader.readLine()) != null) {

                System.out.println(
                        prefix + line
                );
            }
        }
    }


    /*
     * ============================================================
     * REGISTRY TYPE CONVERSION
     * ============================================================
     */


    private String toLgpoType(
            String registryType) {

        return switch (
                registryType.toUpperCase()) {

            case "REG_DWORD",
                 "DWORD" -> "DWORD";

            case "REG_SZ",
                 "SZ" -> "SZ";

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported registry type: "
                            + registryType
                    );
        };
    }


    /*
     * ============================================================
     * REFRESH GROUP POLICY
     * ============================================================
     */


    private void refreshGroupPolicy()
            throws IOException,
                   InterruptedException {

        Process process =
                new ProcessBuilder(
                        "gpupdate",
                        "/target:computer",
                        "/force"
                )
                .redirectErrorStream(true)
                .start();


        process.getInputStream()
               .transferTo(
                       java.io.OutputStream
                               .nullOutputStream()
               );


        int exitCode =
                process.waitFor();


        if (exitCode != 0) {

            System.err.println(
                    "gpupdate exited with code: "
                    + exitCode
            );

        } else {

            System.out.println(
                    "[GPUPDATE] "
                    + "Computer Policy updated successfully."
            );
        }
    }
}