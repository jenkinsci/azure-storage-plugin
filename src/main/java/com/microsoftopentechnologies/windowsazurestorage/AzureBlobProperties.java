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
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.apache.tika.Tika;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.InputStream;

public class AzureBlobProperties implements Describable<AzureBlobProperties> {

    private final String cacheControl;
    private final String contentEncoding;
    private final String contentLanguage;
    private final String contentType;
    private final boolean detectContentType;

    @DataBoundConstructor
    public AzureBlobProperties(
        String cacheControl,
        String contentEncoding,
        String contentLanguage,
        String contentType,
        boolean detectContentType
    ) {
        this.cacheControl = cacheControl;
        this.contentEncoding = contentEncoding;
        this.contentLanguage = contentLanguage;
        this.contentType = contentType;
        this.detectContentType = detectContentType;
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

    public boolean getDetectContentType() {
        return detectContentType;
    }

    public void configure(CloudBlob blob, FilePath src, EnvVars env) throws InterruptedException, IOException {
        BlobProperties props = blob.getProperties();
        props.setCacheControl(Util.replaceMacro(cacheControl, env));
        props.setContentEncoding(Util.replaceMacro(contentEncoding, env));
        props.setContentLanguage(Util.replaceMacro(contentLanguage, env));

        final String resolvedContentType = Util.replaceMacro(contentType, env);
        if (StringUtils.isNotBlank(resolvedContentType)) {
            props.setContentType(resolvedContentType);
        } else if (detectContentType) {
            props.setContentType(detectContentType(src));
        }
    }

    private String detectContentType(FilePath file) throws InterruptedException, IOException {
        Tika tika = new Tika();
        try (InputStream stream = file.read()) {
            return tika.detect(stream, file.getName());
        }
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
