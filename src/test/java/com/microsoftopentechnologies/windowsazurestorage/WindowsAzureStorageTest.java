package com.microsoftopentechnologies.windowsazurestorage;

import org.junit.Test;

import junit.framework.TestCase;


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
	public void testNullOrEmptyCheck() throws Exception {
		// checking for null string
		assertEquals(true, Utils.isNullOrEmpty(null));
		
		// checking for empty string
		assertEquals(true, Utils.isNullOrEmpty(""));
		
		// checking for string with more spaces
		assertEquals(true, Utils.isNullOrEmpty("   "));
		
		// checking with not null and not empty string
		assertEquals(false, Utils.isNullOrEmpty("xyz"));
	}

	@Test
	public void testGetBlobEPReturnsDefaultURL() throws Exception {
		// return default blob host given null URL
		assertEquals(Utils.DEF_BLOB_URL, Utils.getBlobEP(null));
		// return default blob host given the default blob URL
		assertEquals(Utils.DEF_BLOB_URL, Utils.getBlobEP(Utils.DEF_BLOB_URL));
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
