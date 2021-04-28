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
import com.azure.storage.file.share.ShareClient;
import com.azure.storage.file.share.ShareDirectoryClient;
import com.azure.storage.file.share.ShareServiceClient;
import com.azure.storage.file.share.models.ShareFileItem;
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
        int filesNeedDownload;
        try {
            println(Messages.AzureStorageBuilder_downloading());
            final ShareClient cloudFileShare = getCloudFileShare();
            final ShareDirectoryClient cloudFileDirectory = cloudFileShare.getRootDirectoryClient();
            filesNeedDownload = scanFileItems(cloudFileShare, cloudFileDirectory,
                    cloudFileDirectory.listFilesAndDirectories()
            );
            println(Messages.AzureStorageBuilder_files_need_download_count(filesNeedDownload));
            waitForDownloadEnd();
        } catch (URISyntaxException | MalformedURLException | WAStorageException e) {
            final String message = Messages.AzureStorageBuilder_download_err(
                    getServiceData().getStorageAccountInfo().getStorageAccName()) + ":" + e.getMessage();
            e.printStackTrace(error(message));
            println(message);
            setRunUnstable();
        }

        return getFilesDownloaded();
    }

    private int scanFileItems(ShareClient shareClient,
                              ShareDirectoryClient cloudFileDirectory,
                              PagedIterable<ShareFileItem> fileItems
    )
            throws URISyntaxException {
        final DownloadServiceData data = getServiceData();
        int filesNeedDownload = 0;
        for (final ShareFileItem fileItem : fileItems) {
            if (fileItem.isDirectory()) {

                String filePath = prependDirectoryPathIfRequired(
                        cloudFileDirectory.getDirectoryPath(), fileItem.getName() + "/"
                );

                if (shouldDownload(
                        data.getIncludeFilesPattern(),
                        data.getExcludeFilesPattern(),
                        filePath,
                        false)) {
                    ShareDirectoryClient subdirectoryClient = shareClient.getDirectoryClient(
                            prependDirectoryPathIfRequired(cloudFileDirectory.getDirectoryPath(), fileItem.getName())
                    );
                    return scanFileItems(shareClient, subdirectoryClient, subdirectoryClient.listFilesAndDirectories());
                }
            }

            if (shouldDownload(
                    data.getIncludeFilesPattern(),
                    data.getExcludeFilesPattern(),
                    prependDirectoryPathIfRequired(cloudFileDirectory.getDirectoryPath(), fileItem.getName()),
                    true)) {
                getExecutorService().submit(new DownloadThread(cloudFileDirectory.getFileClient(fileItem.getName())));
                filesNeedDownload++;
            }
        }
        return filesNeedDownload;
    }

    private String prependDirectoryPathIfRequired(String directoryPath, String fileName) {
        if (!directoryPath.equals("")) {
            return directoryPath + "/" + fileName;
        }
        return fileName;
    }

    private ShareClient getCloudFileShare()
            throws URISyntaxException, MalformedURLException, WAStorageException {
        final DownloadServiceData serviceData = getServiceData();
        final ShareServiceClient cloudStorageAccount =
                AzureUtils.getShareClient(serviceData.getStorageAccountInfo());
        final ShareClient fileShare = cloudStorageAccount.getShareClient(serviceData.getFileShare());
        if (!fileShare.exists()) {
            throw new WAStorageException("Specified file share doesn't exist.");
        }
        return fileShare;
    }
}
