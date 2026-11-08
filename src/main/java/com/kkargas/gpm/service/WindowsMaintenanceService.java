package com.kkargas.gpm.service;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

@Service
public class WindowsMaintenanceService {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "winops-maintenance-diagnostics");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicLong nextJobId = new AtomicLong();
    private volatile DiagnosticJob latestJob;

    public record DriveInfo(String name, long totalBytes, long freeBytes, long usedBytes, int usedPercent) {}

    public DriveInfo getSystemDrive() {
        try {
            String root = System.getenv("SystemDrive");
            if (root == null || root.isBlank()) root = "C:";
            Path drive = Path.of(root + "\\");
            FileStore store = Files.getFileStore(drive);
            long total = store.getTotalSpace();
            long free = store.getUsableSpace();
            long used = Math.max(0, total - free);
            int percent = total == 0 ? 0 : (int) Math.round(100.0 * used / total);
            return new DriveInfo(root, total, free, used, percent);
        } catch (IOException | RuntimeException exception) {
            return new DriveInfo("System drive", 0, 0, 0, 0);
        }
    }

    // Only predefined utilities can be launched. No user-provided commands or paths.
    public void openTool(String tool) throws IOException {
        String[] command = switch (tool) {
            case "storage" -> new String[] { "cmd.exe", "/c", "start", "", "ms-settings:storagesense" };
            case "cleanup" -> new String[] { "cleanmgr.exe" };
            case "temp" -> new String[] { "explorer.exe", System.getenv().getOrDefault("TEMP", "C:\\Windows\\Temp") };
            case "events" -> new String[] { "mmc.exe", "eventvwr.msc" };
            case "reliability" -> new String[] { "perfmon.exe", "/rel" };
            case "taskmanager" -> new String[] { "taskmgr.exe" };
            case "services" -> new String[] { "mmc.exe", "services.msc" };
            case "scheduler" -> new String[] { "mmc.exe", "taskschd.msc" };
            case "startup" -> new String[] { "cmd.exe", "/c", "start", "", "ms-settings:startupapps" };
            case "updates" -> new String[] { "cmd.exe", "/c", "start", "", "ms-settings:windowsupdate" };
            default -> throw new IllegalArgumentException("Unknown maintenance tool");
        };
        new ProcessBuilder(command).start();
    }

    public synchronized Map<String, Object> startHealthCheck() {
        if (latestJob != null && !latestJob.finished) return latestJob.snapshot();
        DiagnosticJob job = new DiagnosticJob(nextJobId.incrementAndGet());
        latestJob = job;
        CompletableFuture.runAsync(() -> executeHealthCheck(job), executor);
        return job.snapshot();
    }

    public Map<String, Object> getHealthCheck() {
        DiagnosticJob job = latestJob;
        return job == null ? Map.of("status", "IDLE", "finished", true) : job.snapshot();
    }

    private void executeHealthCheck(DiagnosticJob job) {
        Process process = null;
        try {
            // CheckHealth is read-only; repair operations remain in the System module.
            process = new ProcessBuilder("dism.exe", "/Online", "/Cleanup-Image", "/CheckHealth")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                job.complete("FAILED", "DISM health check timed out after 120 seconds.");
                return;
            }
            int exitCode = process.exitValue();
            job.complete(exitCode == 0 ? "COMPLETED" : "FAILED",
                    exitCode == 0
                            ? "DISM CheckHealth finished successfully. For detailed repair guidance, use System → Recovery & System Repair."
                            : "DISM returned exit code " + exitCode + ". Check Windows logs for details.");
        } catch (IOException exception) {
            job.complete("FAILED", "Could not start DISM. Run WinOps with administrator privileges.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            job.complete("FAILED", "Health check was interrupted.");
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private static final class DiagnosticJob {
        final long id;
        final String startedAt = Instant.now().toString();
        volatile String status = "RUNNING";
        volatile String message = "Checking Windows component store health...";
        volatile boolean finished;

        DiagnosticJob(long id) { this.id = id; }

        synchronized void complete(String status, String message) {
            this.message = message;
            this.status = status;
            this.finished = true;
        }

        Map<String, Object> snapshot() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("startedAt", startedAt);
            result.put("status", status);
            result.put("message", message);
            result.put("finished", finished);
            return result;
        }
    }
}
