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

public class UploadServiceData extends ServiceData {
    private String containerName;
    private String fileShareName;
    private AzureBlobProperties blobProperties;
    private boolean pubAccessible;
    private boolean cleanUpContainerOrShare;
    private String filePath;
    private String virtualPath;
    private String excludedFilesPath;
    private UploadType uploadType;
    private List<AzureBlob> individualBlobs = new ArrayList<AzureBlob>();
    private List<AzureBlob> archiveBlobs = new ArrayList<AzureBlob>();
    private List<AzureBlobMetadataPair> azureBlobMetadata;

    public UploadServiceData(final Run<?, ?> run,
                             final FilePath workspace,
                             final Launcher launcher,
                             final TaskListener taskListener,
                             final StorageAccountInfo storageAccountInfo) {
        super(run, workspace, launcher, taskListener, storageAccountInfo);
    }


    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(final String containerName) {
        this.containerName = containerName;
    }

    public AzureBlobProperties getBlobProperties() {
        return blobProperties;
    }

    public void setBlobProperties(final AzureBlobProperties blobProperties) {
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

    public void setPubAccessible(final boolean pubAccessible) {
        this.pubAccessible = pubAccessible;
    }

    public boolean isCleanUpContainerOrShare() {
        return cleanUpContainerOrShare;
    }

    public void setCleanUpContainerOrShare(final boolean cleanUpContainerOrShare) {
        this.cleanUpContainerOrShare = cleanUpContainerOrShare;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(final String filePath) {
        this.filePath = filePath;
    }

    public String getVirtualPath() {
        return virtualPath;
    }

    public void setVirtualPath(final String virtualPath) {
        this.virtualPath = virtualPath;
    }

    public String getExcludedFilesPath() {
        return excludedFilesPath;
    }

    public void setExcludedFilesPath(final String excludedFilesPath) {
        this.excludedFilesPath = excludedFilesPath;
    }

    public UploadType getUploadType() {
        return uploadType;
    }

    public void setUploadType(final UploadType uploadType) {
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

    public void setAzureBlobMetadata(final List<AzureBlobMetadataPair> azureBlobMetadata) {
        this.azureBlobMetadata = azureBlobMetadata;
    }

    public String getFileShareName() {
        return fileShareName;
    }

    public void setFileShareName(final String fileShareName) {
        this.fileShareName = fileShareName;
    }
}
