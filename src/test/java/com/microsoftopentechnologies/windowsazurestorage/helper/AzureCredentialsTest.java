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
import java.io.IOException;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.jvnet.hudson.test.JenkinsRule;

/**
 *
 * @author arroyc
 */
public class AzureCredentialsTest {
    private String stName = "test1";
    private String stKey = "123";
    private String stURL = "http://test1";
    
    private AzureCredentials azureCred;
    private AzureCredentials.StorageAccountCredential stCred;
    private Jenkins jenkinsInstance;
    
    @ClassRule 
    public static JenkinsRule j = new JenkinsRule();
    
    @Before
    public void setUp() throws IOException {
        jenkinsInstance = Jenkins.getInstance();
        azureCred = new AzureCredentials(CredentialsScope.GLOBAL, Utils.getMD5(stName.concat(stKey)), null, stName, stKey, stURL);
        stCred = new AzureCredentials.StorageAccountCredential(stName, stKey, stURL);
        CredentialsStore s = CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
        s.addCredentials(Domain.global(), azureCred);
    }

    /**
     * Test of getStorageAccountCredential method, of class AzureCredentials.
     */
    @Test
    public void testGetStorageAccountCredential() {
        System.out.println("getStorageAccountCredential");
        assertEquals(null, AzureCredentials.getStorageAccountCredential(null));
        String storageCredentialId = azureCred.getId();

        AzureCredentials.StorageAccountCredential result = AzureCredentials.getStorageAccountCredential(storageCredentialId);
        assertEquals(stCred.getStorageAccountKey(), result.getStorageAccountKey());
        assertEquals(stCred.getSecureKey(), result.getSecureKey());
        assertEquals(stCred.getStorageAccountName(), result.getStorageAccountName());
        assertEquals(stCred.getEndpointURL(), result.getEndpointURL());
    }

    /**
     * Test of getStorageCreds method, of class AzureCredentials.
     */
    @Test
    public void testGetStorageCreds() {
        System.out.println("getStorageCreds");
        AzureCredentials.StorageAccountCredential result = AzureCredentials.getStorageCreds(azureCred.getId(), stName);
        assertEquals(stCred.getStorageAccountKey(), result.getStorageAccountKey());
        assertEquals(stCred.getStorageAccountName(), result.getStorageAccountName());
        assertEquals(stCred.getEndpointURL(), result.getEndpointURL());
        
        result = AzureCredentials.getStorageCreds(null, stName);
        assertEquals(stCred.getStorageAccountKey(), result.getStorageAccountKey());
        assertEquals(stCred.getStorageAccountName(), result.getStorageAccountName());
        assertEquals(stCred.getEndpointURL(), result.getEndpointURL());
    }

    /**
     * Test of getStorageAccountName method, of class AzureCredentials.
     */
    @Test
    public void testGetStorageAccountName() {
        System.out.println("getStorageAccountName");
        assertEquals(stName, azureCred.getStorageAccountName());
    }

    /**
     * Test of getStorageKey method, of class AzureCredentials.
     */
    @Test
    public void testGetStorageKey() {
        System.out.println("getStorageKey");
        assertEquals(stCred.getSecureKey().getEncryptedValue(), azureCred.getStorageKey());
    }

    /**
     * Test of getBlobEndpointURL method, of class AzureCredentials.
     */
    @Test
    public void testGetBlobEndpointURL() {
        System.out.println("getBlobEndpointURL");
        assertEquals(stURL, azureCred.getBlobEndpointURL());        
    }

    /**
     * Test of getStorageCred method, of class AzureCredentials.
     */
    @Test
    public void testGetStorageCred() {
        System.out.println("getStorageCred");
        assertEquals(stCred.getStorageAccountKey(), azureCred.getStorageCred().getStorageAccountKey());
        assertEquals(stCred.getSecureKey(), azureCred.getStorageCred().getSecureKey());
        assertEquals(stCred.getStorageAccountName(), azureCred.getStorageCred().getStorageAccountName());
        assertEquals(stCred.getEndpointURL(), azureCred.getStorageCred().getEndpointURL());
    }

    /**
     * Test of convertToStorageAccountInfo method, of class AzureCredentials.
     */
    @Test
    public void testConvertToStorageAccountInfo() {
        System.out.println("convertToStorageAccountInfo");
        StorageAccountInfo expResult = new StorageAccountInfo(stName, stKey, stURL);
        StorageAccountInfo result = AzureCredentials.convertToStorageAccountInfo(azureCred.getStorageCred());
        assertEquals(expResult.getStorageAccName(), result.getStorageAccName());
        assertEquals(expResult.getBlobEndPointURL(), result.getBlobEndPointURL());
        assertEquals(expResult.getStorageAccountKey(), result.getStorageAccountKey());
    }
    
}
