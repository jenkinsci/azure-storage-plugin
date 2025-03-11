package com.microsoftopentechnologies.windowsazurestorage.helper;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.security.ACL;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@WithJenkins
class CredentialRenameTest {

    @Test
    @LocalData
    void testRenameStorageConfig(JenkinsRule j) {
        String storageAccount = "name";
        String storageAccountKey = "key";
        String storageBlobURL = "https://blob.core.windows.net/";
        String storageCdnURL = "https://cdn-resource-name.azureedge.net/";

        CredentialsStore s = CredentialsProvider.lookupStores(j.jenkins).iterator().next();

        assertEquals(1, s.getCredentials(Domain.global()).size());

        AzureStorageAccount.StorageAccountCredential expected = new AzureStorageAccount.StorageAccountCredential(
                storageAccount, storageAccountKey, storageBlobURL, storageCdnURL);

        List<AzureStorageAccount> azureStorageAccounts = CredentialsProvider.lookupCredentialsInItem(
                AzureStorageAccount.class,
                null,
                ACL.SYSTEM2,
                Collections.emptyList());
        assertNotNull(azureStorageAccounts);
        assertEquals(1, azureStorageAccounts.size());
        AzureStorageAccount storageCred = azureStorageAccounts.get(0);

        assertEquals(expected.getStorageAccountName(), storageCred.getStorageAccountName());
        assertEquals(expected.getBlobEndpointURL(), storageCred.getBlobEndpointURL());
        assertEquals(expected.getCdnEndpointURL(), storageCred.getCdnEndpointURL());
        assertEquals(expected.getSecureKey().getPlainText(), storageCred.getPlainStorageKey());
    }
}
