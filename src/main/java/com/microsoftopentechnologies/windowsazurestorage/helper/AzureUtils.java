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

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobAccessPolicy;
import com.azure.storage.blob.models.BlobSignedIdentifier;
import com.azure.storage.blob.models.PublicAccessType;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.common.policy.RequestRetryOptions;
import com.azure.storage.common.policy.RetryPolicyType;
import com.azure.storage.file.share.ShareClient;
import com.azure.storage.file.share.ShareFileClient;
import com.azure.storage.file.share.ShareServiceClient;
import com.azure.storage.file.share.ShareServiceClientBuilder;
import com.azure.storage.file.share.sas.ShareFileSasPermission;
import com.azure.storage.file.share.sas.ShareServiceSasSignatureValues;
import com.microsoftopentechnologies.windowsazurestorage.Messages;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import io.jenkins.plugins.azuresdk.HttpClientRetriever;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;

public final class AzureUtils {
    private static final String TEST_CNT_NAME = "testcheckfromjenkins";
    private static final String BLOB = "blob";
    private static final String QUEUE = "queue";
    private static final String TABLE = "table";

    private static final int ONE_WEEK = 7;

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
            final BlobContainerClient container = getBlobContainerReference(
                    storageAccount, TEST_CNT_NAME, false, false, null);
            container.exists();

        } catch (Exception e) {
            throw new WAStorageException(Messages.Client_SA_val_fail(), e);
        }
        return true;
    }

    public static BlobServiceClient getCloudStorageAccount(final StorageAccountInfo storageAccount)
            throws MalformedURLException, URISyntaxException {
        return getCloudStorageAccount(storageAccount, new RequestRetryOptions());
    }

    public static ShareServiceClient getShareClient(final StorageAccountInfo storageAccount)
            throws MalformedURLException, URISyntaxException {
        return new ShareServiceClientBuilder()
                .credential(new StorageSharedKeyCredential(storageAccount.getStorageAccName(),
                        storageAccount.getStorageAccountKey()))
                .httpClient(HttpClientRetriever.get())
                .endpoint(storageAccount.getBlobEndPointURL()
                        .replace("blob", "file")) // TODO add file endpoint
                .buildClient();
    }

    public static BlobServiceClient getCloudStorageAccount(
            final StorageAccountInfo storageAccount, RequestRetryOptions retryOptions) {
        return new BlobServiceClientBuilder()
                .credential(new StorageSharedKeyCredential(storageAccount.getStorageAccName(),
                        storageAccount.getStorageAccountKey()))
                .httpClient(HttpClientRetriever.get())
                .endpoint(storageAccount.getBlobEndPointURL())
                .retryOptions(retryOptions)
                .buildClient();
    }

    public static BlobContainerClient getBlobContainerReference(StorageAccountInfo storageAccount,
                                                                String containerName,
                                                                boolean createIfNotExist,
                                                                boolean allowRetry,
                                                                Boolean cntPubAccess)
            throws URISyntaxException, IOException {

        RequestRetryOptions retryOptions = new RequestRetryOptions();
        if (!allowRetry) {
            retryOptions = new RequestRetryOptions(
                    RetryPolicyType.FIXED,
                    1,
                    Duration.ofSeconds(Integer.MAX_VALUE),
                    Duration.ofMillis(1),
                    Duration.ofSeconds(1),
                    null
            );
        }

        final BlobServiceClient cloudStorageAccount = getCloudStorageAccount(storageAccount, retryOptions);
        final BlobContainerClient containerClient = cloudStorageAccount.getBlobContainerClient(containerName);

        boolean cntExists = containerClient.exists();
        if (createIfNotExist && !cntExists) {
            containerClient.create();
        }

        // Apply permissions only if container is created newly
        setContainerPermission(containerClient, cntExists, cntPubAccess);

        return containerClient;
    }

    public static String generateBlobSASURL(
            StorageAccountInfo storageAccount,
            String containerName,
            String blobName,
            BlobSasPermission permissions) throws Exception {

        BlobServiceClient cloudStorageAccount = getCloudStorageAccount(storageAccount);

        // Create the blob client.
        BlobContainerClient container = cloudStorageAccount.getBlobContainerClient(containerName);

        // At this point need to throw an error back since container itself did not exist.
        if (!container.exists()) {
            throw new Exception("WAStorageClient: generateBlobSASURL: Container " + containerName
                    + " does not exist in storage account " + storageAccount.getStorageAccName());
        }

        BlobClient blob = container.getBlobClient(blobName);

        BlobServiceSasSignatureValues sasSignatureValues =
                new BlobServiceSasSignatureValues(generateExpiryDate(), permissions);
        return blob.generateSas(sasSignatureValues);
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
            ShareFileSasPermission permissions) throws Exception {
        ShareServiceClient shareServiceClient = getShareClient(storageAccount);

        ShareClient fileShare = shareServiceClient.getShareClient((shareName));
        if (!fileShare.exists()) {
            throw new Exception("WAStorageClient: generateFileSASURL: Share " + shareName
                    + " does not exist in storage account " + storageAccount.getStorageAccName());
        }

        ShareFileClient cloudFile = fileShare.getRootDirectoryClient().getFileClient(fileName);
        ShareServiceSasSignatureValues sasSignatureValues =
                new ShareServiceSasSignatureValues(generateExpiryDate(), permissions);
        return cloudFile.generateSas(sasSignatureValues);
    }

    private static OffsetDateTime generateExpiryDate() {
        return OffsetDateTime.now().plusHours(1);
    }

    private static void setContainerPermission(
            BlobContainerClient container,
            boolean cntExists,
            Boolean cntPubAccess) {
        if (!cntExists && cntPubAccess != null) {
            // Set access permissions on container.
            if (cntPubAccess) {
                BlobSignedIdentifier identifier = new BlobSignedIdentifier()
                        .setId("name")
                        .setAccessPolicy(new BlobAccessPolicy()
                                .setStartsOn(OffsetDateTime.now())
                                .setExpiresOn(OffsetDateTime.now().plusDays(ONE_WEEK))
                                .setPermissions("r"));

                container.setAccessPolicy(PublicAccessType.CONTAINER, Collections.singletonList(identifier));
            }
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
