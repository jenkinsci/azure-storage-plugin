package com.microsoftopentechnologies.windowsazurestorage.helper;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.model.Item;
import hudson.security.ACL;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CredentialRenameTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @LocalData
    public void testRenameStorageConfig() throws Exception {
        String storageAccount = "name";
        String storageAccountKey = "key";
        String storageBlobURL = "https://blob.core.windows.net/";

        CredentialsStore s = CredentialsProvider.lookupStores(j.jenkins).iterator().next();

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
