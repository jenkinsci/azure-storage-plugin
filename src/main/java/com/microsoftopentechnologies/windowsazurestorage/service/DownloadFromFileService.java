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
import com.microsoft.azure.storage.file.CloudFile;
import com.microsoft.azure.storage.file.CloudFileClient;
import com.microsoft.azure.storage.file.CloudFileDirectory;
import com.microsoft.azure.storage.file.CloudFileShare;
import com.microsoft.azure.storage.file.ListFileItem;
import com.microsoftopentechnologies.windowsazurestorage.Messages;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.service.model.DownloadServiceData;

import java.net.MalformedURLException;
import java.net.URISyntaxException;

public class DownloadFromFileService extends DownloadService {
    public DownloadFromFileService(DownloadServiceData data) {
        super(data);
    }

    @Override
    public int execute() {
        int filesNeedDownload = 0;
        try {
            println(Messages.AzureStorageBuilder_downloading());
            final CloudFileShare cloudFileShare = getCloudFileShare();
            final CloudFileDirectory cloudFileDirectory = cloudFileShare.getRootDirectoryReference();
            startDownloadThreads();
            filesNeedDownload = scanFileItems(cloudFileDirectory.listFilesAndDirectories());
            setFilesNeedDownload(filesNeedDownload);
            setIsScanFinished(true);
            waitForDownloadEnd();
        } catch (StorageException | URISyntaxException | MalformedURLException | WAStorageException e) {
            final String message = Messages.AzureStorageBuilder_download_err(
                    getServiceData().getStorageAccountInfo().getStorageAccName()) + ":" + e.getMessage();
            e.printStackTrace(error(message));
            println(message);
            setRunUnstable();
        }

        return filesNeedDownload;
    }

    private int scanFileItems(Iterable<ListFileItem> fileItems) throws WAStorageException {
        final DownloadServiceData data = getServiceData();
        int filesNeedDownload = 0;
        for (final ListFileItem fileItem : fileItems) {
            if (fileItem instanceof CloudFile) {
                final CloudFile cloudFile = (CloudFile) fileItem;
                final boolean shouldDownload = shouldDownload(data.getIncludeFilesPattern(),
                        data.getExcludeFilesPattern(), cloudFile.getName(), true);
                if (shouldDownload) {
                    getDownloadItemDeque().push(cloudFile);
                    filesNeedDownload++;
                }
            } else if (fileItem instanceof CloudFileDirectory) {
                filesNeedDownload += scanFileItems(((CloudFileDirectory) fileItem).listFilesAndDirectories());
            }
        }
        return filesNeedDownload;
    }

    private CloudFileShare getCloudFileShare()
            throws URISyntaxException, MalformedURLException, StorageException, WAStorageException {
        final DownloadServiceData serviceData = getServiceData();
        final CloudStorageAccount cloudStorageAccount =
                AzureUtils.getCloudStorageAccount(serviceData.getStorageAccountInfo());
        final CloudFileClient cloudFileClient = cloudStorageAccount.createCloudFileClient();
        final CloudFileShare fileShare = cloudFileClient.getShareReference(serviceData.getFileShare());
        if (!fileShare.exists()) {
            throw new WAStorageException("Specified file share doesn't exist.");
        }
        return fileShare;
    }
}
