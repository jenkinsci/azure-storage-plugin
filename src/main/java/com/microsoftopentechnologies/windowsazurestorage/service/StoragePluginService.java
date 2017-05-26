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
    protected static final String fpSeparator = ",";

    protected T serviceData;

    protected StoragePluginService(T serviceData) {
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

    protected String getTime(long timeInMills) {
        return DurationFormatUtils.formatDuration(timeInMills, "HH:mm:ss.S")
                + " (HH:mm:ss.S)";
    }

    /**
     * Returns Blob requests options - primarily sets concurrentRequestCount to
     * number of available cores
     *
     * @return
     */
    protected BlobRequestOptions getBlobRequestOptions() {
        BlobRequestOptions options = new BlobRequestOptions();
        options.setConcurrentRequestCount(Runtime.getRuntime().availableProcessors());

        return options;
    }
}
