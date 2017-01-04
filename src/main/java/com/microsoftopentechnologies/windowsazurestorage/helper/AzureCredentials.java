/*
 Copyright 2016 Microsoft, Inc.
 
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
package com.microsoftopentechnologies.windowsazurestorage.helper;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.microsoftopentechnologies.windowsazurestorage.Messages;
import com.microsoftopentechnologies.windowsazurestorage.Messages;
import com.microsoftopentechnologies.windowsazurestorage.WAStorageClient;
import com.microsoftopentechnologies.windowsazurestorage.WAStorageClient;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.*;
import hudson.Extension;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.Secret;
import java.util.Collections;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 * @author arroyc
 */
public class AzureCredentials extends BaseStandardCredentials {

    public static class StorageAccountCredential implements java.io.Serializable {

        public final String storageAccountName;
        public final Secret storageAccountKey;
        public final String blobEndpointURL;

        public StorageAccountCredential(
                String storageAccountName,
                String storageKey,
                String endpointURL) {
            this.storageAccountName = storageAccountName;
            this.storageAccountKey = Secret.fromString(storageKey);
            this.blobEndpointURL = StringUtils.isBlank(endpointURL)
                    ? Constants.DEFAULT_ENDPOINT_URL
                    : endpointURL;
        }

        public StorageAccountCredential() {
            this.storageAccountName = "";
            this.storageAccountKey = Secret.fromString("");
            this.blobEndpointURL = Constants.DEFAULT_ENDPOINT_URL;
        }

        public boolean isValidStorageCredential() throws WAStorageException {
            if (StringUtils.isBlank(storageAccountName)) {
                throw new WAStorageException("Error: Storage Account Name is missing");
            }

            if (StringUtils.isBlank(storageAccountKey.getPlainText())) {
                throw new WAStorageException("Error: Storage Account Key is missing");
            }

            if (StringUtils.isBlank(blobEndpointURL)) {
                throw new WAStorageException("Error: bloblEndpointURL is invalid or missing");
            }

            return true;
        }

        public String getStorageAccountName() {
            return storageAccountName;
        }

        public String getstorageAccountKey() {
            return storageAccountKey.getPlainText();
        }

        public String getEndpointURL() {
            return blobEndpointURL;
        }

    }

    public final StorageAccountCredential storageData;

    @DataBoundConstructor
    public AzureCredentials(
            CredentialsScope scope,
            String id,
            String description,
            String storageAccountName,
            String storageKey,
            String blobEndpointURL
    ) {
        super(scope, id, description);
        storageData = new StorageAccountCredential(storageAccountName, storageKey, blobEndpointURL);

    }

    public static AzureCredentials.StorageAccountCredential getStorageAccountCredential(final String storageCredentialId) {
        AzureCredentials creds = CredentialsMatchers.firstOrNull(CredentialsProvider.lookupCredentials(AzureCredentials.class, Jenkins.getInstance(), ACL.SYSTEM, Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.withId(storageCredentialId));
        if (creds == null) {
            return new AzureCredentials.StorageAccountCredential();
        }
        return creds.storageData;
    }

    public String getStorageAccountName() {
        return storageData.storageAccountName;
    }

    public String getstorageAccountKey() {
        return storageData.storageAccountKey.getPlainText();
    }

    public String getEndpointURL() {
        return storageData.blobEndpointURL;
    }

    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

        @Override
        public String getDisplayName() {
            return "Microsoft Azure Storage";
        }

        public String getDefaultBlobURL() {
            return Constants.DEFAULT_ENDPOINT_URL;
        }

        public FormValidation doVerifyConfiguration(
                @QueryParameter String storageAccountName,
                @QueryParameter String storageKey,
                @QueryParameter String blobEndpointURL) {

            try {
                StorageAccountInfo storageAccount = new StorageAccountInfo(storageAccountName, storageKey, blobEndpointURL);
                WAStorageClient.validateStorageAccount(storageAccount);
                AzureCredentials.StorageAccountCredential storageCreds = new AzureCredentials.StorageAccountCredential(storageAccountName, storageKey, blobEndpointURL);
            } catch (Exception e) {
                return FormValidation.error(e.getMessage());
            }

            return FormValidation.ok(Messages.WAStoragePublisher_SA_val());
        }

    }

    public static StorageAccountInfo convertToStorageAccountInfo(StorageAccountCredential storageCreds) {
        StorageAccountInfo storageAccount = new StorageAccountInfo(storageCreds.getStorageAccountName(),
                storageCreds.getstorageAccountKey(), storageCreds.getEndpointURL());
        return storageAccount;
    }

}
