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

import com.microsoft.azure.storage.blob.BlobRequestOptions;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.service.model.ServiceData;
import hudson.model.Result;
import org.apache.commons.lang.time.DurationFormatUtils;

import java.io.PrintWriter;

public abstract class StoragePluginService<T extends ServiceData> {

    /*
     * A random name for container name to test validity of storage account
     * details
     */
    protected static final String FP_SEPARATOR = ",";

    private T serviceData;

    protected StoragePluginService(final T serviceData) {
        this.serviceData = serviceData;
    }

    public abstract int execute() throws WAStorageException;

    protected void setRunUnstable() {
        serviceData.getRun().setResult(Result.UNSTABLE);
    }

    protected void println(final String message) {
        serviceData.getTaskListener().getLogger().println(message);
    }

    protected PrintWriter error(final String message) {
        return serviceData.getTaskListener().error(message);
    }

    protected String getTime(final long timeInMills) {
        return DurationFormatUtils.formatDuration(timeInMills, "HH:mm:ss.S")
                + " (HH:mm:ss.S)";
    }

    /**
     * Returns Blob requests options - primarily sets concurrentRequestCount to
     * number of available cores.
     *
     * @return
     */
    protected BlobRequestOptions getBlobRequestOptions() {
        BlobRequestOptions options = new BlobRequestOptions();
        options.setConcurrentRequestCount(Runtime.getRuntime().availableProcessors());

        return options;
    }

    public T getServiceData() {
        return serviceData;
    }

    public void setServiceData(final T serviceData) {
        this.serviceData = serviceData;
    }
}
