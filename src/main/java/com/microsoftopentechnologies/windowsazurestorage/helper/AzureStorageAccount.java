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
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import hudson.Extension;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author arroyc
 */
public class AzureStorageAccount extends BaseStandardCredentials {

    // used to detect the emulator
    private static final List<String> LOCAL_ADDRESSES = Arrays.asList("127.0.0.1", "localhost", "::1", "0.0.0.0");

    public static class StorageAccountCredential implements java.io.Serializable {

        private final String storageAccountName;
        private final Secret storageAccountKey;
        private final String blobEndpointURL;

        public StorageAccountCredential(
                String storageAccountName,
                String storageKey,
                String endpointURL) {
            this.storageAccountName = storageAccountName;
            this.storageAccountKey = Secret.fromString(storageKey);
            String url = endpointURL;
            if (StringUtils.isBlank(endpointURL)) {
                url = Constants.DEF_BLOB_URL;
            }
            this.blobEndpointURL = joinAccountNameAndEndpoint(storageAccountName, url);
        }

        /**
         * The old SDK worked with 'endpoint suffixes' in the form http(s)://blob.core.windows.net.
         * New SDK uses endpoints: https://my-account-name.blob.core.windows.net.
         *
         * UI still stores the suffix so we need to join them, unless it's already added or the emulator is in use
         */
        @SuppressWarnings("HttpUrlsUsage")
        private static String joinAccountNameAndEndpoint(String accountName, String urlSuffix) {
            if (urlSuffix.contains(accountName) || LOCAL_ADDRESSES.stream().anyMatch(urlSuffix::contains)) {
                return urlSuffix;
            }

            return urlSuffix
                    .replace("http://", "https://")
                    .replace("https://", String.format("https://%s.", accountName));
        }

        public StorageAccountCredential() {
            this.storageAccountName = "";
            this.storageAccountKey = Secret.fromString("");
            this.blobEndpointURL = Constants.DEF_BLOB_URL;
        }

        public boolean isValidStorageCredential() throws WAStorageException {
            if (StringUtils.isBlank(storageAccountName)) {
                throw new WAStorageException("Error: Storage Account Name is missing");
            }

            if (StringUtils.isBlank(storageAccountKey.getPlainText())) {
                throw new WAStorageException("Error: Storage Account Key is missing");
            }

            if (StringUtils.isBlank(blobEndpointURL)) {
                throw new WAStorageException("Error: blobEndpointURL is invalid or missing");
            }

            return true;
        }

        public String getStorageAccountName() {
            return storageAccountName;
        }

        public String getStorageAccountKey() {
            return storageAccountKey.getPlainText();
        }

        protected Secret getSecureKey() {
            return storageAccountKey;
        }

        public String getEndpointURL() {
            // joined in getter as constructor isn't called when reading saved configuration
            return joinAccountNameAndEndpoint(storageAccountName, blobEndpointURL);
        }

        public String getId() {
            String storageId = this.getStorageAccountName().concat(this.getStorageAccountKey());
            return Utils.getMD5(storageId);
        }

    }

    private final StorageAccountCredential storageData;

    @DataBoundConstructor
    public AzureStorageAccount(
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

    public static AzureStorageAccount.StorageAccountCredential getStorageAccountCredential(Item owner,
                                                                                           String storageCredentialId) {
        if (StringUtils.isBlank(storageCredentialId)) {
            return null;
        }
        AzureStorageAccount creds = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        AzureStorageAccount.class,
                        owner,
                        ACL.SYSTEM,
                        Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.withId(storageCredentialId));
        if (creds == null) {
            return new AzureStorageAccount.StorageAccountCredential();
        }
        return creds.storageData;
    }

    /**
     * @deprecated Use {@link #getStorageAccountCredential(Item, String)}.
     */
    @Deprecated
    public static AzureStorageAccount.StorageAccountCredential getStorageAccountCredential(String storageCredentialId) {
        return getStorageAccountCredential(null, storageCredentialId);
    }

    /**
     * @deprecated Use {@link #getStorageAccountCredential(Item, String)}.
     */
    @Deprecated
    public static AzureStorageAccount.StorageAccountCredential getStorageCreds(String credentialsId,
                                                                               String storageAccName) {
        try {
            if (credentialsId != null) {
                AzureStorageAccount.StorageAccountCredential credentials =
                        AzureStorageAccount.getStorageAccountCredential(null, credentialsId);
                if (credentials != null) {
                    return credentials;
                }
            } else {
                List<AzureStorageAccount> allCreds = CredentialsProvider.lookupCredentials(
                        AzureStorageAccount.class,
                        Jenkins.getInstance(),
                        ACL.SYSTEM,
                        Collections.<DomainRequirement>emptyList());
                for (AzureStorageAccount cred : allCreds) {
                    if (storageAccName.equals(cred.getStorageAccountName())) {
                        return cred.getStorageCred();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public String getStorageAccountName() {
        return storageData.storageAccountName;
    }

    public String getPlainStorageKey() {
        return storageData.storageAccountKey.getPlainText();
    }

    public String getStorageKey() {
        return storageData.storageAccountKey.getEncryptedValue();
    }

    public String getBlobEndpointURL() {
        return storageData.getEndpointURL();
    }

    public StorageAccountCredential getStorageCred() {
        return storageData;
    }

    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

        @Override
        public String getDisplayName() {
            return "Azure Storage";
        }

        public String getDefaultBlobURL() {
            return Constants.DEF_BLOB_URL;
        }

        public FormValidation doVerifyConfiguration(
                @QueryParameter String storageAccountName,
                @QueryParameter Secret storageKey,
                @QueryParameter String blobEndpointURL) {

            try {
                StorageAccountInfo storageAccount = new StorageAccountInfo(
                        storageAccountName, storageKey.getPlainText(), blobEndpointURL);
                AzureUtils.validateStorageAccount(storageAccount);
            } catch (Exception e) {
                return FormValidation.error(e, e.getMessage());
            }

            return FormValidation.ok(Messages.WAStoragePublisher_SA_val());
        }

    }

    public static StorageAccountInfo convertToStorageAccountInfo(StorageAccountCredential storageCreds) {
        return new StorageAccountInfo(storageCreds.getStorageAccountName(), storageCreds.getStorageAccountKey(),
                storageCreds.getEndpointURL());
    }

}
