package com.gejia.flashcode.flashcodeportalservice.flash.utils;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BuildCommandExecutorTest {
    @TempDir Path directory;

    private List<String> command(String mode) throws Exception {
        return List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
                Path.of(Worker.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(),
                Worker.class.getName(), mode);
    }

    @Test void acceptsSuccessfulBuild() {
        assertDoesNotThrow(() -> BuildCommandExecutor.run(command("ok"), directory, Duration.ofSeconds(10)));
    }

    @Test void reportsBuildFailure() {
        var error = assertThrows(IOException.class,
                () -> BuildCommandExecutor.run(command("fail"), directory, Duration.ofSeconds(10)));
        assertTrue(error.getMessage().contains("退出码=7"));
    }

    @Test void timesOutSilentBuild() {
        long start = System.nanoTime();
        var error = assertThrows(IOException.class,
                () -> BuildCommandExecutor.run(command("sleep"), directory, Duration.ofMillis(300)));
        assertTrue(error.getMessage().contains("超时"));
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 5);
    }

    @Test void boundsLargeBuildLog() {
        var error = assertThrows(IOException.class,
                () -> BuildCommandExecutor.run(command("flood"), directory, Duration.ofSeconds(10)));
        assertTrue(error.getMessage().contains("退出码=9"));
        assertTrue(error.getMessage().contains("x".repeat(1000)));
        assertTrue(error.getMessage().length() < 17000);
    }

    public static class Worker {
        public static void main(String[] args) throws Exception {
            switch (args[0]) {
                case "fail" -> System.exit(7);
                case "sleep" -> Thread.sleep(30000);
                case "flood" -> { System.out.print("x".repeat(100000)); System.exit(9); }
                default -> System.out.print("OK");
            }
        }
    }
}
