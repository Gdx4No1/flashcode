package com.gejia.flashcode.flashcodeportalservice.flash.config;

import com.gejia.flashcode.flashcodecommondomain.constants.TokenConstants;
import com.gejia.flashcode.flashcodecommonsecurity.utils.JwtUtil;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TokenSecretConfigurationTest {
    private record Result(int exit, String output) {}

    private Result probe(String secret) throws Exception {
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path classPathJar = Files.createTempFile(Path.of(System.getProperty("java.io.tmpdir")), "jwt-probe-", ".jar");
        writeClassPathJar(classPathJar, classPath);
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-Duser.home=" + System.getProperty("user.home"),
                "-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"),
                "-cp", classPathJar.toAbsolutePath().toString(),
                Probe.class.getName()).redirectErrorStream(true);
        if (secret == null) builder.environment().remove("FLASHCODE_JWT_SECRET");
        else builder.environment().put("FLASHCODE_JWT_SECRET", secret);
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS), "Secret probe timed out");
            return new Result(process.exitValue(), new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(classPathJar);
        }
    }

    private void writeClassPathJar(Path jarPath, String classPath) throws Exception {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        String manifestClassPath = java.util.Arrays.stream(classPath.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
                .map(entry -> Path.of(entry).toAbsolutePath().normalize().toUri().toASCIIString())
                .collect(java.util.stream.Collectors.joining(" "));
        attributes.put(Attributes.Name.CLASS_PATH, manifestClassPath);
        try (OutputStream output = Files.newOutputStream(jarPath);
             JarOutputStream jar = new JarOutputStream(output, manifest)) {
            // An empty JAR with a manifest keeps the child JVM command line short on Windows.
        }
    }

    @Test void missingSecretFailsWithoutUsingOldKey() throws Exception {
        Result result = probe(null);
        assertNotEquals(0, result.exit());
        assertTrue(result.output().contains("Configure FLASHCODE_JWT_SECRET"), result.output());
    }

    @Test void invalidBase64FailsWithoutEchoingValue() throws Exception {
        String invalid = "invalid-secret-do-not-print!";
        Result result = probe(invalid);
        assertNotEquals(0, result.exit());
        assertFalse(result.output().contains(invalid));
    }

    @Test void shortKeyFails() throws Exception {
        Result result = probe(Base64.getEncoder().encodeToString(new byte[32]));
        assertNotEquals(0, result.exit());
    }

    @Test void validKeyWorks() throws Exception {
        Result result = probe(Base64.getEncoder().encodeToString(new byte[64]));
        assertEquals(0, result.exit(), result.output());
        assertTrue(result.output().contains("TOKEN_OK"));
    }

    @Test void configuredKeySignsAndVerifiesJwt() {
        assertTrue(Base64.getDecoder().decode(TokenConstants.SECRET).length >= 64);
        String token = JwtUtil.createToken(Map.of("userId", "delivery-test"));
        assertEquals("delivery-test", JwtUtil.parseToken(token).get("userId"));
    }

    public static class Probe {
        public static void main(String[] args) {
            if (Base64.getDecoder().decode(TokenConstants.SECRET).length < 64) throw new IllegalStateException("Invalid key");
            System.out.println("TOKEN_OK");
        }
    }
}
