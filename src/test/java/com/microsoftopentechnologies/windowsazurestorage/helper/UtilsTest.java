/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.microsoftopentechnologies.windowsazurestorage.helper;

import com.microsoft.azure.storage.OperationContext;
import com.microsoft.azure.storage.core.BaseRequest;
import jenkins.model.Jenkins;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;

/**
 * @author arroyc
 */
public class UtilsTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    /**
     * Test of validateContainerName method, of class Utils.
     */
    @Test
    public void testValidateContainerName() {
        System.out.println("check test result in testContainerName in \\windows-azure-storage-plugin\\src\\test\\java\\com\\microsoftopentechnologies\\windowsazurestorage\\WindowsAzureStorageTest.java");
    }

    /**
     * Test of validateBlobName method, of class Utils.
     */
    @Test
    public void testValidateBlobName() {
        System.out.println("validateBlobName");
        boolean expResult = false;
        assertEquals(expResult, Utils.validateBlobName(null));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1025; i++) {
            try {
                sb.append('a');
            } catch (Throwable e) {
                System.out.println(i);
                break;
            }
        }
        assertEquals(expResult, Utils.validateBlobName(sb.toString()));
        assertEquals(true, Utils.validateBlobName("abcd"));
    }

    /**
     * Test of isNullOrEmpty method, of class Utils.
     */
    @Test
    public void testIsNullOrEmpty() {
        System.out.println("check test result in testNullOrEmptyCheck in \\windows-azure-storage-plugin\\src\\test\\java\\com\\microsoftopentechnologies\\windowsazurestorage\\WindowsAzureStorageTest.java");
    }

    /**
     * Test of containTokens method, of class Utils.
     */
    @Test
    public void testContainTokens() {
        System.out.println("containTokens");
        String text = "";
        boolean expResult = false;
        boolean result = Utils.containTokens(text);
        assertEquals(expResult, result);
        result = Utils.containTokens("$Aab123_");
        assertEquals(true, result);
        assertEquals(true, Utils.containTokens("${Aab123_}"));
        assertEquals(false, Utils.containTokens("${Aab123_"));
    }

    /**
     * Test of getBlobEP method, of class Utils.
     */
    @Test
    public void testGetBlobEP() {
        System.out.println("getBlobEP");
        String blobURL = "";
        String expResult = Constants.DEF_BLOB_URL;
        String result = Utils.getBlobEP(blobURL);
        assertEquals(expResult, result);
        result = Utils.getBlobEP("abcd");
        assertEquals("https://abcd/", result);
    }

    /**
     * Test of getDefaultBlobURL method, of class Utils.
     */
    @Test
    public void testGetDefaultBlobURL() {
        System.out.println("check result in testGetBlobEPReturnsDefaultURL in \\windows-azure-storage-plugin\\src\\test\\java\\com\\microsoftopentechnologies\\windowsazurestorage\\WindowsAzureStorageTest.java");
    }

    /**
     * Test of getJenkinsInstance method, of class Utils.
     */
    @Test
    public void testGetJenkinsInstance() {
        System.out.println("getJenkinsInstance");
        Jenkins expResult = Jenkins.getInstance();
        Jenkins result = Utils.getJenkinsInstance();
        assertEquals(expResult, result);
    }

    /**
     * Test of getMD5 method, of class Utils.
     */
    @Test
    public void testGetMD5() {
        System.out.println("getMD5");
        String plainText = "this is a test for md5 hash";
        String expResult = DigestUtils.md5Hex(plainText);
        String result = Utils.getMD5(plainText);
        assertEquals(expResult, result);
    }

    /**
     * Test of getWorkDirectory method, of class Utils.
     */
    @Test
    public void testGetWorkDirectory() {
        System.out.println("getWorkDirectory");
        String expResult = Jenkins.getInstance().root.getAbsolutePath();
        String result = Utils.getWorkDirectory();
        assertEquals(expResult, result);
    }

    /**
     * Test of updateUserAgent method, of class Utils.
     */
    @Test
    public void testUpdateUserAgent() throws Exception {
        System.out.println("updateUserAgent");
        String version = Utils.getPluginVersion() == null ? "local" : Utils.getPluginVersion();

        String expResult = Constants.PLUGIN_NAME + "/" + version + "/" + Utils.getPluginInstance() + "/" + BaseRequest.getUserAgent();
        OperationContext result = Utils.updateUserAgent();
        String actualResult = result.getUserHeaders().get("User-Agent");

        assertEquals(expResult, actualResult);
    }

    /**
     * Test of updateUserAgent method, of class Utils.
     */
    @Test
    public void testUpdateUserAgentWithContentLength() throws Exception {
        System.out.println("updateUserAgentWithContentLength");
        String version = Utils.getPluginVersion() == null ? "local" : Utils.getPluginVersion();
        final long contentLength = 65535L;
        String expResult = Constants.PLUGIN_NAME + "/" + version + "/" + Utils.getPluginInstance() + "/ContentLength/" + contentLength + "/" + BaseRequest.getUserAgent();
        OperationContext result = Utils.updateUserAgent(contentLength);
        String actualResult = result.getUserHeaders().get("User-Agent");

        assertEquals(expResult, actualResult);
    }

    /**
     * Test of getPluginInstance method, of class Utils.
     */
    @Test
    public void testGetPluginInstance() {
        System.out.println("getPluginInstance");
        String expResult = Jenkins.getInstance().getLegacyInstanceId();
        String result = Utils.getPluginInstance();
        assertEquals(expResult, result);
    }

    /**
     * Test of getPluginVersion method, of class Utils.
     */
    @Test
    public void testGetPluginVersion() {
        System.out.println("getPluginVersion");
        String expResult = Utils.class.getPackage().getImplementationVersion();
        String result = Utils.getPluginVersion();
        assertEquals(expResult, result);
    }

}
