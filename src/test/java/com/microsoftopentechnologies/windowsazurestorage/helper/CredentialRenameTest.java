package com.microsoftopentechnologies.windowsazurestorage.helper;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.model.Item;
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
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CredentialRenameTest {
    public static File input = new File("src/test/resources/com/microsoftopentechnologies/helper/correctFormatOldConfig.xml");
    private Jenkins jenkinsInstance;
    private static final String correctConfigContent =
            "<?xml version='1.0' encoding='UTF-8'?>\n" +
                    "<com.cloudbees.plugins.credentials.SystemCredentialsProvider plugin=\"credentials@2.1.18\">\n" +
                    "  <domainCredentialsMap class=\"hudson.util.CopyOnWriteMap$Hash\">\n" +
                    "    <entry>\n" +
                    "      <com.cloudbees.plugins.credentials.domains.Domain>\n" +
                    "        <specifications/>\n" +
                    "      </com.cloudbees.plugins.credentials.domains.Domain>\n" +
                    "      <java.util.concurrent.CopyOnWriteArrayList>\n" +
                    "        <com.microsoftopentechnologies.windowsazurestorage.helper.AzureCredentials plugin=\"windows-azure-storage@0.3.13-SNAPSHOT\">\n" +
                    "          <scope>GLOBAL</scope>\n" +
                    "          <id>jieshestorage</id>\n" +
                    "          <description></description>\n" +
                    "          <storageData>\n" +
                    "            <storageAccountName>name</storageAccountName>\n" +
                    "            <storageAccountKey>key</storageAccountKey>\n" +
                    "            <blobEndpointURL>https://blob.core.windows.net/</blobEndpointURL>\n" +
                    "          </storageData>\n" +
                    "        </com.microsoftopentechnologies.windowsazurestorage.helper.AzureCredentials>\n" +
                    "      </java.util.concurrent.CopyOnWriteArrayList>\n" +
                    "    </entry>\n" +
                    "  </domainCredentialsMap>\n" +
                    "</com.cloudbees.plugins.credentials.SystemCredentialsProvider>";

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

    @Test
    public void testRenameStorageConfig() throws Exception {
        String storageAccount = "name";
        String storageAccountKey = "key";
        String storageBlobURL = "https://blob.core.windows.net/";
        File configFile = testFolder.newFile("credentials.xml");
        FileUtils.writeStringToFile(configFile, correctConfigContent);
        FileUtils.copyFileToDirectory(configFile, jenkinsInstance.root);

        CredentialRename.renameStorageConfig();

        CredentialsStore s = CredentialsProvider.lookupStores(jenkinsInstance).iterator().next();

        assertEquals(1, s.getCredentials(Domain.global()).size());

        AzureStorageAccount.StorageAccountCredential u = new AzureStorageAccount.StorageAccountCredential(storageAccount, storageAccountKey, storageBlobURL);

        List<AzureStorageAccount> azureStorageAccounts = CredentialsProvider.lookupCredentials(
                AzureStorageAccount.class,
                (Item) null,
                ACL.SYSTEM,
                Collections.emptyList());
        assertNotNull(azureStorageAccounts);
        assertEquals(1, azureStorageAccounts.size());
        AzureStorageAccount storageCred = azureStorageAccounts.get(0);

        assertEquals(u.getStorageAccountName(), storageCred.getStorageAccountName());
        assertEquals(u.getEndpointURL(), storageCred.getBlobEndpointURL());
        assertEquals(u.getSecureKey().getPlainText(), storageCred.getPlainStorageKey());

    }
}
