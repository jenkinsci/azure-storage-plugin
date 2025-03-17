package com.microsoftopentechnologies.windowsazurestorage;

import com.microsoftopentechnologies.windowsazurestorage.helper.Constants;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AzureStorageTest {

    @Test
    void testContainerName() {

        // checking for container name length of 3 characters
        assertTrue(Utils.validateContainerName("abc"));

        // checking for container name length of 5 characters
        assertTrue(Utils.validateContainerName("1abc3"));

        // checking for container name length of 63 characters
        assertTrue(Utils.validateContainerName("abcde12345abcde12345abcde12345abcde12345abcde12345abcde12345abc"));

        // checking for container name with dash (-) characters
        assertTrue(Utils.validateContainerName("abc-def"));

        // checking for special container name
        assertTrue(Utils.validateContainerName("$root"));
        assertTrue(Utils.validateContainerName("$web"));

        // Negative case : consecutive dashes are not allowed
        assertFalse(Utils.validateContainerName("abc--def"));

        // Negative case : dash canot be first character
        assertFalse(Utils.validateContainerName("-abc12def"));

        // Negative case : dash canot be last character
        assertFalse(Utils.validateContainerName("abc12def-"));

        // Negative case : more than 63 characters
        assertFalse(Utils.validateContainerName("abcde12345abcde12345abcde12345abcde12345abcde12345abcde12345abc34"));

        // Negative case : only 2 characters
        assertFalse(Utils.validateContainerName("ab"));
    }

    @Test
    void testGetBlobEPReturnsDefaultURL() {
        // return default blob host given null URL
        assertEquals(Constants.DEF_BLOB_URL, Utils.getBlobEP(null));
        // return default blob host given the default blob URL
        assertEquals(Constants.DEF_BLOB_URL, Utils.getBlobEP(Constants.DEF_BLOB_URL));
    }

    @Test
    void testGetBlobEPAddsHttpProtocolWhenNoProtocolPresent() {
        assertEquals("https://blob.host.domain.tld/", Utils.getBlobEP("blob.host.domain.tld"));
    }

    @Test
    void testGetBlobEPAddsTrailingForwardSlashWhenMissing() {
        assertEquals("https://blob.core.windows.net/", Utils.getBlobEP("https://blob.core.windows.net"));
    }
}
