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
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlobDirectory;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;
import com.microsoft.jenkins.azurecommons.telemetry.AppInsightsConstants;
import com.microsoft.jenkins.azurecommons.telemetry.AppInsightsUtils;
import com.microsoftopentechnologies.windowsazurestorage.AzureStoragePlugin;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadServiceData;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.util.DirScanner;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

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
            Future<?> archiveUploadFuture = getExecutorService().submit(new UploadThread(blob,
                    zipPath, serviceData.getArchiveBlobs()));
            archiveUploadFuture.get();
            tempDir.deleteRecursive();
        } catch (IOException | InterruptedException | URISyntaxException | StorageException | ExecutionException e) {
            String storageAcc = AppInsightsUtils.hash(serviceData.getStorageAccountInfo().getStorageAccName());
            AzureStoragePlugin.sendEvent(AppInsightsConstants.AZURE_BLOB_STORAGE, UPLOAD_FAILED,
                    "StorageAccount", storageAcc,
                    "Message", e.getMessage());
            throw new WAStorageException("Fail to upload individual files to blob", e);
        }
    }

    @Override
    protected void uploadIndividuals(String embeddedVP, FilePath[] paths)
            throws WAStorageException {
        final UploadServiceData serviceData = getServiceData();
        try {
            final CloudBlobContainer container = getCloudBlobContainer();

            for (FilePath src : paths) {
                final String blobPath = getItemPath(src, embeddedVP);
                final CloudBlockBlob blob = container.getBlockBlobReference(blobPath);
                configureBlobPropertiesAndMetadata(blob, src);
                getExecutorService().submit(new UploadThread(blob, src, serviceData.getIndividualBlobs()));
            }
        } catch (IOException | InterruptedException | URISyntaxException | StorageException e) {
            String storageAcc = AppInsightsUtils.hash(serviceData.getStorageAccountInfo().getStorageAccName());
            AzureStoragePlugin.sendEvent(AppInsightsConstants.AZURE_BLOB_STORAGE, UPLOAD_FAILED,
                    "StorageAccount", storageAcc,
                    "Message", e.getMessage());
            throw new WAStorageException("Fail to upload archive to blob", e);
        }
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

        // Delete previous contents if cleanup is needed
        if (serviceData.isCleanUpContainerOrShare()) {
            deleteBlobs(container.listBlobs());
        }
        return container;
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
