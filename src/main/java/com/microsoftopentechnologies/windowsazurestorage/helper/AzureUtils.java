/*
 Copyright 2014 Microsoft Open Technologies, Inc.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package com.microsoftopentechnologies.windowsazurestorage.helper;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.OperationContext;
import com.microsoft.azure.storage.RetryNoRetry;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobContainerPermissions;
import com.microsoft.azure.storage.blob.BlobContainerPublicAccessType;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.SharedAccessBlobPermissions;
import com.microsoft.azure.storage.blob.SharedAccessBlobPolicy;
import com.microsoft.azure.storage.file.CloudFile;
import com.microsoft.azure.storage.file.CloudFileClient;
import com.microsoft.azure.storage.file.CloudFileShare;
import com.microsoft.azure.storage.file.SharedAccessFilePermissions;
import com.microsoft.azure.storage.file.SharedAccessFilePolicy;
import com.microsoftopentechnologies.windowsazurestorage.Messages;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public final class AzureUtils {
    private static final String TEST_CNT_NAME = "testcheckfromjenkins";
    private static final String BLOB = "blob";
    private static final String QUEUE = "queue";
    private static final String TABLE = "table";

    /**
     * This method validates Storage Account credentials by checking for a dummy
     * container existence.
     *
     * @param storageAccount
     * @return true if valid
     * @throws WAStorageException
     */
    public static boolean validateStorageAccount(
            final StorageAccountInfo storageAccount) throws WAStorageException {
        try {
            // Get container reference
            final CloudBlobContainer container = getBlobContainerReference(
                    storageAccount, TEST_CNT_NAME, false, false, null);
            container.exists();

        } catch (Exception e) {
            throw new WAStorageException(Messages.Client_SA_val_fail());
        }
        return true;
    }

    public static CloudStorageAccount getCloudStorageAccount(
            final StorageAccountInfo storageAccount) throws URISyntaxException, MalformedURLException {
        CloudStorageAccount cloudStorageAccount;
        final String accName = storageAccount.getStorageAccName();
        final String blobURLStr = storageAccount.getBlobEndPointURL();
        final StorageCredentialsAccountAndKey credentials = new StorageCredentialsAccountAndKey(accName,
                storageAccount.getStorageAccountKey());

        if (StringUtils.isBlank(blobURLStr) || blobURLStr.equalsIgnoreCase(Constants.DEF_BLOB_URL)) {
            cloudStorageAccount = new CloudStorageAccount(credentials);
        } else {
            final URL blobURL = new URL(blobURLStr);
            boolean useHttps = blobURL.getProtocol().equalsIgnoreCase("https");

            cloudStorageAccount = new CloudStorageAccount(credentials, useHttps, getEndpointSuffix(blobURLStr));
        }

        return cloudStorageAccount;
    }

    public static CloudBlobContainer getBlobContainerReference(StorageAccountInfo storageAccount,
                                                               String containerName,
                                                               boolean createIfNotExist,
                                                               boolean allowRetry,
                                                               Boolean cntPubAccess)
            throws URISyntaxException, StorageException, IOException {

        final CloudStorageAccount cloudStorageAccount = getCloudStorageAccount(storageAccount);
        final CloudBlobClient serviceClient = cloudStorageAccount.createCloudBlobClient();

        if (!allowRetry) {
            // Setting no retry policy
            final RetryNoRetry rnr = new RetryNoRetry();
            // serviceClient.setRetryPolicyFactory(rnr);
            serviceClient.getDefaultRequestOptions().setRetryPolicyFactory(rnr);
        }

        final CloudBlobContainer container = serviceClient.getContainerReference(containerName);

        boolean cntExists = container.exists();
        if (createIfNotExist && !cntExists) {
            container.createIfNotExists(null, Utils.updateUserAgent());
        }

        // Apply permissions only if container is created newly
        setContainerPermission(container, cntExists, cntPubAccess);

        return container;
    }

    /**
     * Generates SAS URL for blob in Azure storage account.
     *
     * @param storageAccount
     * @param blobName
     * @param containerName  container name
     * @return SAS URL
     * @throws Exception
     */
    @Deprecated
    public static String generateBlobSASURL(
            StorageAccountInfo storageAccount,
            String containerName,
            String blobName) throws Exception {
        return generateBlobSASURL(storageAccount, containerName, blobName,
                EnumSet.of(SharedAccessBlobPermissions.READ));
    }

    @Deprecated
    public static SharedAccessBlobPolicy generateBlobPolicy() {
        SharedAccessBlobPolicy policy = new SharedAccessBlobPolicy();
        policy.setSharedAccessExpiryTime(generateExpiryDate());
        policy.setPermissions(EnumSet.of(SharedAccessBlobPermissions.READ));

        return policy;
    }

    public static String generateBlobSASURL(
            StorageAccountInfo storageAccount,
            String containerName,
            String blobName,
            EnumSet<SharedAccessBlobPermissions> permissions) throws Exception {

        CloudStorageAccount cloudStorageAccount = getCloudStorageAccount(storageAccount);

        // Create the blob client.
        CloudBlobClient blobClient = cloudStorageAccount.createCloudBlobClient();
        CloudBlobContainer container = blobClient.getContainerReference(containerName);

        // At this point need to throw an error back since container itself did not exist.
        if (!container.exists()) {
            throw new Exception("WAStorageClient: generateBlobSASURL: Container " + containerName
                    + " does not exist in storage account " + storageAccount.getStorageAccName());
        }

        CloudBlob blob = container.getBlockBlobReference(blobName);
        String sas = blob.generateSharedAccessSignature(generateBlobPolicy(permissions), null);

        return sas;
    }

    public static SharedAccessBlobPolicy generateBlobPolicy(EnumSet<SharedAccessBlobPermissions> permissions) {
        SharedAccessBlobPolicy policy = new SharedAccessBlobPolicy();
        policy.setSharedAccessExpiryTime(generateExpiryDate());
        policy.setPermissions(permissions);
        return policy;
    }

    /**
     * Generates SAS URL for file item in Azure storage File Share.
     *
     * @param storageAccount
     * @param fileName
     * @param shareName      container name
     * @return SAS URL
     * @throws Exception
     */
    @Deprecated
    public static String generateFileSASURL(
            StorageAccountInfo storageAccount,
            String shareName,
            String fileName) throws Exception {
        return generateFileSASURL(storageAccount, shareName, fileName,
                EnumSet.of(SharedAccessFilePermissions.READ));
    }

    @Deprecated
    public static SharedAccessFilePolicy generateFilePolicy() {
        SharedAccessFilePolicy policy = new SharedAccessFilePolicy();
        policy.setSharedAccessExpiryTime(generateExpiryDate());
        policy.setPermissions(EnumSet.of(SharedAccessFilePermissions.READ));

        return policy;
    }

    /**
     * Generates SAS URL for file item in Azure storage File Share.
     *
     * @param storageAccount
     * @param fileName
     * @param shareName      container name
     * @return SAS URL
     * @throws Exception
     */
    public static String generateFileSASURL(
            StorageAccountInfo storageAccount,
            String shareName,
            String fileName,
            EnumSet<SharedAccessFilePermissions> permissions) throws Exception {
        CloudStorageAccount cloudStorageAccount = getCloudStorageAccount(storageAccount);

        CloudFileClient fileClient = cloudStorageAccount.createCloudFileClient();
        CloudFileShare fileShare = fileClient.getShareReference((shareName));
        if (!fileShare.exists()) {
            throw new Exception("WAStorageClient: generateFileSASURL: Share " + shareName
                    + " does not exist in storage account " + storageAccount.getStorageAccName());
        }

        CloudFile cloudFile = fileShare.getRootDirectoryReference().getFileReference(fileName);
        return cloudFile.generateSharedAccessSignature(generateFilePolicy(permissions), null);
    }

    public static SharedAccessFilePolicy generateFilePolicy(EnumSet<SharedAccessFilePermissions> permissions) {
        SharedAccessFilePolicy policy = new SharedAccessFilePolicy();
        policy.setSharedAccessExpiryTime(generateExpiryDate());
        policy.setPermissions(permissions);
        return policy;
    }

    /**
     * Set default proxy for Azure Storage SDK if Jenkins has proxy setting.
     */
    public static void updateDefaultProxy() {
        Jenkins jenkinsInstance = Utils.getJenkinsInstance();
        ProxyConfiguration proxyConfig = jenkinsInstance.proxy;
        if (proxyConfig != null) {
            Proxy proxy = proxyConfig.createProxy(null);
            OperationContext.setDefaultProxy(proxy);
        }
    }

    private static Date generateExpiryDate() {
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.setTime(new Date());
        calendar.add(Calendar.HOUR, 1);
        return calendar.getTime();
    }

    private static void setContainerPermission(
            CloudBlobContainer container,
            boolean cntExists,
            Boolean cntPubAccess) throws StorageException {
        if (!cntExists && cntPubAccess != null) {
            // Set access permissions on container.
            final BlobContainerPermissions cntPerm = new BlobContainerPermissions();
            if (cntPubAccess) {
                cntPerm.setPublicAccess(BlobContainerPublicAccessType.CONTAINER);
            } else {
                cntPerm.setPublicAccess(BlobContainerPublicAccessType.OFF);
            }
            container.uploadPermissions(cntPerm);
        }
    }

    /**
     * Returns suffix for blob endpoint.
     *
     * @param blobURL endpoint
     * @return the endpoint suffix
     */
    private static String getEndpointSuffix(String blobURL) throws URISyntaxException {
        final int endSuffixStartIndex = blobURL.toLowerCase().indexOf(Utils.BLOB_ENDPOINT_ENDSUFFIX_KEYWORD);
        if (endSuffixStartIndex < 0) {
            throw new URISyntaxException(blobURL, "The blob endpoint is not correct!");
        }

        return blobURL.substring(endSuffixStartIndex);
    }


    private AzureUtils() {
        // hide constructor
    }
}
