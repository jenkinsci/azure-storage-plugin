package com.microsoftopentechnologies.windowsazurestorage;

import org.junit.Test;

import junit.framework.TestCase;

import com.microsoftopentechnologies.windowsazurestorage.helper.Constants;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;

public class WindowsAzureStorageTest extends TestCase {

	@Test
	public void testContainerName() throws Exception {

		// checking for container name length of 3 characters
		assertEquals(true, Utils.validateContainerName("abc"));

		// checking for container name length of 5 characters
		assertEquals(true, Utils.validateContainerName("1abc3"));

		// checking for container name length of 63 characters
		assertEquals(true, Utils.validateContainerName("abcde12345abcde12345abcde12345abcde12345abcde12345abcde12345abc"));

		// checking for container name with dash (-) characters
		assertEquals(true, Utils.validateContainerName("abc-def"));

        // checking for special container name
        assertEquals(true, Utils.validateContainerName("$root"));
        assertEquals(true, Utils.validateContainerName("$web"));

		// Negative case : consecutive dashes are not allowed
		assertEquals(false, Utils.validateContainerName("abc--def"));

		// Negative case : dash canot be first character
		assertEquals(false, Utils.validateContainerName("-abc12def"));

		// Negative case : dash canot be last character
		assertEquals(false, Utils.validateContainerName("abc12def-"));

		// Negative case : more than 63 characters
		assertEquals(false, Utils.validateContainerName("abcde12345abcde12345abcde12345abcde12345abcde12345abcde12345abc34"));

		// Negative case : only 2 characters
		assertEquals(false, Utils.validateContainerName("ab"));
	}

	@Test
	public void testGetBlobEPReturnsDefaultURL() throws Exception {
		// return default blob host given null URL
		assertEquals(Constants.DEF_BLOB_URL, Utils.getBlobEP(null));
		// return default blob host given the default blob URL
		assertEquals(Constants.DEF_BLOB_URL, Utils.getBlobEP(Constants.DEF_BLOB_URL));
	}

	@Test
	public void testGetBlobEPAddsHttpProtocolWhenNoProtocolPresent() throws Exception {
		assertEquals("http://blob.host.domain.tld/", Utils.getBlobEP("blob.host.domain.tld"));
	}

	@Test
	public void testGetBlobEPAddsTrailingForwardSlashWhenMissing() throws Exception {
		assertEquals("https://blob.core.windows.net/", Utils.getBlobEP("https://blob.core.windows.net"));
	}

}
