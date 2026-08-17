package com.example.viewonlybrowser;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Limits dashboard detection to the configured HTTPS host and its www alias. */
final class TrustedSitePolicy {
    private final Set<String> trustedHosts = new HashSet<>();

    TrustedSitePolicy(String... hosts) {
        for (String host : hosts) {
            String normalizedHost = normalizeHost(host);
            if (normalizedHost.isEmpty() || normalizedHost.contains("/")) {
                throw new IllegalArgumentException("Trusted hosts must contain valid hostnames");
            }
            trustedHosts.add(normalizedHost);
        }
    }

    boolean matches(String candidateUrl) {
        if (candidateUrl == null) {
            return false;
        }

        try {
            URI candidate = new URI(candidateUrl);
            if (!"https".equalsIgnoreCase(candidate.getScheme()) || candidate.getHost() == null) {
                return false;
            }

            String candidateHost = normalizeHost(candidate.getHost());
            return trustedHosts.contains(candidateHost);
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static String normalizeHost(String host) {
        String normalized = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("www.") ? normalized.substring(4) : normalized;
    }
}
