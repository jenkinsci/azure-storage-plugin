/*
 * Copyright (c) Microsoft Corporation
 *   <p/>
 *  All rights reserved.
 *   <p/>
 *  MIT License
 *   <p/>
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 *  documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 *  the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 *  to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *  <p/>
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 *  the Software.
 *   <p/>
 *  THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 *  THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 *  TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package com.microsoftopentechnologies.windowsazurestorage.helper;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.RetryNoRetry;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.*;
import com.microsoftopentechnologies.windowsazurestorage.Messages;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

public class AzureUtils {
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

    public static CloudStorageAccount getCloudStorageAccount(final StorageAccountInfo storageAccount) throws URISyntaxException {
        CloudStorageAccount cloudStorageAccount;
        final String accName = storageAccount.getStorageAccName();
        final String blobURL = storageAccount.getBlobEndPointURL();
        final StorageCredentialsAccountAndKey credentials = new StorageCredentialsAccountAndKey(accName,
                storageAccount.getStorageAccountKey());

        if (StringUtils.isBlank(blobURL) || blobURL.equalsIgnoreCase(Constants.DEF_BLOB_URL)) {
            cloudStorageAccount = new CloudStorageAccount(credentials);
        } else {
            cloudStorageAccount = new CloudStorageAccount(credentials, false, getEndpointSuffix(blobURL));
        }

        return cloudStorageAccount;
    }

    public static CloudBlobContainer getBlobContainerReference(final StorageAccountInfo storageAccount,
                                                               final String containerName,
                                                               final boolean createIfNotExist,
                                                               final boolean allowRetry,
                                                               final Boolean cntPubAccess)
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
     * Generates SAS URL for blob in Azure storage account
     *
     * @param storageAccount
     * @param blobName
     * @param containerName  container name
     * @return SAS URL
     * @throws Exception
     */
    public static String generateSASURL(StorageAccountInfo storageAccount, String containerName, String blobName) throws Exception {
        String storageAccountName = storageAccount.getStorageAccName();
        StorageCredentialsAccountAndKey credentials = new StorageCredentialsAccountAndKey(storageAccountName, storageAccount.getStorageAccountKey());
        URL blobURL = new URL(storageAccount.getBlobEndPointURL());
        String saBlobURI = new StringBuilder().append(blobURL.getProtocol()).append("://").append(storageAccountName).append(".")
                .append(blobURL.getHost()).append("/").toString();
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

    private static void setContainerPermission(final CloudBlobContainer container, final boolean cntExists, final Boolean cntPubAccess)
            throws StorageException {
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
    private static String getEndpointSuffix(final String blobURL) throws URISyntaxException {
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
    private static String getCustomURI(String storageAccountName, String type,
                                       String blobURL) {
        if (QUEUE.equalsIgnoreCase(type)) {
            return blobURL.replace(storageAccountName + "." + BLOB,
                    storageAccountName + "." + type);
        } else if (TABLE.equalsIgnoreCase(type)) {
            return blobURL.replace(storageAccountName + "." + BLOB,
                    storageAccountName + "." + type);
        } else {
            return null;
        }
    }
}
