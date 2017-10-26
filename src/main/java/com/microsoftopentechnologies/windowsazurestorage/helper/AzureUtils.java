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
import com.microsoftopentechnologies.windowsazurestorage.Messages;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
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
    public static String generateSASURL(
            StorageAccountInfo storageAccount,
            String containerName,
            String blobName) throws Exception {
        String storageAccountName = storageAccount.getStorageAccName();
        StorageCredentialsAccountAndKey credentials =
                new StorageCredentialsAccountAndKey(storageAccountName, storageAccount.getStorageAccountKey());
        URL blobURL = new URL(storageAccount.getBlobEndPointURL());
        String saBlobURI = new StringBuilder()
                .append(blobURL.getProtocol()).append("://").append(storageAccountName).append(".")
                .append(blobURL.getHost()).append("/")
                .toString();
        CloudStorageAccount cloudStorageAccount = new CloudStorageAccount(credentials, new URI(saBlobURI),
                new URI(getCustomURI(storageAccountName, QUEUE, saBlobURI)),
                new URI(getCustomURI(storageAccountName, TABLE, saBlobURI)));
        // Create the blob client.
        CloudBlobClient blobClient = cloudStorageAccount.createCloudBlobClient();
        CloudBlobContainer container = blobClient.getContainerReference(containerName);

        // At this point need to throw an error back since container itself did not exist.
        if (!container.exists()) {
            throw new Exception("WAStorageClient: generateSASURL: Container " + containerName
                    + " does not exist in storage account " + storageAccountName);
        }

        CloudBlob blob = container.getBlockBlobReference(blobName);
        String sas = blob.generateSharedAccessSignature(generatePolicy(), null);

        return sas;
    }

    public static SharedAccessBlobPolicy generatePolicy() {
        SharedAccessBlobPolicy policy = new SharedAccessBlobPolicy();
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.setTime(new Date());

        calendar.add(Calendar.HOUR, 1);
        policy.setSharedAccessExpiryTime(calendar.getTime());
        policy.setPermissions(EnumSet.of(SharedAccessBlobPermissions.READ));

        return policy;
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

    /**
     * Returns custom URL for queue and table.
     *
     * @param storageAccountName
     * @param type
     * @param blobURL
     * @return
     */
    private static String getCustomURI(String storageAccountName, String type, String blobURL) {
        if (QUEUE.equalsIgnoreCase(type) || TABLE.equalsIgnoreCase(type)) {
            return blobURL.replace(storageAccountName + "." + BLOB,
                    storageAccountName + "." + type);
        } else {
            return null;
        }
    }

    private AzureUtils() {
        // hide constructor
    }
}
