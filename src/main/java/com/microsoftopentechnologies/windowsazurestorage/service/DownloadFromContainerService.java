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
import com.microsoftopentechnologies.windowsazurestorage.Messages;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.service.model.BuilderServiceData;

import java.io.IOException;
import java.net.URISyntaxException;

public class DownloadFromContainerService extends DownloadBlobService {
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
