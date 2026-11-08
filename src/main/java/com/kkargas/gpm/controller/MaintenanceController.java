package com.kkargas.gpm.controller;

import java.io.IOException;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.kkargas.gpm.service.WindowsMaintenanceService;

@Controller
public class MaintenanceController {

    private final WindowsMaintenanceService maintenanceService;

    public MaintenanceController(WindowsMaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @GetMapping("/maintenance")
    public String getMaintenancePage(Model model) {
        model.addAttribute("systemDrive", maintenanceService.getSystemDrive());
        return "Maintenance";
    }

    @PostMapping("/maintenance/tools/{tool}")
    @ResponseBody
    public Map<String, Object> openTool(@PathVariable String tool) {
        try {
            maintenanceService.openTool(tool);
            return Map.of("success", true, "message", "Windows utility launched.");
        } catch (IllegalArgumentException exception) {
            return Map.of("success", false, "message", "Unsupported maintenance tool.");
        } catch (IOException exception) {
            return Map.of("success", false, "message", "Could not launch this Windows utility.");
        }
    }

    @PostMapping("/maintenance/health/start")
    @ResponseBody
    public Map<String, Object> startHealthCheck() {
        return maintenanceService.startHealthCheck();
    }

    @GetMapping("/maintenance/health/status")
    @ResponseBody
    public Map<String, Object> healthCheckStatus() {
        return maintenanceService.getHealthCheck();
    }
}
