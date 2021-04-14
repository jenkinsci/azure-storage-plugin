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

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.file.CloudFile;
import com.microsoft.azure.storage.file.CloudFileClient;
import com.microsoft.azure.storage.file.CloudFileShare;
import com.microsoftopentechnologies.windowsazurestorage.AzureBlob;
import com.microsoftopentechnologies.windowsazurestorage.AzureBlobAction;
import com.microsoftopentechnologies.windowsazurestorage.Messages;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.helper.Constants;
import com.microsoftopentechnologies.windowsazurestorage.service.model.DownloadServiceData;
import hudson.EnvVars;
import hudson.matrix.MatrixBuild;
import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.copyartifact.BuildFilter;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class DownloadFromBuildService extends DownloadService {
    private CloudBlobContainer cloudBlobContainer;
    private CloudFileShare cloudFileShare;

    public DownloadFromBuildService(DownloadServiceData data) {
        super(data);
    }

    @Override
    public int execute() {
        final DownloadServiceData serviceData = getServiceData();
        int filesDownloaded = 0;

        try {
            Job<?, ?> job = Jenkins.get().getItemByFullName(serviceData.getProjectName(), Job.class);
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
            e.printStackTrace(error(Messages.AzureStorageBuilder_download_err(
                    serviceData.getStorageAccountInfo().getStorageAccName())));
            setRunUnstable();
        }
        return filesDownloaded;
    }

    private int downloadArtifacts(Run<?, ?> source) {
        final DownloadServiceData serviceData = getServiceData();
        int filesNeedDownload;
        try {

            final AzureBlobAction action = source.getAction(AzureBlobAction.class);
            if (action == null) {
                return getFilesDownloaded();
            }
            List<AzureBlob> azureBlobs = action.getIndividualBlobs();
            if (action.getZipArchiveBlob() != null && serviceData.isIncludeArchiveZips()) {
                synchronized (azureBlobs) {
                    azureBlobs.addAll(Arrays.asList(action.getZipArchiveBlob()));
                }
            }
            filesNeedDownload = scanBlobs(azureBlobs);
            println(Messages.AzureStorageBuilder_files_need_download_count(filesNeedDownload));
            waitForDownloadEnd();
        } catch (WAStorageException e) {
            setRunUnstable();
        }
        return getFilesDownloaded();
    }

    private int scanBlobs(List<AzureBlob> azureBlobs) throws WAStorageException {
        final DownloadServiceData serviceData = getServiceData();
        int filesNeedDownload = 0;
        println(Messages.AzureStorageBuilder_downloading());

        for (final AzureBlob blob : azureBlobs) {
            try {
                final URL blobURL = new URL(blob.getBlobURL());
                final String filePath = blobURL.getFile();

                if (shouldDownload(
                        serviceData.getIncludeFilesPattern(),
                        serviceData.getExcludeFilesPattern(),
                        blob.getBlobName(),
                        true)) {
                    if (Constants.BLOB_STORAGE.equalsIgnoreCase(blob.getStorageType())) {
                        if (cloudBlobContainer == null) {
                            cloudBlobContainer = AzureUtils.getBlobContainerReference(
                                    serviceData.getStorageAccountInfo(),
                                    filePath.split("/")[1],
                                    false,
                                    true,
                                    null);
                        }
                        final CloudBlockBlob cbb = cloudBlobContainer.getBlockBlobReference(blob.getBlobName());
                        getExecutorService().submit(new DownloadThread(cbb));
                        filesNeedDownload++;
                    } else if (Constants.FILE_STORAGE.equalsIgnoreCase(blob.getStorageType())) {
                        if (cloudFileShare == null) {
                            final CloudStorageAccount cloudStorageAccount =
                                    AzureUtils.getCloudStorageAccount(serviceData.getStorageAccountInfo());
                            final CloudFileClient cloudFileClient = cloudStorageAccount.createCloudFileClient();
                            cloudFileShare = cloudFileClient.getShareReference(filePath.split("/")[1]);
                        }
                        final String cloudFileName = filePath.substring(
                                filePath.indexOf(cloudFileShare.getName()) + cloudFileShare.getName().length() + 1);
                        final CloudFile cloudFile =
                                cloudFileShare.getRootDirectoryReference().getFileReference(cloudFileName);
                        getExecutorService().submit(new DownloadThread(cloudFile));
                        filesNeedDownload++;
                    }
                }
            } catch (StorageException | URISyntaxException | IOException e) {
                throw new WAStorageException(e.getMessage(), e);
            }
        }
        return filesNeedDownload;
    }
}
