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
import com.microsoftopentechnologies.windowsazurestorage.Messages;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.service.model.DownloadServiceData;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DownloadFromContainerService extends DownloadService {

    private static final Logger LOGGER = Logger.getLogger(DownloadFromContainerService.class.getName());
    public DownloadFromContainerService(DownloadServiceData data) {
        super(data);
    }

    @Override
    public int execute() {
        final DownloadServiceData serviceData = getServiceData();
        int filesNeedDownload;
        try {
            if (serviceData.isVerbose()) {
                println(Messages.AzureStorageBuilder_downloading());
            }
            final BlobContainerClient container = AzureUtils.getBlobContainerReference(
                    serviceData.getStorageAccountInfo(),
                    serviceData.getContainerName(),
                    false,
                    true,
                    null);
            filesNeedDownload = scanBlobs(container, container.listBlobs());
            println(Messages.AzureStorageBuilder_files_need_download_count(filesNeedDownload));
            waitForDownloadEnd();
        } catch (URISyntaxException | IOException | WAStorageException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            e.printStackTrace(error(Messages.AzureStorageBuilder_download_err(
                    serviceData.getStorageAccountInfo().getStorageAccName())));
            setRunUnstable();
        }
        return getFilesDownloaded();
    }

    protected int scanBlobs(BlobContainerClient container, PagedIterable<BlobItem> blobItems)
            throws URISyntaxException, WAStorageException {
        final DownloadServiceData serviceData = getServiceData();
        int filesNeedDownload = 0;
        for (final BlobItem blobItem : blobItems) {
            if (shouldDownload(
                    serviceData.getIncludeFilesPattern(),
                    serviceData.getExcludeFilesPattern(),
                    blobItem.getName(),
                    true)) {
                getExecutorService().submit(new DownloadThread(container.getBlobClient(blobItem.getName())));
                filesNeedDownload++;
            }
        }
        return filesNeedDownload;
    }
}
