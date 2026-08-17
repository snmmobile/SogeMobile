package com.example.viewonlybrowser;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TrustedSitePolicyTest {
    private final TrustedSitePolicy policy = new TrustedSitePolicy(
            "sogebanking.com",
            "www2.sogebanking.com");

    @Test
    public void acceptsSecureSogebankingPagesAndWwwAlias() {
        assertTrue(policy.matches("https://sogebanking.com/"));
        assertTrue(policy.matches("https://sogebanking.com/login?language=fr"));
        assertTrue(policy.matches("https://www.sogebanking.com/dashboard"));
        assertTrue(policy.matches("https://www2.sogebanking.com/sogebanking/#f"));
        assertTrue(policy.matches("https://www2.sogebanking.com/sogebanking/index.html"));
    }

    @Test
    public void rejectsOtherDomainsAndInsecureUrls() {
        assertFalse(policy.matches("https://corporate.sogebanking.com/"));
        assertFalse(policy.matches("https://www3.sogebanking.com/sogebanking/"));
        assertFalse(policy.matches("https://sogebanking.com.evil.test/"));
        assertFalse(policy.matches("http://sogebanking.com/"));
        assertFalse(policy.matches("javascript:alert(1)"));
    }

    @Test
    public void rejectsMalformedAndMissingUrls() {
        assertFalse(policy.matches(null));
        assertFalse(policy.matches("not a url"));
    }
}
