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
package com.microsoftopentechnologies.windowsazurestorage;

import com.microsoft.azure.storage.blob.BlobProperties;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

public class AzureBlobProperties implements Describable<AzureBlobProperties> {

    private final String cacheControl;
    private final String contentEncoding;
    private final String contentLanguage;
    private final String contentType;

    @DataBoundConstructor
    public AzureBlobProperties(
        String cacheControl,
        String contentEncoding,
        String contentLanguage,
        String contentType
    ) {
        this.cacheControl = cacheControl;
        this.contentEncoding = contentEncoding;
        this.contentLanguage = contentLanguage;
        this.contentType = contentType;
    }

    public String getCacheControl() {
        return cacheControl;
    }

    public String getContentEncoding() {
        return contentEncoding;
    }

    public String getContentLanguage() {
        return contentLanguage;
    }

    public String getContentType() {
        return contentType;
    }

    public void configure(CloudBlob blob) {
        BlobProperties props = blob.getProperties();
        props.setCacheControl(cacheControl);
        props.setContentEncoding(contentEncoding);
        props.setContentLanguage(contentLanguage);
        props.setContentType(contentType);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Descriptor<AzureBlobProperties> getDescriptor() {
        Jenkins instance = Jenkins.getInstance();
        if (instance == null) {
            return null;
        } else {
            return instance.getDescriptor(getClass());
        }
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<AzureBlobProperties> {

    }
}
