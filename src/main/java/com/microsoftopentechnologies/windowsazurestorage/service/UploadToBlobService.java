/*
 Copyright 2017 Microsoft Open Technologies, Inc.

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

package com.microsoftopentechnologies.windowsazurestorage.service;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobProperties;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlobDirectory;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;
import com.microsoft.jenkins.azurecommons.telemetry.AppInsightsConstants;
import com.microsoft.jenkins.azurecommons.telemetry.AppInsightsUtils;
import com.microsoftopentechnologies.windowsazurestorage.AzureStoragePlugin;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.helper.Constants;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;
import com.microsoftopentechnologies.windowsazurestorage.service.model.PartialBlobProperties;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadServiceData;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadType;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.util.DirScanner;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service to upload files to Windows Azure Blob Storage.
 */
public class UploadToBlobService extends UploadService {


    public UploadToBlobService(UploadServiceData serviceData) {
        super(serviceData);
    }

    @Override
    protected void uploadArchive(String archiveIncludes)
            throws WAStorageException {
        final UploadServiceData serviceData = getServiceData();
        try {
            final CloudBlobContainer container = getCloudBlobContainer();
            if (serviceData.getUploadType() == UploadType.ZIP) {
                cleanupContainer(container);
            }

            final FilePath workspacePath = serviceData.getRemoteWorkspace();
            // Create a temp dir for the upload
            final FilePath tempDir = workspacePath.createTempDir(ZIP_FOLDER_NAME, null);
            final FilePath zipPath = tempDir.child(ZIP_NAME);

            // zip included files into archive.zip file.
            final DirScanner.Glob globScanner = new DirScanner.Glob(archiveIncludes, excludedFilesAndZip());
            workspacePath.zip(zipPath.write(), globScanner);

            // When uploading the zip, do not add in the tempDir to the azure
            String blobURI = zipPath.getName();
            if (!StringUtils.isBlank(serviceData.getVirtualPath())) {
                blobURI = serviceData.getVirtualPath() + blobURI;
            }

            final CloudBlockBlob blob = container.getBlockBlobReference(blobURI);

            List<UploadObject> uploadObjects = new ArrayList<>();
            UploadObject uploadObject = generateUploadObject(zipPath, serviceData.getStorageAccountInfo(),
                    blob, container.getName());
            uploadObjects.add(uploadObject);

            UploadOnSlave uploadOnSlave = new UploadOnSlave(uploadObjects);
            List<UploadResult> results = workspacePath.act(uploadOnSlave);

            updateAzureBlobs(results, serviceData.getArchiveBlobs());

            tempDir.deleteRecursive();
        } catch (Exception e) {
            String storageAcc = AppInsightsUtils.hash(serviceData.getStorageAccountInfo().getStorageAccName());
            AzureStoragePlugin.sendEvent(AppInsightsConstants.AZURE_BLOB_STORAGE, UPLOAD_FAILED,
                    "StorageAccount", storageAcc,
                    "Message", e.getMessage());
            throw new WAStorageException("Fail to upload individual files to blob", e);
        }
    }

    private UploadObject generateUploadObject(FilePath path, StorageAccountInfo accountInfo,
                                              CloudBlockBlob blob, String containerName) throws Exception {
        String sas = generateWriteSASURL(accountInfo, blob.getName(),
                Constants.BLOB_STORAGE, containerName);
        String blobURL = blob.getUri().toString().replace("http://", "https://");
        return new UploadObject(blob.getName(), path, blobURL, sas, Constants.BLOB_STORAGE,
                blob.getServiceClient().getCredentials().getAccountName(),
                convertBlobProperties(blob.getProperties()), blob.getMetadata());
    }

    private PartialBlobProperties convertBlobProperties(BlobProperties properties) {
        return new PartialBlobProperties(properties.getContentEncoding(), properties.getContentLanguage(),
                properties.getCacheControl(), properties.getContentType());
    }

    @Override
    protected void uploadIndividuals(String embeddedVP, FilePath[] paths, FilePath workspace)
            throws WAStorageException {
        final UploadServiceData serviceData = getServiceData();
        try {
            final CloudBlobContainer container = getCloudBlobContainer();
            UploadType uploadType = serviceData.getUploadType();
            if (uploadType == UploadType.INDIVIDUAL || uploadType == UploadType.BOTH) {
                cleanupContainer(container);
            }

            List<UploadObject> uploadObjects = new ArrayList<>();
            for (FilePath src : paths) {
                final String blobPath = getItemPath(src, embeddedVP);
                final CloudBlockBlob blob = container.getBlockBlobReference(blobPath);
                configureBlobPropertiesAndMetadata(blob, src);

                UploadObject uploadObject = generateUploadObject(src, serviceData.getStorageAccountInfo(),
                        blob, container.getName());
                uploadObjects.add(uploadObject);
            }

            UploadOnSlave uploadOnSlave = new UploadOnSlave(uploadObjects);
            List<UploadResult> results = workspace.act(uploadOnSlave);

            updateAzureBlobs(results, serviceData.getIndividualBlobs());

        } catch (Exception e) {
            String storageAcc = AppInsightsUtils.hash(serviceData.getStorageAccountInfo().getStorageAccName());
            AzureStoragePlugin.sendEvent(AppInsightsConstants.AZURE_BLOB_STORAGE, UPLOAD_FAILED,
                    "StorageAccount", storageAcc,
                    "Message", e.getMessage());
            throw new WAStorageException("Fail to upload archive to blob", e);
        }
    }

    @Deprecated
    @Override
    protected void uploadIndividuals(String embeddedVP, FilePath[] paths) throws WAStorageException {
        throw new NotImplementedException();
    }

    private void configureBlobPropertiesAndMetadata(
            final CloudBlockBlob blob,
            final FilePath src) throws IOException, InterruptedException {
        final UploadServiceData serviceData = getServiceData();
        final EnvVars env = serviceData.getRun().getEnvironment(serviceData.getTaskListener());

        // Set blob properties
        if (serviceData.getBlobProperties() != null) {
            serviceData.getBlobProperties().configure(blob, src, env);
        }

        // Set blob metadata
        if (serviceData.getAzureBlobMetadata() != null) {
            blob.setMetadata(updateMetadata(blob.getMetadata()));
        }
    }

    private CloudBlobContainer getCloudBlobContainer() throws URISyntaxException, StorageException, IOException {
        final UploadServiceData serviceData = getServiceData();
        final CloudBlobContainer container = AzureUtils.getBlobContainerReference(
                serviceData.getStorageAccountInfo(),
                serviceData.getContainerName(),
                true,
                true,
                serviceData.isPubAccessible());

        return container;
    }

    private void cleanupContainer(CloudBlobContainer container) throws
            StorageException, IOException, URISyntaxException {
        final UploadServiceData serviceData = getServiceData();
        // Delete previous contents if cleanup is needed
        if (serviceData.isCleanUpContainerOrShare()) {
            deleteBlobs(container.listBlobs());
        } else if (serviceData.isCleanUpVirtualPath() && StringUtils.isNotBlank(serviceData.getVirtualPath())) {
            deleteBlobs(container.getDirectoryReference(serviceData.getVirtualPath()).listBlobs());
        }
    }

    /**
     * Deletes contents of container.
     *
     * @param blobItems list of blobs to delete
     * @throws StorageException
     * @throws URISyntaxException
     */
    private void deleteBlobs(Iterable<ListBlobItem> blobItems)
            throws StorageException, URISyntaxException, IOException {

        for (ListBlobItem blobItem : blobItems) {
            if (blobItem instanceof CloudBlob) {
                ((CloudBlob) blobItem).uploadProperties(null, null, Utils.updateUserAgent());
                ((CloudBlob) blobItem).delete();
            } else if (blobItem instanceof CloudBlobDirectory) {
                deleteBlobs(((CloudBlobDirectory) blobItem).listBlobs());
            }
        }
    }
}
