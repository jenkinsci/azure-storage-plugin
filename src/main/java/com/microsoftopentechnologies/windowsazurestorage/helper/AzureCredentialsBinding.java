package com.microsoftopentechnologies.windowsazurestorage.helper;

import com.microsoftopentechnologies.windowsazurestorage.Messages;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;

public class AzureCredentialsBinding extends MultiBinding<AzureStorageAccount> {
    public static final String DEFAULT_STORAGE_ACCOUNT_NAME = "AZURE_STORAGE_ACCOUNT_NAME";
    public static final String DEFAULT_STORAGE_ACCOUNT_KEY = "AZURE_STORAGE_ACCOUNT_KEY";
    public static final String DEFAULT_BLOB_ENDPOINT_URL = "AZURE_BLOB_ENDPOINT_URL";
    public static final String DEFAULT_CDN_ENDPOINT_URL = "AZURE_CDN_ENDPOINT_URL";

    private String storageAccountNameVariable;
    private String storageAccountKeyVariable;
    private String blobEndpointUrlVariable;
    private String cdnEndpointUrlVariable;

    @DataBoundConstructor
    public AzureCredentialsBinding(String credentialsId) {
        super(credentialsId);
    }

    @DataBoundSetter
    public void setStorageAccountNameVariable(String storageAccountNameVariable) {
        this.storageAccountNameVariable = storageAccountNameVariable;
    }

    @DataBoundSetter
    public void setStorageAccountKeyVariable(String storageAccountKeyVariable) {
        this.storageAccountKeyVariable = storageAccountKeyVariable;
    }

    @DataBoundSetter
    public void setBlobEndpointUrlVariable(String blobEndpointUrlVariable) {
        this.blobEndpointUrlVariable = blobEndpointUrlVariable;
    }

    @DataBoundSetter
    public void setCdnEndpointUrlVariable(String cdnEndpointUrlVariable) {
        this.cdnEndpointUrlVariable = cdnEndpointUrlVariable;
    }

    public String getStorageAccountNameVariable() {
        if (!StringUtils.isBlank(storageAccountNameVariable)) {
            return storageAccountNameVariable;
        }
        return DEFAULT_STORAGE_ACCOUNT_NAME;
    }

    public String getStorageAccountKeyVariable() {
        if (!StringUtils.isBlank(storageAccountKeyVariable)) {
            return storageAccountKeyVariable;
        }
        return DEFAULT_STORAGE_ACCOUNT_KEY;
    }

    public String getBlobEndpointUrlVariable() {
        if (!StringUtils.isBlank(blobEndpointUrlVariable)) {
            return blobEndpointUrlVariable;
        }
        return DEFAULT_BLOB_ENDPOINT_URL;
    }

    public String getCdnEndpointUrlVariable() {
        if (!StringUtils.isBlank(cdnEndpointUrlVariable)) {
            return cdnEndpointUrlVariable;
        }
        return DEFAULT_CDN_ENDPOINT_URL;
    }

    @Override
    protected Class<AzureStorageAccount> type() {
        return AzureStorageAccount.class;
    }

    @Override
    public MultiEnvironment bind(@NonNull Run<?, ?> build,
                                 @Nullable FilePath workspace,
                                 @Nullable Launcher launcher,
                                 @NonNull TaskListener listener)
            throws IOException {
        AzureStorageAccount credentials = getCredentials(build);
        Map<String, String> variableMap = new HashMap<>();
        variableMap.put(getStorageAccountNameVariable(), credentials.getStorageAccountName());
        variableMap.put(getStorageAccountKeyVariable(), credentials.getPlainStorageKey());
        variableMap.put(getBlobEndpointUrlVariable(), credentials.getBlobEndpointURL());
        if (credentials.getCdnEndpointURL() != null) {
            variableMap.put(getCdnEndpointUrlVariable(), credentials.getCdnEndpointURL());
        }
        return new MultiEnvironment(variableMap);
    }

    @Override
    public Set<String> variables() {
        return new HashSet<>(Arrays.asList(
                getStorageAccountNameVariable(),
                getStorageAccountKeyVariable(),
                getBlobEndpointUrlVariable()
        ));
    }

    @Symbol("azureStorage")
    @Extension
    public static class DescriptorImpl extends BindingDescriptor<AzureStorageAccount> {
        @Override
        protected Class<AzureStorageAccount> type() {
            return AzureStorageAccount.class;
        }

        @Override
        public boolean requiresWorkspace() {
            return false;
        }

        @Override
        public String getDisplayName() {
            return Messages.AzureStorage_credentials_binding_display_name();
        }

        public static String getDefaultStorageAccountNameVariable() {
            return DEFAULT_STORAGE_ACCOUNT_NAME;
        }

        public static String getDefaultStorageAccountKeyVariable() {
            return DEFAULT_STORAGE_ACCOUNT_KEY;
        }

        public static String getDefaultBlobEndpointUrlVariable() {
            return DEFAULT_BLOB_ENDPOINT_URL;
        }

        public static String getDefaultCdnEndpointUrlVariable() {
            return DEFAULT_CDN_ENDPOINT_URL;
        }
    }
}
