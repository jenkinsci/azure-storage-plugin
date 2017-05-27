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
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoftopentechnologies.windowsazurestorage.Messages;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.service.model.BuilderServiceData;

import java.io.IOException;
import java.net.URISyntaxException;

public class DownloadFromContainerService extends DownloadService {
    public DownloadFromContainerService(final BuilderServiceData data) {
        super(data);
    }

    @Override
    public int execute() {
        int filesDownloaded = 0;
        try {
            println(Messages.AzureStorageBuilder_downloading());
            final CloudBlobContainer container = AzureUtils.getBlobContainerReference(serviceData.getStorageAccountInfo(),
                    serviceData.getContainerName(), false, true, null);
            filesDownloaded = downloadBlobs(container.listBlobs());
        } catch (StorageException | URISyntaxException | IOException | WAStorageException e) {
            e.printStackTrace(error(Messages.AzureStorageBuilder_download_err(serviceData.getStorageAccountInfo().getStorageAccName())));
            setRunUnstable();
        }

        return filesDownloaded;
    }
}
