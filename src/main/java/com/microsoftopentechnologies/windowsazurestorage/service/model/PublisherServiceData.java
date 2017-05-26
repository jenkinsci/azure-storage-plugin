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
