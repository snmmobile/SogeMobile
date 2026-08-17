package com.example.viewonlybrowser;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ViewOnlyBlockPolicyTest {
    @Test
    public void blocksTheExactSecureSensitiveDocuments() {
        assertTrue(ViewOnlyBlockPolicy.blocks(
                "https://www2.sogebanking.com/sogebanking/pages_personal/account_details.html"));
        assertTrue(ViewOnlyBlockPolicy.blocks(
                "https://www2.sogebanking.com/sogebanking/pages_personal/account_details.html?account=2"));
        assertTrue(ViewOnlyBlockPolicy.blocks(
                "https://www2.sogebanking.com/pages_personal/account_details.html"));
        assertTrue(ViewOnlyBlockPolicy.blocks(
                "https://www2.sogebanking.com/sogebanking/pages_personal/transfers_landing.html"));
        assertTrue(ViewOnlyBlockPolicy.blocks(
                "https://www2.sogebanking.com/pages_personal/transfers_landing.html?from=dashboard"));
    }

    @Test
    public void doesNotBlockOtherPagesOrUntrustedUrls() {
        assertFalse(ViewOnlyBlockPolicy.blocks(
                "https://www2.sogebanking.com/sogebanking/pages_personal/transfers_history.html"));
        assertFalse(ViewOnlyBlockPolicy.blocks(
                "http://www2.sogebanking.com/sogebanking/pages_personal/account_details.html"));
        assertFalse(ViewOnlyBlockPolicy.blocks(
                "https://www2.sogebanking.com.evil.test/sogebanking/pages_personal/account_details.html"));
        assertFalse(ViewOnlyBlockPolicy.blocks(
                "https://www2.sogebanking.com.evil.test/pages_personal/transfers_landing.html"));
    }
}
