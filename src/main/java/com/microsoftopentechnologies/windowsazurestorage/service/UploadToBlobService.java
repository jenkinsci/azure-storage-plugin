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

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.helper.Constants;
import com.microsoftopentechnologies.windowsazurestorage.service.model.PartialBlobProperties;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadServiceData;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadType;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.util.DirScanner;
import jenkins.model.Jenkins;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to upload files to Azure Blob Storage.
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
            final BlobContainerClient container = getCloudBlobContainer();
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

            final BlockBlobClient blob = container.getBlobClient(blobURI).getBlockBlobClient();

            List<UploadObject> uploadObjects = new ArrayList<>();
            PartialBlobProperties blobProperties = new PartialBlobProperties(
                    "UTF-8", null, null, null);
            UploadObject uploadObject = generateUploadObject(zipPath, serviceData.getStorageAccountInfo(),
                    blob, container.getBlobContainerName(), blobProperties, updateMetadata(new HashMap<>()));
            uploadObjects.add(uploadObject);

            UploadOnSlave uploadOnSlave = new UploadOnSlave(Jenkins.get().proxy, uploadObjects);
            List<UploadResult> results = workspacePath.act(uploadOnSlave);

            updateAzureBlobs(results, serviceData.getArchiveBlobs());

            tempDir.deleteRecursive();
        } catch (Exception e) {
            throw new WAStorageException("Fail to upload archive to blob", e);
        }
    }

    private UploadObject generateUploadObject(FilePath path, StorageAccountInfo accountInfo,
                                              BlockBlobClient blob, String containerName,
                                              PartialBlobProperties blobProperties,
                                              Map<String, String> metadata) throws Exception {
        String sas = generateWriteSASURL(accountInfo, blob.getBlobName(),
                Constants.BLOB_STORAGE, containerName);

        return new UploadObject(blob.getBlobName(), path, blob.getBlobUrl(), sas, Constants.BLOB_STORAGE,
                blob.getAccountName(), blobProperties,
                metadata);
    }

    @Override
    protected void uploadIndividuals(String embeddedVP, FilePath[] paths, FilePath workspace)
            throws WAStorageException {
        final UploadServiceData serviceData = getServiceData();
        try {
            final BlobContainerClient container = getCloudBlobContainer();
            UploadType uploadType = serviceData.getUploadType();
            if (uploadType == UploadType.INDIVIDUAL || uploadType == UploadType.BOTH) {
                cleanupContainer(container);
            }

            List<UploadObject> uploadObjects = new ArrayList<>();
            for (FilePath src : paths) {
                final String blobPath = getItemPath(src, embeddedVP, serviceData);
                final BlockBlobClient blob = container.getBlobClient(blobPath).getBlockBlobClient();
                PartialBlobProperties blobProperties = configureBlobProperties(src);

                UploadObject uploadObject = generateUploadObject(src, serviceData.getStorageAccountInfo(),
                        blob, container.getBlobContainerName(), blobProperties, updateMetadata(new HashMap<>()));
                uploadObjects.add(uploadObject);
            }

            UploadOnSlave uploadOnSlave = new UploadOnSlave(Jenkins.get().proxy, uploadObjects);
            List<UploadResult> results = workspace.act(uploadOnSlave);

            updateAzureBlobs(results, serviceData.getIndividualBlobs());

        } catch (Exception e) {
            throw new WAStorageException("Fail to upload individual files to blob", e);
        }
    }

    @Deprecated
    @Override
    protected void uploadIndividuals(String embeddedVP, FilePath[] paths) throws WAStorageException {
        throw new NotImplementedException();
    }

    private PartialBlobProperties configureBlobProperties(
            final FilePath src) throws IOException, InterruptedException {
        final UploadServiceData serviceData = getServiceData();
        final EnvVars env = serviceData.getRun().getEnvironment(serviceData.getTaskListener());

        // Set blob properties
        if (serviceData.getBlobProperties() != null) {
            return serviceData.getBlobProperties().configure(src, env);
        }

        return new PartialBlobProperties("UTF-8", null, null, null);

    }

    private BlobContainerClient getCloudBlobContainer() throws URISyntaxException, IOException {
        final UploadServiceData serviceData = getServiceData();
        final BlobContainerClient container = AzureUtils.getBlobContainerReference(
                serviceData.getStorageAccountInfo(),
                serviceData.getContainerName(),
                true,
                true,
                serviceData.isPubAccessible());

        return container;
    }

    private void cleanupContainer(BlobContainerClient container) throws
            IOException, URISyntaxException {
        final UploadServiceData serviceData = getServiceData();
        // Delete previous contents if cleanup is needed
        if (serviceData.isCleanUpContainerOrShare()) {
            println("Clean up existing blobs in container " + serviceData.getContainerName());
            deleteBlobs(container, container.listBlobs());
        } else if (serviceData.isCleanUpVirtualPath() && StringUtils.isNotBlank(serviceData.getVirtualPath())) {
            println("Clean up existing blobs in container path " + serviceData.getVirtualPath());
            deleteBlobs(container, container.listBlobsByHierarchy(serviceData.getVirtualPath()));
        }
    }

    /**
     * Deletes contents of container.
     *
     * @param container the blob container client
     * @param blobItems list of blobs to delete
     */
    private void deleteBlobs(BlobContainerClient container, PagedIterable<BlobItem> blobItems) {
        for (BlobItem blobItem : blobItems) {
            container.getBlobClient(blobItem.getName()).delete();
        }
    }
}
