/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.microsoftopentechnologies.windowsazurestorage.helper;

import jenkins.model.Jenkins;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author arroyc
 */
@WithJenkins
class UtilsTest {

    private static JenkinsRule j;

    @BeforeAll
    static void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Test of validateContainerName method, of class Utils.
     */
    @Test
    @Disabled
    void testValidateContainerName() {
        System.out.println("check test result in testContainerName in \\windows-azure-storage-plugin\\src\\test\\java\\com\\microsoftopentechnologies\\windowsazurestorage\\WindowsAzureStorageTest.java");
    }

    /**
     * Test of validateBlobName method, of class Utils.
     */
    @Test
    void testValidateBlobName() {
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
        assertTrue(Utils.validateBlobName("abcd"));
    }

    /**
     * Test of isNullOrEmpty method, of class Utils.
     */
    @Test
    @Disabled
    void testIsNullOrEmpty() {
        System.out.println("check test result in testNullOrEmptyCheck in \\windows-azure-storage-plugin\\src\\test\\java\\com\\microsoftopentechnologies\\windowsazurestorage\\WindowsAzureStorageTest.java");
    }

    /**
     * Test of containTokens method, of class Utils.
     */
    @Test
    void testContainTokens() {
        System.out.println("containTokens");
        String text = "";
        boolean expResult = false;
        boolean result = Utils.containTokens(text);
        assertEquals(expResult, result);
        result = Utils.containTokens("$Aab123_");
        assertTrue(result);
        assertTrue(Utils.containTokens("${Aab123_}"));
        assertFalse(Utils.containTokens("${Aab123_"));
    }

    /**
     * Test of getBlobEP method, of class Utils.
     */
    @Test
    void testGetBlobEP() {
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
    @Disabled
    void testGetDefaultBlobURL() {
        System.out.println("check result in testGetBlobEPReturnsDefaultURL in \\windows-azure-storage-plugin\\src\\test\\java\\com\\microsoftopentechnologies\\windowsazurestorage\\WindowsAzureStorageTest.java");
    }

    /**
     * Test of getJenkinsInstance method, of class Utils.
     */
    @Test
    void testGetJenkinsInstance() {
        System.out.println("getJenkinsInstance");
        Jenkins expResult = Jenkins.get();
        Jenkins result = Jenkins.get();
        assertEquals(expResult, result);
    }

    /**
     * Test of getMD5 method, of class Utils.
     */
    @Test
    void testGetMD5() {
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
    void testGetWorkDirectory() {
        System.out.println("getWorkDirectory");
        String expResult = Jenkins.get().root.getAbsolutePath();
        String result = Utils.getWorkDirectory();
        assertEquals(expResult, result);
    }

    /**
     * Test of getPluginInstance method, of class Utils.
     */
    @Test
    void testGetPluginInstance() {
        System.out.println("getPluginInstance");
        String expResult = Jenkins.get().getLegacyInstanceId();
        String result = Utils.getPluginInstance();
        assertEquals(expResult, result);
    }

    /**
     * Test of getPluginVersion method, of class Utils.
     */
    @Test
    void testGetPluginVersion() {
        System.out.println("getPluginVersion");
        String expResult = Utils.class.getPackage().getImplementationVersion();
        String result = Utils.getPluginVersion();
        assertEquals(expResult, result);
    }
}
