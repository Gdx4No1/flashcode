package com.gejia.flashcode.flashcodeportalservice.flash.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("flashcode.user-code-root")
class SourceRootConfigurationTest {
    @TempDir Path temporary;
    private final String previous = System.getProperty("flashcode.user-code-root");
    @AfterEach void restore() {
        if (previous == null) System.clearProperty("flashcode.user-code-root");
        else System.setProperty("flashcode.user-code-root", previous);
    }
    @Test void explicitAbsoluteRootIsUsed() throws IOException {
        Path root = temporary.resolve("isolated");
        System.setProperty("flashcode.user-code-root", root.toString());
        assertEquals(root.toAbsolutePath(), GenerateAppWriter.ensureUsercodeDir());
        assertTrue(Files.isDirectory(root));
    }
    @Test void relativeOverrideIsRejected() {
        System.setProperty("flashcode.user-code-root", "../other-project");
        assertThrows(IOException.class, GenerateAppWriter::ensureUsercodeDir);
    }
}
