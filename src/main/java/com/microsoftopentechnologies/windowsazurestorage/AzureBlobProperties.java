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
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.io.InputStream;

public class AzureBlobProperties implements Describable<AzureBlobProperties> {

    private String cacheControl;
    private String contentEncoding;
    private String contentLanguage;
    private String contentType;
    private boolean detectContentType = true;

    public AzureBlobProperties(
            final String cacheControl,
            final String contentEncoding,
            final String contentLanguage,
            final String contentType,
            final boolean detectContentType
    ) {
        this.cacheControl = Util.fixEmpty(cacheControl);
        this.contentEncoding = Util.fixEmpty(contentEncoding);
        this.contentLanguage = Util.fixEmpty(contentLanguage);
        this.contentType = Util.fixEmpty(contentType);
        this.detectContentType = detectContentType;
    }

    @DataBoundConstructor
    public AzureBlobProperties() {
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

    @DataBoundSetter
    public void setCacheControl(String cacheControl) {
        this.cacheControl = Util.fixEmpty(cacheControl);
    }

    @DataBoundSetter
    public void setContentEncoding(String contentEncoding) {
        this.contentEncoding = Util.fixEmpty(contentEncoding);
    }

    @DataBoundSetter
    public void setContentLanguage(String contentLanguage) {
        this.contentLanguage = Util.fixEmpty(contentLanguage);
    }

    @DataBoundSetter
    public void setContentType(String contentType) {
        this.contentType = Util.fixEmpty(contentType);
    }

    @DataBoundSetter
    public void setDetectContentType(boolean detectContentType) {
        this.detectContentType = detectContentType;
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
            if (file.getName().toLowerCase().endsWith(".js")) {
                // Tika has a shortcoming not able to properly identify JavaScript files, determine type by extension
                // rather than Tika for those.
                return "application/javascript";
            } else {
                return tika.detect(stream, file.getName());
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Descriptor<AzureBlobProperties> getDescriptor() {
        Jenkins instance = Jenkins.getInstance();
        return instance.getDescriptor(getClass());
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<AzureBlobProperties> {

    }
}
