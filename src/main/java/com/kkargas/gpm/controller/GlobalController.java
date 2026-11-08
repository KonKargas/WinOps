package com.kkargas.gpm.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalController {
	

    @ModelAttribute("computerName")
    public String computerName() {

        String computerName =
                System.getenv("COMPUTERNAME");

        return computerName != null
                ? computerName
                : "Unknown";
    }


    @ModelAttribute("operatingSystem")
    public String operatingSystem() {
        return System.getProperty("os.name");
    }

}
