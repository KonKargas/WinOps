package com.kkargas.gpm.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.kkargas.gpm.model.WindowsUser;


@Service
public class WindowsUserService {


    /*
     * =========================================================
     * GET ALL LOCAL WINDOWS USERS
     * =========================================================
     */

    public List<WindowsUser> getLocalUsers() {

        List<WindowsUser> users =
                new ArrayList<>();


        Set<String> administrators =
                getLocalAdministrators();


        String command =
                "Get-LocalUser | ForEach-Object { "
                + "[string]::Join('|', @("
                + "$_.Name, "
                + "$_.FullName, "
                + "$_.Description, "
                + "$_.Enabled, "
                + "$_.PasswordRequired, "
                + "$_.PasswordExpires, "
                + "$_.UserMayChangePassword"
                + ")) "
                + "}";


        List<String> output =
                executePowerShell(command);


        for (String line : output) {

            if (line == null
                    || line.isBlank()) {

                continue;
            }


            /*
             * Keep empty values when splitting.
             *
             * Example:
             *
             * Konstantinos||Local Admin|True|True|False|True
             */

            String[] values =
                    line.split("\\|", -1);


            if (values.length < 7) {

                System.err.println(
                        "[WINDOWS USER] Invalid output: "
                        + line
                );

                continue;
            }


            String username =
                    values[0].trim();

            String fullName =
                    values[1].trim();

            String description =
                    values[2].trim();

            boolean enabled =
                    Boolean.parseBoolean(
                            values[3].trim()
                    );

            boolean passwordRequired =
                    Boolean.parseBoolean(
                            values[4].trim()
                    );

            boolean passwordExpires =
                    !values[5].trim().isEmpty();

            boolean userMayChangePassword =
                    Boolean.parseBoolean(
                            values[6].trim()
                    );


            boolean administrator =
                    administrators.contains(
                            username.toLowerCase()
                    );


            WindowsUser user =
                    new WindowsUser(
                            username,
                            fullName,
                            description,
                            enabled,
                            administrator,
                            passwordRequired,
                            passwordExpires,
                            userMayChangePassword
                    );


            users.add(user);
        }


        return users;
    }


    /*
     * =========================================================
     * GET LOCAL ADMINISTRATORS
     * =========================================================
     */

    private Set<String> getLocalAdministrators() {

        Set<String> administrators =
                new HashSet<>();


        /*
         * We identify the built-in Administrators group
         * using its SID instead of its localized name.
         *
         * S-1-5-32-544 = BUILTIN\Administrators
         */

        String command =
                "$group = "
                + "Get-LocalGroup -SID 'S-1-5-32-544'; "
                + "Get-LocalGroupMember "
                + "-Group $group | "
                + "ForEach-Object { $_.Name }";


        List<String> output =
                executePowerShell(command);


        for (String line : output) {

            if (line == null
                    || line.isBlank()) {

                continue;
            }


            String member =
                    line.trim();


            /*
             * PowerShell usually returns:
             *
             * COMPUTERNAME\Username
             *
             * We only need:
             *
             * Username
             */

            int separator =
                    member.lastIndexOf('\\');


            if (separator >= 0
                    && separator < member.length() - 1) {

                member =
                        member.substring(
                                separator + 1
                        );
            }


            administrators.add(
                    member.toLowerCase()
            );
        }


        return administrators;
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

                String errorOutput =
                        String.join(
                                System.lineSeparator(),
                                output
                        );

                System.err.println(
                        "[POWERSHELL ERROR]"
                );

                System.err.println(
                        errorOutput
                );


                throw new RuntimeException(
                        "PowerShell command failed "
                        + "with exit code "
                        + exitCode
                        + System.lineSeparator()
                        + errorOutput
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



    /* =========================================================
     * DELETE LOCAL WINDOWS USER
     * ========================================================= */

    public void deleteLocalUser(String username) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Invalid username.");
        }

        // PowerShell single-quoted literals escape embedded apostrophes.
        String safeUsername = username.replace("'", "''");

        // All checks and removal execute in one PowerShell process.
        String command = "& { "
                + "$ErrorActionPreference = 'Stop'; "
                + "$name = '" + safeUsername + "'; "
                + "$target = Get-LocalUser -Name $name -ErrorAction Stop; "
                + "$current = [System.Security.Principal.WindowsIdentity]::GetCurrent(); "
                + "if ($target.SID.Value -eq $current.User.Value) { throw 'Cannot delete the current account.' }; "
                + "if ($target.SID.Value -match '-(500|501|503|504)$') { throw 'Built-in Windows accounts cannot be deleted.' }; "
                + "$admins = @(Get-LocalGroupMember -SID 'S-1-5-32-544' -ErrorAction Stop); "
                + "$isAdmin = @($admins | Where-Object { $_.SID -and $_.SID.Value -eq $target.SID.Value }).Count -gt 0; "
                + "if ($isAdmin) { "
                + "  $adminSids = @($admins | Where-Object { $_.SID } | ForEach-Object { $_.SID.Value }); "
                + "  $enabledAdmins = @(Get-LocalUser | Where-Object { $_.Enabled -and ($adminSids -contains $_.SID.Value) }); "
                + "  if ($target.Enabled -and $enabledAdmins.Count -le 1) { throw 'Cannot delete the last enabled local administrator.' } "
                + "}; "
                + "Remove-LocalUser -SID $target.SID -ErrorAction Stop; "
                + "}";

        executePowerShell(command);
    }

    public WindowsUser getLocalUser(String username) {

        if (username == null || username.isBlank()) {
            return null;
        }

        return getLocalUsers()
                .stream()
                .filter(user ->
                        user.getUsername()
                                .equalsIgnoreCase(username)
                )
                .findFirst()
                .orElse(null);
    }
    
    /*
     * =========================================================
     * UPDATE LOCAL USER INFORMATION
     * =========================================================
     */

    public void updateUserInformation(
            String username,
            String fullName,
            String description) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "Username cannot be empty."
            );
        }

        String safeUsername =
                escapePowerShell(username);

        String safeFullName =
                escapePowerShell(
                        fullName != null
                                ? fullName
                                : ""
                );

        String safeDescription =
                escapePowerShell(
                        description != null
                                ? description
                                : ""
                );


        String command =
                "Set-LocalUser "
                + "-Name '" + safeUsername + "' "
                + "-FullName '" + safeFullName + "' "
                + "-Description '" + safeDescription + "'";


        executePowerShell(command);
    }


    /*
     * =========================================================
     * POWERSHELL STRING ESCAPING
     * =========================================================
     */

    private String escapePowerShell(String value) {

        if (value == null) {
            return "";
        }

        /*
         * Inside a PowerShell single-quoted string,
         * a single quote is escaped by doubling it.
         */

        return value.replace("'", "''");
    }
    
    /*
     * =========================================================
     * CHANGE LOCAL USER PASSWORD
     * =========================================================
     */

    public void changePassword(
            String username,
            String newPassword) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "Username cannot be empty."
            );
        }

        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException(
                    "Password cannot be empty."
            );
        }


        String safeUsername =
                escapePowerShell(username);

        String safePassword =
                escapePowerShell(newPassword);


        String command =
                "$password = ConvertTo-SecureString "
                + "'" + safePassword + "' "
                + "-AsPlainText -Force; "
                + "Set-LocalUser "
                + "-Name '" + safeUsername + "' "
                + "-Password $password";


        executePowerShell(command);
    }
    
    /*
     * =========================================================
     * UPDATE LOCAL USER SETTINGS
     * =========================================================
     */

    public void updateUserSettings(
            String username,
            boolean enabled,
            boolean administrator,
            boolean userMayChangePassword,
            boolean passwordRequired) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "Username cannot be empty."
            );
        }


        String safeUsername =
                escapePowerShell(username);


        /*
         * =====================================================
         * ENABLE / DISABLE ACCOUNT
         * =====================================================
         */

        if (enabled) {

            executePowerShell(
                    "Enable-LocalUser "
                    + "-Name '" + safeUsername + "'"
            );

        } else {

            executePowerShell(
                    "Disable-LocalUser "
                    + "-Name '" + safeUsername + "'"
            );
        }


        /*
         * =====================================================
         * ADMINISTRATOR PRIVILEGES
         * =====================================================
         */

        boolean currentlyAdministrator =
                getLocalAdministrators()
                        .contains(
                                username.toLowerCase()
                        );


        if (administrator
                && !currentlyAdministrator) {

            String command =
                    "$group = "
                    + "Get-LocalGroup -SID 'S-1-5-32-544'; "
                    + "Add-LocalGroupMember "
                    + "-Group $group "
                    + "-Member '" + safeUsername + "'";

            executePowerShell(command);

        } else if (!administrator
                && currentlyAdministrator) {

            String command =
                    "$group = "
                    + "Get-LocalGroup -SID 'S-1-5-32-544'; "
                    + "Remove-LocalGroupMember "
                    + "-Group $group "
                    + "-Member '" + safeUsername + "'";

            executePowerShell(command);
        }


        /*
         * =====================================================
         * USER MAY CHANGE PASSWORD
         * =====================================================
         */

        String mayChangePasswordValue =
                userMayChangePassword
                        ? "$true"
                        : "$false";


        executePowerShell(
                "Set-LocalUser "
                + "-Name '" + safeUsername + "' "
                + "-UserMayChangePassword "
                + mayChangePasswordValue
        );


        /*
         * =====================================================
         * PASSWORD REQUIRED
         * =====================================================
         */
        executePowerShell(
                "net user "
                + "'" + safeUsername + "' "
                + (passwordRequired
                        ? "/passwordreq:yes"
                        : "/passwordreq:no")
        );
    }
    
    /*
     * =========================================================
     * CREATE LOCAL WINDOWS USER
     * =========================================================
     */

    public void createUser(
            String username,
            String fullName,
            String description,
            String password,
            boolean administrator,
            boolean userMayChangePassword,
            boolean passwordRequired) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "Username cannot be empty."
            );
        }

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException(
                    "Password cannot be empty."
            );
        }


        /*
         * Make sure the account does not already exist.
         */

        if (getLocalUser(username) != null) {
            throw new IllegalArgumentException(
                    "A local user with this username already exists."
            );
        }


        String safeUsername =
                escapePowerShell(username);

        String safeFullName =
                escapePowerShell(
                        fullName != null
                                ? fullName
                                : ""
                );

        String safeDescription =
                escapePowerShell(
                        description != null
                                ? description
                                : ""
                );

        String safePassword =
                escapePowerShell(password);


        /*
         * =====================================================
         * CREATE ACCOUNT
         * =====================================================
         */

        String command =
                "$password = ConvertTo-SecureString "
                + "'" + safePassword + "' "
                + "-AsPlainText -Force; "
                + "New-LocalUser "
                + "-Name '" + safeUsername + "' "
                + "-Password $password "
                + "-FullName '" + safeFullName + "' "
                + "-Description '" + safeDescription + "'";


        executePowerShell(command);


        /*
         * =====================================================
         * USER MAY CHANGE PASSWORD
         * =====================================================
         */

        executePowerShell(
                "Set-LocalUser "
                + "-Name '" + safeUsername + "' "
                + "-UserMayChangePassword "
                + (userMayChangePassword ? "$true" : "$false")
        );


        /*
         * =====================================================
         * PASSWORD REQUIRED
         * =====================================================
         */

        executePowerShell(
                "net user "
                + "'" + safeUsername + "' "
                + (passwordRequired
                        ? "/passwordreq:yes"
                        : "/passwordreq:no")
        );


        /*
         * =====================================================
         * ADMINISTRATOR
         * =====================================================
         */

        if (administrator) {

            String administratorCommand =
                    "$group = "
                    + "Get-LocalGroup -SID 'S-1-5-32-544'; "
                    + "Add-LocalGroupMember "
                    + "-Group $group "
                    + "-Member '" + safeUsername + "'";


            executePowerShell(
                    administratorCommand
            );
        }
    }
}