package com.example.viewonlybrowser;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ApkIntegrityTest {
    @Test
    public void verifiesTheExpectedSha256WithoutAcceptingAnotherDigest() throws Exception {
        File file = File.createTempFile("sogemobile-update-", ".apk");
        try {
            Files.write(file.toPath(), "verified update".getBytes(StandardCharsets.UTF_8));

            assertTrue(ApkIntegrity.matchesSha256(file,
                    "59f19f34399b14e5f1628642e9ce341d660094ba76898e4db6b1875f525b6a6a"));
            assertFalse(ApkIntegrity.matchesSha256(file,
                    "09f19f34399b14e5f1628642e9ce341d660094ba76898e4db6b1875f525b6a6a"));
        } finally {
            file.delete();
        }
    }
}
