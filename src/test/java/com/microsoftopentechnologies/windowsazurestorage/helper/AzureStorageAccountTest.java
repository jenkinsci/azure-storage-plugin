/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.microsoftopentechnologies.windowsazurestorage.helper;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author arroyc
 */
@WithJenkins
class AzureStorageAccountTest {
    private static final String stName = "test1";
    private static final String stKey = "123";
    private static final String stBlobURL = "http://test1";
    private static final String stCdnURL = "http://cdn-test1";

    private AzureStorageAccount azureCred;
    private AzureStorageAccount.StorageAccountCredential stCred;

    private static JenkinsRule j;

    @BeforeAll
    static void setUp(JenkinsRule rule) {
        j = rule;
    }

    @BeforeEach
    void setUp() throws IOException {
        azureCred = new AzureStorageAccount(CredentialsScope.GLOBAL, UUID.randomUUID().toString(), null, stName, stKey, stBlobURL, stCdnURL);
        stCred = new AzureStorageAccount.StorageAccountCredential(stName, stKey, stBlobURL, stCdnURL);
        CredentialsStore s = CredentialsProvider.lookupStores(Jenkins.get()).iterator().next();
        s.addCredentials(Domain.global(), azureCred);
    }

    /**
     * Test of getStorageAccountCredential method, of class AzureStorageAccount.
     */
    @Test
    void testGetStorageAccountCredential() {
        System.out.println("getStorageAccountCredential");
        assertNull(AzureStorageAccount.getStorageAccountCredential(null, null));
        String storageCredentialId = azureCred.getId();

        AzureStorageAccount.StorageAccountCredential result = AzureStorageAccount.getStorageAccountCredential(null, storageCredentialId);
        assertNotNull(result);
        assertEquals(stCred.getStorageAccountKey(), result.getStorageAccountKey());
        assertEquals(stCred.getSecureKey(), result.getSecureKey());
        assertEquals(stCred.getStorageAccountName(), result.getStorageAccountName());
        assertEquals(stCred.getBlobEndpointURL(), result.getBlobEndpointURL());
        assertEquals(stCred.getCdnEndpointURL(), result.getCdnEndpointURL());
    }

    /**
     * Test of getStorageCreds method, of class AzureStorageAccount.
     */
    @Test
    void testGetStorageCreds() {
        System.out.println("getStorageCreds");
        AzureStorageAccount.StorageAccountCredential result = AzureStorageAccount.getStorageCreds(azureCred.getId(), stName);
        assertEquals(stCred.getStorageAccountKey(), result.getStorageAccountKey());
        assertEquals(stCred.getStorageAccountName(), result.getStorageAccountName());
        assertEquals(stCred.getBlobEndpointURL(), result.getBlobEndpointURL());
        assertEquals(stCred.getCdnEndpointURL(), result.getCdnEndpointURL());

        result = AzureStorageAccount.getStorageCreds(null, stName);
        assertEquals(stCred.getStorageAccountKey(), result.getStorageAccountKey());
        assertEquals(stCred.getStorageAccountName(), result.getStorageAccountName());
        assertEquals(stCred.getBlobEndpointURL(), result.getBlobEndpointURL());
        assertEquals(stCred.getCdnEndpointURL(), result.getCdnEndpointURL());
    }

    /**
     * Test of getStorageAccountName method, of class AzureStorageAccount.
     */
    @Test
    void testGetStorageAccountName() {
        System.out.println("getStorageAccountName");
        assertEquals(stName, azureCred.getStorageAccountName());
    }

    /**
     * Test of getStorageKey method, of class AzureStorageAccount.
     */
    @Test
    void testGetStorageKey() {
        System.out.println("getStorageKey");
        assertEquals(stCred.getSecureKey().getPlainText(), Secret.decrypt(azureCred.getStorageKey()).getPlainText());
    }

    /**
     * Test of getBlobEndpointURL method, of class AzureStorageAccount.
     */
    @Test
    void testGetBlobEndpointURL() {
        System.out.println("getBlobEndpointURL");
        assertEquals(stBlobURL, azureCred.getBlobEndpointURL());
    }

    /**
     * Test of getCdnEndpointURL method, of class AzureStorageAccount.
     */
    @Test
    void testGetCdnEndpointURL() {
        System.out.println("getCdnEndpointURL");
        assertEquals(stCdnURL, azureCred.getCdnEndpointURL());
    }

    /**
     * Test of getStorageCred method, of class AzureStorageAccount.
     */
    @Test
    void testGetStorageCred() {
        System.out.println("getStorageCred");
        assertEquals(stCred.getStorageAccountKey(), azureCred.getStorageCred().getStorageAccountKey());
        assertEquals(stCred.getSecureKey(), azureCred.getStorageCred().getSecureKey());
        assertEquals(stCred.getStorageAccountName(), azureCred.getStorageCred().getStorageAccountName());
        assertEquals(stCred.getBlobEndpointURL(), azureCred.getStorageCred().getBlobEndpointURL());
        assertEquals(stCred.getCdnEndpointURL(), azureCred.getStorageCred().getCdnEndpointURL());
    }

    /**
     * Test of convertToStorageAccountInfo method, of class AzureStorageAccount.
     */
    @Test
    void testConvertToStorageAccountInfo() {
        System.out.println("convertToStorageAccountInfo");
        StorageAccountInfo expResult = new StorageAccountInfo(stName, stKey, stBlobURL, stCdnURL);
        StorageAccountInfo result = AzureStorageAccount.convertToStorageAccountInfo(azureCred.getStorageCred());
        assertEquals(expResult.getStorageAccName(), result.getStorageAccName());
        assertEquals(expResult.getBlobEndPointURL(), result.getBlobEndPointURL());
        assertEquals(expResult.getCdnEndPointURL(), result.getCdnEndPointURL());
        assertEquals(expResult.getStorageAccountKey(), result.getStorageAccountKey());
    }
}
