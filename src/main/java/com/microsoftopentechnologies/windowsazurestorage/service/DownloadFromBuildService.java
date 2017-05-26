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

package com.microsoftopentechnologies.windowsazurestorage.service;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoftopentechnologies.windowsazurestorage.AzureBlob;
import com.microsoftopentechnologies.windowsazurestorage.AzureBlobAction;
import com.microsoftopentechnologies.windowsazurestorage.Messages;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;
import com.microsoftopentechnologies.windowsazurestorage.service.model.BuilderServiceData;
import hudson.EnvVars;
import hudson.matrix.MatrixBuild;
import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.copyartifact.BuildFilter;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class DownloadFromBuildService extends DownloadBlobService {
    public DownloadFromBuildService(final BuilderServiceData data) {
        super(data);
    }

    @Override
    public int execute() {
        int filesDownloaded = 0;

        try {
            Job<?, ?> job = Utils.getJenkinsInstance().getItemByFullName(serviceData.getProjectName(), Job.class);
            if (job != null) {
                // Resolve download location
                final EnvVars envVars = serviceData.getRun().getEnvironment(serviceData.getTaskListener());
                BuildFilter filter = new BuildFilter();
                Run source = serviceData.getBuildSelector().getBuild(job, envVars, filter, serviceData.getRun());

                if (source instanceof MatrixBuild) {
                    for (Run r : ((MatrixBuild) source).getExactRuns()) {
                        filesDownloaded += downloadArtifacts(r);
                    }
                } else {
                    filesDownloaded += downloadArtifacts(source);
                }
            } else {
                println(Messages.AzureStorageBuilder_job_invalid(serviceData.getProjectName()));
                setRunUnstable();
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace(error(Messages.AzureStorageBuilder_download_err(serviceData.getStorageAccountInfo().getStorageAccName())));
            setRunUnstable();
        }
        return filesDownloaded;
    }

    private int downloadArtifacts(final Run<?, ?> source) {
        int filesDownloaded = 0;
        try {

            final AzureBlobAction action = source.getAction(AzureBlobAction.class);
            List<AzureBlob> azureBlobs = action.getIndividualBlobs();
            if (action.getZipArchiveBlob() != null && serviceData.isIncludeArchiveZips()) {
                azureBlobs.addAll(Arrays.asList(action.getZipArchiveBlob()));
            }

            filesDownloaded = downloarAzureBlobs(azureBlobs);
        } catch (WAStorageException e) {
            setRunUnstable();
        }

        return filesDownloaded;
    }

    private int downloarAzureBlobs(final List<AzureBlob> azureBlobs) throws WAStorageException {
        int filesDownloaded = 0;

        for (final AzureBlob blob : azureBlobs) {
            try {
                println(Messages.AzureStorageBuilder_downloading());

                final URL blobURL = new URL(blob.getBlobURL());
                final String filePath = blobURL.getFile();

                final CloudBlobContainer container = AzureUtils.getBlobContainerReference(serviceData.getStorageAccountInfo(),
                        filePath.split("/")[1], false, true, null);

                if (shouldDownload(serviceData.getIncludeFilesPattern(), serviceData.getExcludeFilesPattern(), blob.getBlobName(), true)) {
                    final CloudBlockBlob cbb = container.getBlockBlobReference(blob.getBlobName());
                    downloadBlob(cbb);
                    filesDownloaded++;
                }
            } catch (StorageException | URISyntaxException | IOException e) {
                throw new WAStorageException(e.getMessage(), e.getCause());
            }
        }
        return filesDownloaded;
    }
}
