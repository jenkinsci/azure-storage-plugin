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

package com.microsoftopentechnologies.windowsazurestorage.service.model;

import com.microsoftopentechnologies.windowsazurestorage.AzureBlob;
import com.microsoftopentechnologies.windowsazurestorage.AzureBlobMetadataPair;
import com.microsoftopentechnologies.windowsazurestorage.AzureBlobProperties;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.util.ArrayList;
import java.util.List;

public class PublisherServiceData extends ServiceData {
    String containerName;
    AzureBlobProperties blobProperties;
    boolean pubAccessible;
    boolean cleanUpContainer;
    String filePath;
    String virtualPath;
    String excludedFilesPath;
    UploadType uploadType;
    List<AzureBlob> individualBlobs = new ArrayList<AzureBlob>();
    List<AzureBlob> archiveBlobs = new ArrayList<AzureBlob>();
    List<AzureBlobMetadataPair> azureBlobMetadata;

    public PublisherServiceData(final Run<?, ?> run,
                                final FilePath workspace,
                                final Launcher launcher,
                                final TaskListener taskListener,
                                final StorageAccountInfo storageAccountInfo) {
        super(run, workspace, launcher, taskListener, storageAccountInfo);
    }


    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public AzureBlobProperties getBlobProperties() {
        return blobProperties;
    }

    public void setBlobProperties(AzureBlobProperties blobProperties) {
        this.blobProperties = blobProperties;
    }

    /**
     * denotes if container is publicly accessible.
     *
     * @return
     */
    public boolean isPubAccessible() {
        return pubAccessible;
    }

    public void setPubAccessible(boolean pubAccessible) {
        this.pubAccessible = pubAccessible;
    }

    public boolean isCleanUpContainer() {
        return cleanUpContainer;
    }

    public void setCleanUpContainer(boolean cleanUpContainer) {
        this.cleanUpContainer = cleanUpContainer;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getVirtualPath() {
        return virtualPath;
    }

    public void setVirtualPath(String virtualPath) {
        this.virtualPath = virtualPath;
    }

    public String getExcludedFilesPath() {
        return excludedFilesPath;
    }

    public void setExcludedFilesPath(String excludedFilesPath) {
        this.excludedFilesPath = excludedFilesPath;
    }

    public UploadType getUploadType() {
        return uploadType;
    }

    public void setUploadType(UploadType uploadType) {
        this.uploadType = uploadType;
    }

    public List<AzureBlob> getIndividualBlobs() {
        return individualBlobs;
    }

    public List<AzureBlob> getArchiveBlobs() {
        return archiveBlobs;
    }

    public List<AzureBlobMetadataPair> getAzureBlobMetadata() {
        return azureBlobMetadata;
    }

    public void setAzureBlobMetadata(List<AzureBlobMetadataPair> azureBlobMetadata) {
        this.azureBlobMetadata = azureBlobMetadata;
    }
}
