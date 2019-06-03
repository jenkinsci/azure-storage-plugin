package com.microsoftopentechnologies.windowsazurestorage.helper;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageUri;
import com.microsoft.azure.storage.core.Base64;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import org.junit.Assert;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

public class AzureUtilsTest {

    private void assertCloudStorageAccountWithDefaultEndpoint(final CloudStorageAccount account,
                                                              final boolean useHttps) throws URISyntaxException {
        final String protocol = useHttps ? "https" : "http";
        Assert.assertEquals("foo", account.getCredentials().getAccountName());
        Assert.assertEquals(new URI(protocol + "://foo.blob.core.windows.net"), account.getBlobEndpoint());
        Assert.assertEquals(new URI(protocol + "://foo.file.core.windows.net"), account.getFileEndpoint());
        Assert.assertEquals(
                new StorageUri(
                        new URI(protocol + "://foo.blob.core.windows.net"),
                        new URI(protocol + "://foo-secondary.blob.core.windows.net")),
                account.getBlobStorageUri());
        Assert.assertEquals(
                new StorageUri(
                        new URI(protocol + "://foo.file.core.windows.net"),
                        new URI(protocol + "://foo-secondary.file.core.windows.net")),
                account.getFileStorageUri());
    }

    @Test
    public void testGetCloudStorageAccount_EmptyEndPoint() throws MalformedURLException, URISyntaxException {
        final StorageAccountInfo info = new StorageAccountInfo(
                "foo", Base64.encode(new byte[]{'k'}), "");
        final CloudStorageAccount account = AzureUtils.getCloudStorageAccount(info);
        Assert.assertNull(account.getEndpointSuffix());
        assertCloudStorageAccountWithDefaultEndpoint(account, true);
    }

    @Test
    public void testGetCloudStorageAccount_DefaultEndPoint() throws MalformedURLException, URISyntaxException {
        final StorageAccountInfo info = new StorageAccountInfo(
                "foo", Base64.encode(new byte[]{'k'}), "http://blob.core.windows.net");
        final CloudStorageAccount account = AzureUtils.getCloudStorageAccount(info);
        Assert.assertEquals("core.windows.net", account.getEndpointSuffix());
        assertCloudStorageAccountWithDefaultEndpoint(account, false);
    }

    @Test
    public void testGetCloudStorageAccount_DefaultEndPointHttps() throws MalformedURLException, URISyntaxException {
        final StorageAccountInfo info = new StorageAccountInfo(
                "foo", Base64.encode(new byte[]{'k'}), "https://blob.core.windows.net");
        final CloudStorageAccount account = AzureUtils.getCloudStorageAccount(info);
        Assert.assertEquals("core.windows.net", account.getEndpointSuffix());
        assertCloudStorageAccountWithDefaultEndpoint(account, true);
    }

    @Test
    public void testGetCloudStorageAccount_CustomEndPoint() throws MalformedURLException, URISyntaxException {
        final StorageAccountInfo info = new StorageAccountInfo(
                "foo", Base64.encode(new byte[]{'k'}), "http://blob.core.chinacloudapi.cn");
        final CloudStorageAccount account = AzureUtils.getCloudStorageAccount(info);
        Assert.assertEquals("foo", account.getCredentials().getAccountName());
        Assert.assertEquals("core.chinacloudapi.cn", account.getEndpointSuffix());
        Assert.assertEquals(new URI("http://foo.blob.core.chinacloudapi.cn"), account.getBlobEndpoint());
        Assert.assertEquals(new URI("http://foo.file.core.chinacloudapi.cn"), account.getFileEndpoint());
        Assert.assertEquals(
                new StorageUri(
                        new URI("http://foo.file.core.chinacloudapi.cn"),
                        new URI("http://foo-secondary.file.core.chinacloudapi.cn")),
                account.getFileStorageUri());
    }

}
