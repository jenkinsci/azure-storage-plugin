/*
 Copyright 2013 Microsoft Open Technologies, Inc.

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
package com.microsoftopentechnologies.windowsazurestorage.beans;

import org.kohsuke.stapler.DataBoundConstructor;

public class StorageAccountInfo {

    /**
     * Azure storage account name.
     */
    private String storageAccName;

    /**
     * Azure storage account primary access key.
     */
    private String storageAccountKey;

    /**
     * Azure storage blob endpoint URL.
     */
    private String blobEndPointURL;

    /**
     * Azure storage CDN endpoint URL.
     */
    private String cdnEndPointURL;

    @DataBoundConstructor
    public StorageAccountInfo(String storageAccName, String storageAccountKey,
                              String blobEndPointURL, String cdnEndPointURL) {
        this.storageAccName = storageAccName;
        this.blobEndPointURL = blobEndPointURL;
        this.storageAccountKey = storageAccountKey;
        this.cdnEndPointURL = cdnEndPointURL;
    }

    public String getStorageAccName() {
        return storageAccName;
    }

    public void setStorageAccName(String storageAccName) {
        this.storageAccName = storageAccName;
    }

    public String getStorageAccountKey() {
        return storageAccountKey;
    }

    public void setStorageAccountKey(String storageAccountKey) {
        this.storageAccountKey = storageAccountKey;
    }

    public String getBlobEndPointURL() {
        return blobEndPointURL;
    }

    public void setBlobEndPointURL(String blobEndPointURL) {
        this.blobEndPointURL = blobEndPointURL;
    }

    public String getCdnEndPointURL() {
        return cdnEndPointURL;
    }

    public void setCdnEndPointURL(String cdnEndPointURL) {
        this.cdnEndPointURL = cdnEndPointURL;
    }
}
