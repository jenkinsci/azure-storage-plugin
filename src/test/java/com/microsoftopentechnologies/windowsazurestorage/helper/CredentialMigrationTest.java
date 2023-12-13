/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.microsoftopentechnologies.windowsazurestorage.helper;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;


/**
 *
 * @author arroyc
 */
public class CredentialMigrationTest {

    public static File input = new File("src/test/resources/com/microsoftopentechnologies/helper/correctFormatOldConfig.xml");
    private Jenkins jenkinsInstance;
    private static final String correctConfigContent = "<?xml version='1.0' encoding='UTF-8'?>\n" +
    "<com.microsoftopentechnologies.windowsazurestorage.AzureStoragePublisher_-WAStorageDescriptor plugin='windows-azure-storage@0.3.3-SNAPSHOT'>\n" +
    "<storageAccounts>\n" +
    "<com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo>\n" +
    "<storageAccName>abcdef</storageAccName>\n" +
    "<storageAccountKey>12345</storageAccountKey>\n" +
    "<blobEndPointURL>http://test1/</blobEndPointURL>\n" +
    "</com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo>\n" +
    "<com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo>\n" +
    "<storageAccName>12345</storageAccName>\n" +
    "<storageAccountKey>abcdef</storageAccountKey>\n" +
    "<blobEndPointURL>http://test2/</blobEndPointURL>\n" +
    "</com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo>\n" +
    "</storageAccounts>\n" +
    "</com.microsoftopentechnologies.windowsazurestorage.AzureStoragePublisher_-WAStorageDescriptor>";
    
    private static final String wrongConfigContent = "<?xml version='1.0' encoding='UTF-8'?>\n" +
    "<com.microsoftopentechnologies.windowsazurestorage.AzureStoragePublisher_-WAStorageDescriptor plugin='windows-azure-storage@0.3.3-SNAPSHOT'>\n" +
    "<storageAccounts>\n" +
    "<storageAccName>abcdef</storageAccName>\n" +
    "<storageAccountKey>12345</storageAccountKey>\n" +
    "<blobEndPointURL>https://blob.core.windows.net/</blobEndPointURL>\n" +
    "</storageAccounts>\n" +
    "</com.microsoftopentechnologies.windowsazurestorage.AzureStoragePublisher_-WAStorageDescriptor>";
    
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();
    @ClassRule 
    public static JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() {
        jenkinsInstance = Jenkins.getInstance();
        
    }
    /**
     * Test of upgradeStorageConfig method, of class CredentialMigration.
     */
    @Test
    public void testUpgradeStorageConfig() throws Exception {
        System.out.println("upgradeStorageConfig");
        
        String storageAccount = "abcdef";
        String storageAccountKey = "12345";
        String storageBlobURL = "http://test1/";
        File configFile = testFolder.newFile(Constants.LEGACY_STORAGE_CONFIG_FILE);
        FileUtils.writeStringToFile(configFile, correctConfigContent);
        FileUtils.copyFileToDirectory(configFile, jenkinsInstance.root);

        CredentialMigration.upgradeStorageConfig();
        
        CredentialsStore s = CredentialsProvider.lookupStores(jenkinsInstance).iterator().next();

        assertEquals(2, s.getCredentials(Domain.global()).size());
                
        AzureStorageAccount.StorageAccountCredential u = new AzureStorageAccount.StorageAccountCredential(storageAccount, storageAccountKey, storageBlobURL, "");
        AzureStorageAccount storageCred = CredentialsMatchers.firstOrNull(CredentialsProvider.lookupCredentials(AzureStorageAccount.class, jenkinsInstance, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.withId(u.getId()));

        assertEquals(u.getStorageAccountName(), storageCred.getStorageAccountName());
        assertEquals(u.getBlobEndpointURL(), storageCred.getBlobEndpointURL());
        assertEquals(u.getSecureKey().getPlainText(), storageCred.getPlainStorageKey());
        
    }

    /**
     * Test of getOldStorageConfig method, of class CredentialMigration.
     */
    @Test
    public void testGetOldStorageConfig() throws Exception {
        System.out.println("getOldStorageConfig");

        List<StorageAccountInfo> expResult = new ArrayList<StorageAccountInfo>();
        expResult.add(new StorageAccountInfo("abcdef", "12345", "http://test1/", ""));
        expResult.add(new StorageAccountInfo("12345", "abcdef", "http://test2/", ""));
        
        File configFile = testFolder.newFile("test.xml");
        FileUtils.writeStringToFile(configFile, correctConfigContent);
        List<StorageAccountInfo> result = CredentialMigration.getOldStorageConfig(configFile);
        
        assertEquals(expResult.size(), result.size());
        assertEquals(expResult.get(0).getStorageAccName(), result.get(0).getStorageAccName());
        assertEquals(expResult.get(0).getBlobEndPointURL(), result.get(0).getBlobEndPointURL());
        assertEquals(expResult.get(0).getStorageAccountKey(), result.get(0).getStorageAccountKey());
        
        assertEquals(expResult.get(1).getStorageAccName(), result.get(1).getStorageAccName());
        assertEquals(expResult.get(1).getBlobEndPointURL(), result.get(1).getBlobEndPointURL());
        assertEquals(expResult.get(1).getStorageAccountKey(), result.get(1).getStorageAccountKey());
        
        expResult.clear();
        result.clear();
        
        configFile = testFolder.newFile("wrong.xml");
        FileUtils.writeStringToFile(configFile, wrongConfigContent);
        result = CredentialMigration.getOldStorageConfig(configFile);
        
        assertEquals(0, result.size());
    }

}
