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

import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.service.model.ServiceData;
import hudson.model.Result;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.io.PrintWriter;

public abstract class StoragePluginService<T extends ServiceData> {

    /*
     * A random name for container name to test validity of storage account
     * details
     */
    protected static final String FP_SEPARATOR = ",";

    private T serviceData;

    protected StoragePluginService(T serviceData) {
        this.serviceData = serviceData;
    }

    public abstract int execute() throws WAStorageException;

    protected void setRunUnstable() {
        serviceData.getRun().setResult(Result.UNSTABLE);
    }

    protected void println(String message) {
        serviceData.getTaskListener().getLogger().println(message);
    }

    protected PrintWriter error(String message) {
        return serviceData.getTaskListener().error(message);
    }

    protected String getTime(long timeInMills) {
        return DurationFormatUtils.formatDuration(timeInMills, "HH:mm:ss.S")
                + " (HH:mm:ss.S)";
    }

    public T getServiceData() {
        return serviceData;
    }

    public void setServiceData(T serviceData) {
        this.serviceData = serviceData;
    }
}
