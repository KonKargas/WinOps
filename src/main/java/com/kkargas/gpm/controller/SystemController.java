package com.kkargas.gpm.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.kkargas.gpm.model.SystemInformation;
import com.kkargas.gpm.service.WindowsSystemService;


@Controller
public class SystemController {

    private final WindowsSystemService
            windowsSystemService;


    public SystemController(
            WindowsSystemService windowsSystemService) {

        this.windowsSystemService =
                windowsSystemService;
    }


    /*
     * =========================================================
     * SYSTEM PAGE
     * =========================================================
     */

    @GetMapping("/system")
    public String viewSystem(
            Model model) {

        return "System";
    }

    @GetMapping("/system/data")
    public String loadSystemData(Model model) {
        SystemInformation systemInformation = windowsSystemService.getSystemInformation();
        model.addAttribute("system", systemInformation);
        model.addAttribute("timeZones", windowsSystemService.getAvailableTimeZones());
        return "fragments/system-data :: content";
    }

    /*
     * =========================================================
     * RENAME COMPUTER
     * =========================================================
     */

    @PostMapping("/system/computer-name")
    public String renameComputer(
            @RequestParam String computerName) {

        boolean renamed =
                windowsSystemService
                        .renameComputer(
                                computerName
                        );


        if (!renamed) {

            return "redirect:/system?sameComputerName=true";
        }


        return "redirect:/system?renameSuccess=true";
    }


    /*
     * =========================================================
     * CHANGE TIME ZONE
     * =========================================================
     */

    @PostMapping("/system/time-zone")
    public String changeTimeZone(
            @RequestParam String timeZoneId) {

        windowsSystemService
                .changeTimeZone(
                        timeZoneId
                );


        return "redirect:/system?timeZoneSuccess=true";
    }


    /*
     * =========================================================
     * OPEN MSCONFIG
     * =========================================================
     */

    @PostMapping("/system/msconfig")
    public String openMsConfig() {

        windowsSystemService
                .openMsConfig();

        return "redirect:/system";
    }


    /*
     * =========================================================
     * OPEN ENVIRONMENT VARIABLES
     * =========================================================
     */

    @PostMapping("/system/environment-variables")
    public String openEnvironmentVariables() {

        windowsSystemService
                .openEnvironmentVariables();

        return "redirect:/system";
    }
    
    /*
     * =========================================================
     * OPEN CHRIS TITUS DEBLOATING TOOL
     * =========================================================
     */

    @PostMapping("/system/debloating")
    public String openDebloatingTool() {

        windowsSystemService
                .openDebloating();

        return "redirect:/system";
    }
    
    /*
     * =========================================================
     * CHANGE POWER PLAN
     * =========================================================
     */

    @PostMapping("/system/power-plan")
    public String changePowerPlan(
            @RequestParam String powerPlan) {

        windowsSystemService
                .changePowerPlan(powerPlan);


        return "redirect:/system?powerPlanSuccess=true";
    }


    /*
     * =========================================================
     * DISPLAY TIMEOUT
     * =========================================================
     */

    @PostMapping("/system/display-timeout")
    public String changeDisplayTimeout(
            @RequestParam int minutes) {

        windowsSystemService
                .changeDisplayTimeout(minutes);


        return "redirect:/system?displayTimeoutSuccess=true";
    }


    /*
     * =========================================================
     * SLEEP TIMEOUT
     * =========================================================
     */

    @PostMapping("/system/sleep-timeout")
    public String changeSleepTimeout(
            @RequestParam int minutes) {

        windowsSystemService
                .changeSleepTimeout(minutes);


        return "redirect:/system?sleepTimeoutSuccess=true";
    }


    /*
     * =========================================================
     * HIBERNATE
     * =========================================================
     */

    @PostMapping("/system/hibernate")
    public String changeHibernate(
            @RequestParam boolean enabled) {

        windowsSystemService
                .setHibernateEnabled(enabled);


        return "redirect:/system?hibernateSuccess=true";
    }
    
    /*
     * =========================================================
     * OPEN SYSTEM RESTORE
     * =========================================================
     */

    @PostMapping("/system/system-restore")
    public String openSystemRestore() {

        windowsSystemService
                .openSystemRestore();


        return "redirect:/system";
    }
    
    /*
     * =========================================================
     * OPEN SYSTEM PROTECTION
     * =========================================================
     */

    @PostMapping("/system/system-protection")
    public String openSystemProtection() {

        windowsSystemService
                .openSystemProtection();


        return "redirect:/system";
    }


    /*
     * =========================================================
     * RUN SYSTEM FILE CHECKER
     * =========================================================
     */

    @PostMapping("/system/sfc")
    public String runSystemFileChecker() {

        windowsSystemService
                .runSystemFileChecker();


        return "redirect:/system?sfcStarted=true";
    }


    /*
     * =========================================================
     * RUN DISM RESTORE HEALTH
     * =========================================================
     */

    @PostMapping("/system/dism")
    public String runDismRestoreHealth() {

        windowsSystemService
                .runDismRestoreHealth();


        return "redirect:/system?dismStarted=true";
    }
    
    /*
     * =========================================================
     * SYSTEM ACTIONS
     * =========================================================
     */

    @PostMapping("/system/lock")
    public String lockComputer() {

        windowsSystemService
                .lockComputer();

        return "redirect:/system";
    }


    @PostMapping("/system/sleep")
    public String sleepComputer() {

        windowsSystemService
                .sleepComputer();

        return "redirect:/system";
    }


    @PostMapping("/system/restart")
    public String restartComputer() {

        windowsSystemService
                .restartComputer();

        return "redirect:/system";
    }


    @PostMapping("/system/shutdown")
    public String shutdownComputer() {

        windowsSystemService
                .shutdownComputer();

        return "redirect:/system";
    }
    
    /*
     * =========================================================
     * RESTART TO UEFI FIRMWARE
     * =========================================================
     */

    @PostMapping("/system/firmware")
    public String restartToFirmware() {

        windowsSystemService
                .restartToFirmware();

        return "redirect:/system";
    }
}