package com.kkargas.gpm.config;

import java.io.IOException;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class BrowserLauncher {

    private static final String APPLICATION_URL =
            "http://localhost:8080";

    @EventListener(ApplicationReadyEvent.class)
    public void openBrowser() {

        if (!System.getProperty("os.name")
                .toLowerCase()
                .contains("windows")) {

            return;
        }

        try {

            new ProcessBuilder(
                    "cmd.exe",
                    "/c",
                    "start",
                    "",
                    APPLICATION_URL
            ).start();

        } catch (IOException e) {

            System.err.println(
                    "Could not open browser automatically: "
                    + e.getMessage()
            );
        }
    }
}