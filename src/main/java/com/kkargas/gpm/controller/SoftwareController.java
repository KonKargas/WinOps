package com.kkargas.gpm.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.kkargas.gpm.model.InstalledApplication;
import com.kkargas.gpm.model.SoftwareInstallation;
import com.kkargas.gpm.model.SoftwarePackage;
import com.kkargas.gpm.model.SoftwareUninstallation;
import com.kkargas.gpm.model.SoftwareUpdate;
import com.kkargas.gpm.service.WindowsSoftwareService;


@Controller
public class SoftwareController {

    private final WindowsSoftwareService
            windowsSoftwareService;


    public SoftwareController(
            WindowsSoftwareService windowsSoftwareService) {

        this.windowsSoftwareService =
                windowsSoftwareService;
    }


    /*
     * =========================================================
     * SOFTWARE PAGE
     * =========================================================
     */

    @GetMapping("/software")
    public String viewSoftware() {
        return "Software";
    }

    @GetMapping("/software/data")
    public String viewSoftwareData(Model model) {
        populateSoftwareModel(model);
        return "fragments/software-data :: content";
    }

    private void populateSoftwareModel(Model model) {
        boolean wingetAvailable =
                windowsSoftwareService
                        .isWingetAvailable();


        model.addAttribute(
                "wingetAvailable",
                wingetAvailable
        );
        
        if (!wingetAvailable) {

            model.addAttribute(
                    "wingetVersion",
                    "Not available"
            );

            model.addAttribute(
                    "installedApplications",
                    List.of()
            );

            model.addAttribute(
                    "installedCount",
                    0
            );

            model.addAttribute(
                    "updateCount",
                    0
            );

            model.addAttribute(
                    "wingetSources",
                    List.of()
            );

            model.addAttribute(
                    "sourceCount",
                    0
            );
            
            model.addAttribute(
                    "softwareCatalog",
                    List.of()
            );

            return;
        }


        /*
         * WinGet version
         */

        String wingetVersion =
                windowsSoftwareService
                        .getWingetVersion();


        /*
         * Installed applications
         */

        List<InstalledApplication> applications =
                windowsSoftwareService
                        .getInstalledApplications();


        /*
         * Available updates
         */

        long updateCount =
                windowsSoftwareService
                        .getAvailableUpdateCount(
                                applications
                        );


        /*
         * Package sources
         */

        List<String> sources =
                windowsSoftwareService
                        .getWingetSources();


        /*
         * Model
         */

        model.addAttribute(
                "wingetVersion",
                wingetVersion
        );


        model.addAttribute(
                "installedApplications",
                applications
        );


        model.addAttribute(
                "installedCount",
                applications.size()
        );


        model.addAttribute(
                "updateCount",
                updateCount
        );


        model.addAttribute(
                "wingetSources",
                sources
        );


        model.addAttribute(
                "sourceCount",
                sources.size()
        );
        
        List<SoftwarePackage> softwareCatalog =
                windowsSoftwareService
                        .getSoftwareCatalog(applications);


        model.addAttribute(
                "softwareCatalog",
                softwareCatalog
        );


    }

    /*
     * =========================================================
     * INSTALL SOFTWARE
     * =========================================================
     */

    @PostMapping("/software/install/start")
    @ResponseBody
    public SoftwareInstallation startInstallation(
            @RequestParam String packageId) {
        return windowsSoftwareService.startInstallation(packageId);
    }

    @GetMapping("/software/install/status")
    @ResponseBody
    public SoftwareInstallation getInstallationStatus(
            @RequestParam String packageId) {
        return windowsSoftwareService.getInstallation(packageId);
    }

    /*
     * =========================================================
     * UNINSTALL SOFTWARE
     * =========================================================
     */

    @PostMapping("/software/uninstall/start")
    @ResponseBody
    public SoftwareUninstallation startUninstallation(
            @RequestParam String packageId) {

        return windowsSoftwareService
                .startUninstallation(packageId);
    }

    @GetMapping("/software/uninstall/status")
    @ResponseBody
    public SoftwareUninstallation getUninstallationStatus(
            @RequestParam String packageId) {

        return windowsSoftwareService
                .getUninstallation(packageId);
    }


    /*
     * =========================================================
     * SOFTWARE UPDATES
     * =========================================================
     */

    @PostMapping("/software/update/start")
    @ResponseBody
    public SoftwareUpdate startUpdate(
            @RequestParam String packageId) {

        return windowsSoftwareService
                .startUpdate(packageId);
    }

    @GetMapping("/software/update/status")
    @ResponseBody
    public SoftwareUpdate getUpdateStatus(
            @RequestParam String packageId) {

        return windowsSoftwareService
                .getUpdate(packageId);
    }

    @PostMapping("/software/update/all/start")
    @ResponseBody
    public SoftwareUpdate startUpdateAll() {

        return windowsSoftwareService
                .startUpdateAll();
    }

    @GetMapping("/software/update/all/status")
    @ResponseBody
    public SoftwareUpdate getUpdateAllStatus() {

        return windowsSoftwareService
                .getUpdateAll();
    }

    @GetMapping("/software/operations/active")
    @ResponseBody
    public java.util.Map<String, Object> getActiveOperations() {
        return windowsSoftwareService.getActiveOperations();
    }
    
    @PostMapping("/software/tools/refresh-sources")
    @ResponseBody
    public Map<String, Object> refreshWingetSources() {

        boolean success =
                windowsSoftwareService.refreshWingetSources();

        Map<String, Object> response =
                new HashMap<>();

        response.put(
                "success",
                success
        );

        response.put(
                "message",
                success
                        ? "WinGet sources refreshed successfully."
                        : "WinGet sources could not be refreshed."
        );

        return response;
    }
    
    @PostMapping("/software/tools/open-installed-apps")
    @ResponseBody
    public Map<String, Object> openInstalledApps() {

        boolean success =
                windowsSoftwareService.openInstalledApps();

        Map<String, Object> response =
                new HashMap<>();

        response.put(
                "success",
                success
        );

        response.put(
                "message",
                success
                        ? "Windows Installed Apps opened successfully."
                        : "Windows Installed Apps could not be opened."
        );

        return response;
    }
    
    @GetMapping("/software/tools/diagnostics")
    @ResponseBody
    public Map<String, Object> runWingetDiagnostics() {

        return windowsSoftwareService
                .runWingetDiagnostics();
    }
}
