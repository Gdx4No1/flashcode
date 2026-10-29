package com.gejia.flashcode.flashcodeportalservice.flash.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerateAppWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void failedIncrementalWriteKeepsAllExistingFiles() throws Exception {
        Path app = GenerateAppWriter.writeFiles(tempDir, "103", Map.of("index.html", "old"), true);
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("index.html", "new");
        changes.put("index.html/child", "conflict");
        assertThrows(java.io.IOException.class,
                () -> GenerateAppWriter.writeFiles(tempDir, "103", changes, false));
        assertEquals("old", Files.readString(app.resolve("index.html")));
    }

    @Test
    void successfulIncrementalWriteKeepsUnchangedFiles() throws Exception {
        Path app = GenerateAppWriter.writeFiles(tempDir, "104", Map.of("index.html", "old", "keep.txt", "keep"), true);
        GenerateAppWriter.writeFiles(tempDir, "104", Map.of("index.html", "new"), false);
        assertEquals("new", Files.readString(app.resolve("index.html")));
        assertEquals("keep", Files.readString(app.resolve("keep.txt")));
    }

    @Test
    void rejectsWindowsDriveAndParentPathsBeforeChangingFiles() throws Exception {
        Path app = GenerateAppWriter.writeFiles(tempDir, "105", Map.of("index.html", "old"), true);
        for (String path : new String[]{"D:/outside.txt", "../outside.txt", "a/../outside.txt", "\\\\server\\file"}) {
            Map<String, String> changes = new LinkedHashMap<>();
            changes.put("index.html", "new");
            changes.put(path, "bad");
            assertThrows(IllegalArgumentException.class,
                    () -> GenerateAppWriter.writeFiles(tempDir, "105", changes, false));
            assertEquals("old", Files.readString(app.resolve("index.html")));
        }
    }

    @Test
    void publishesBinaryAssetsWithoutStaleFiles() throws Exception {
        Path source = Files.createDirectories(tempDir.resolve("build"));
        Files.writeString(source.resolve("index.html"), "new preview");
        byte[] binary = {0, 1, -1, 7};
        Files.write(source.resolve("image.png"), binary);
        Path preview = Files.createDirectories(tempDir.resolve("preview"));
        Files.writeString(preview.resolve("old.txt"), "old");
        GenerateAppWriter.publishDirectory(source, preview);
        assertFalse(Files.exists(preview.resolve("old.txt")));
        org.junit.jupiter.api.Assertions.assertArrayEquals(binary, Files.readAllBytes(preview.resolve("image.png")));
    }

    @Test
    void missingEntryDoesNotReplacePreviousPreview() throws Exception {
        Path source = Files.createDirectories(tempDir.resolve("build"));
        Files.writeString(source.resolve("package.json"), "{}");
        Path preview = Files.createDirectories(tempDir.resolve("preview"));
        Files.writeString(preview.resolve("index.html"), "old");
        assertThrows(java.io.IOException.class, () -> GenerateAppWriter.publishDirectory(source, preview));
        assertEquals("old", Files.readString(preview.resolve("index.html")));
    }

    @Test
    void replacesPreviousApplicationWithoutLeavingStaleFiles() throws Exception {
        Path appDir = GenerateAppWriter.writeFiles(
                tempDir,
                "10000001",
                Map.of("old.txt", "old"),
                true);

        GenerateAppWriter.writeFiles(
                tempDir,
                "10000001",
                Map.of("frontend/index.html", "new"),
                true);

        assertFalse(Files.exists(appDir.resolve("old.txt")));
        assertEquals("new", Files.readString(appDir.resolve("frontend/index.html")));
    }

    @Test
    void keepsPreviousApplicationWhenNewFilesContainUnsafePath() throws Exception {
        Path appDir = GenerateAppWriter.writeFiles(
                tempDir,
                "10000002",
                Map.of("index.html", "old"),
                true);
        Map<String, String> unsafeFiles = new LinkedHashMap<>();
        unsafeFiles.put("index.html", "new");
        unsafeFiles.put("../outside.txt", "unsafe");

        assertThrows(IllegalArgumentException.class,
                () -> GenerateAppWriter.writeFiles(tempDir, "10000002", unsafeFiles, true));

        assertEquals("old", Files.readString(appDir.resolve("index.html")));
        assertFalse(Files.exists(tempDir.resolve("outside.txt")));
    }

    @Test
    void rejectsApplicationIdOutsideUserCodeDirectory() {
        assertThrows(IllegalArgumentException.class,
                () -> GenerateAppWriter.writeFiles(tempDir, "../10000003", Map.of("index.html", "test"), true));
        assertTrue(Files.notExists(tempDir.getParent().resolve("10000003")));
    }
}
