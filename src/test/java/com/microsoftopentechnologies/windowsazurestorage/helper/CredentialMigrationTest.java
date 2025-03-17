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
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author arroyc
 */
@WithJenkins
class CredentialMigrationTest {

    private static final String correctConfigContent = """
            <?xml version='1.0' encoding='UTF-8'?>
            <com.microsoftopentechnologies.windowsazurestorage.AzureStoragePublisher_-WAStorageDescriptor plugin='windows-azure-storage@0.3.3-SNAPSHOT'>
            <storageAccounts>
            <com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo>
            <storageAccName>abcdef</storageAccName>
            <storageAccountKey>12345</storageAccountKey>
            <blobEndPointURL>http://test1/</blobEndPointURL>
            </com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo>
            <com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo>
            <storageAccName>12345</storageAccName>
            <storageAccountKey>abcdef</storageAccountKey>
            <blobEndPointURL>http://test2/</blobEndPointURL>
            </com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo>
            </storageAccounts>
            </com.microsoftopentechnologies.windowsazurestorage.AzureStoragePublisher_-WAStorageDescriptor>""";

    private static final String wrongConfigContent = """
            <?xml version='1.0' encoding='UTF-8'?>
            <com.microsoftopentechnologies.windowsazurestorage.AzureStoragePublisher_-WAStorageDescriptor plugin='windows-azure-storage@0.3.3-SNAPSHOT'>
            <storageAccounts>
            <storageAccName>abcdef</storageAccName>
            <storageAccountKey>12345</storageAccountKey>
            <blobEndPointURL>https://blob.core.windows.net/</blobEndPointURL>
            </storageAccounts>
            </com.microsoftopentechnologies.windowsazurestorage.AzureStoragePublisher_-WAStorageDescriptor>""";

    @TempDir
    private File testFolder;
    private static JenkinsRule j;

    private Jenkins jenkinsInstance;

    @BeforeAll
    static void setUp(JenkinsRule rule) {
        j = rule;
    }

    @BeforeEach
    void setUp() {
        jenkinsInstance = Jenkins.get();
    }

    /**
     * Test of upgradeStorageConfig method, of class CredentialMigration.
     */
    @Test
    void testUpgradeStorageConfig() throws Exception {
        System.out.println("upgradeStorageConfig");

        String storageAccount = "abcdef";
        String storageAccountKey = "12345";
        String storageBlobURL = "http://test1/";
        File configFile = new File(testFolder, Constants.LEGACY_STORAGE_CONFIG_FILE);
        FileUtils.writeStringToFile(configFile, correctConfigContent, StandardCharsets.UTF_8);
        FileUtils.copyFileToDirectory(configFile, jenkinsInstance.root);

        CredentialMigration.upgradeStorageConfig();

        CredentialsStore s = CredentialsProvider.lookupStores(jenkinsInstance).iterator().next();

        assertEquals(2, s.getCredentials(Domain.global()).size());

        AzureStorageAccount.StorageAccountCredential u = new AzureStorageAccount.StorageAccountCredential(storageAccount, storageAccountKey, storageBlobURL, "");
        AzureStorageAccount storageCred = CredentialsMatchers.firstOrNull(CredentialsProvider.lookupCredentialsInItemGroup(AzureStorageAccount.class, jenkinsInstance, ACL.SYSTEM2, Collections.emptyList()),
                CredentialsMatchers.withId(u.getId()));

        assertEquals(u.getStorageAccountName(), storageCred.getStorageAccountName());
        assertEquals(u.getBlobEndpointURL(), storageCred.getBlobEndpointURL());
        assertEquals(u.getSecureKey().getPlainText(), storageCred.getPlainStorageKey());
    }

    /**
     * Test of getOldStorageConfig method, of class CredentialMigration.
     */
    @Test
    void testGetOldStorageConfig() throws Exception {
        System.out.println("getOldStorageConfig");

        List<StorageAccountInfo> expResult = new ArrayList<>();
        expResult.add(new StorageAccountInfo("abcdef", "12345", "http://test1/", ""));
        expResult.add(new StorageAccountInfo("12345", "abcdef", "http://test2/", ""));

        File configFile = new File(testFolder, "test.xml");
        FileUtils.writeStringToFile(configFile, correctConfigContent, StandardCharsets.UTF_8);
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

        configFile = new File(testFolder, "wrong.xml");
        FileUtils.writeStringToFile(configFile, wrongConfigContent, StandardCharsets.UTF_8);
        result = CredentialMigration.getOldStorageConfig(configFile);

        assertEquals(0, result.size());
    }
}
