/*
 Copyright 2014 Microsoft Open Technologies, Inc.

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

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureStorageAccount;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;
import com.microsoftopentechnologies.windowsazurestorage.service.DownloadFromBuildService;
import com.microsoftopentechnologies.windowsazurestorage.service.DownloadFromContainerService;
import com.microsoftopentechnologies.windowsazurestorage.service.DownloadFromFileService;
import com.microsoftopentechnologies.windowsazurestorage.service.StoragePluginService;
import com.microsoftopentechnologies.windowsazurestorage.service.model.DownloadServiceData;
import hudson.AbortException;
import hudson.DescriptorExtensionList;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.copyartifact.BuildSelector;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;
import java.util.Locale;

public class AzureStorageBuilder extends Builder implements SimpleBuildStep {
    public static final String DOWNLOAD_TYPE_CONTAINER = "container";
    public static final String DOWNLOAD_TYPE_PROJECT = "project";
    public static final String DOWNLOAD_TYPE_FILE_SHARE = "share";

    static final String CONTAINER = "container";

    private final String downloadType;
    private boolean deleteFromAzureAfterDownload;
    private String storageCredentialId;
    private String containerName = "";
    private String fileShare;
    private String includeFilesPattern = "";
    private String excludeFilesPattern = "";
    private String downloadDirLoc = "";
    private boolean flattenDirectories;
    private boolean includeArchiveZips;
    private BuildSelector buildSelector;
    private String projectName = "";

    private transient AzureStorageAccount.StorageAccountCredential storageCreds;

    @DataBoundConstructor
    public AzureStorageBuilder(
            String storageCredentialId,
            String downloadType) {
        this.storageCredentialId = storageCredentialId;
        this.downloadType = downloadType;
    }

    @DataBoundSetter
    public void setIncludeFilesPattern(String includeFilesPattern) {
        this.includeFilesPattern = includeFilesPattern;
    }

    @DataBoundSetter
    public void setExcludeFilesPattern(String excludeFilesPattern) {
        this.excludeFilesPattern = excludeFilesPattern;
    }

    @DataBoundSetter
    public void setDownloadDirLoc(String downloadDirLoc) {
        this.downloadDirLoc = downloadDirLoc;
    }

    @DataBoundSetter
    public void setFlattenDirectories(boolean flattenDirectories) {
        this.flattenDirectories = flattenDirectories;
    }

    @DataBoundSetter
    public void setIncludeArchiveZips(boolean includeArchiveZips) {
        this.includeArchiveZips = includeArchiveZips;
    }

    @DataBoundSetter
    public void setContainerName(String containerName) {
        if (getDownloadType().equals(DOWNLOAD_TYPE_CONTAINER)) {
            this.containerName = containerName;
        }
    }

    @DataBoundSetter
    public void setFileShare(String fileShare) {
        this.fileShare = fileShare;
    }

    @DataBoundSetter
    public void setBuildSelector(BuildSelector buildSelector) {
        if (getDownloadType().equals(DOWNLOAD_TYPE_PROJECT)) {
            this.buildSelector = buildSelector;
        }
    }

    @DataBoundSetter
    public void setProjectName(String projectName) {
        if (getDownloadType().equals(DOWNLOAD_TYPE_PROJECT)) {
            this.projectName = projectName;
        }
    }

    public boolean isDeleteFromAzureAfterDownload() {
        return deleteFromAzureAfterDownload;
    }

    @DataBoundSetter
    public void setDeleteFromAzureAfterDownload(boolean deleteFromAzureAfterDownload) {
        this.deleteFromAzureAfterDownload = deleteFromAzureAfterDownload;
    }

    public BuildSelector getBuildSelector() {
        return buildSelector;
    }

    /**
     * @deprecated use {@link #getStorageAccName(Item)}
     */
    @Deprecated
    public String getStorageAccName() {
        return getStorageAccName(null);
    }

    public String getStorageAccName(Item owner) {
        AzureStorageAccount.StorageAccountCredential credential = getStorageAccountCredential(owner);
        if (credential != null) {
            return credential.getStorageAccountName();
        }
        return null;
    }

    public String getDownloadType() {
        if (DOWNLOAD_TYPE_FILE_SHARE.equals(downloadType) || DOWNLOAD_TYPE_PROJECT.equals(downloadType)) {
            return downloadType;
        }
        return DOWNLOAD_TYPE_CONTAINER;
    }

    public String getContainerName() {
        return containerName;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getIncludeFilesPattern() {
        return includeFilesPattern;
    }

    public String getExcludeFilesPattern() {
        return excludeFilesPattern;
    }

    public String getDownloadDirLoc() {
        return downloadDirLoc;
    }

    public boolean isIncludeArchiveZips() {
        return includeArchiveZips;
    }

    public boolean isFlattenDirectories() {
        return flattenDirectories;
    }

    public String getFileShare() {
        return this.fileShare;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public String getStorageCredentialId() {
        return storageCredentialId;
    }

    public AzureStorageAccount.StorageAccountCredential getStorageAccountCredential(Item owner) {
        if (storageCreds == null) {
            storageCreds = AzureStorageAccount.getStorageAccountCredential(owner, getStorageCredentialId());
        }
        return storageCreds;
    }

    /**
     * @param run       environment of build
     * @param workspace filepath
     * @param launcher  env var for remote builds
     * @param listener  logging
     */
    @Override
    public synchronized void perform(
            @Nonnull Run<?, ?> run,
            @Nonnull FilePath workspace,
            @Nonnull Launcher launcher,
            @Nonnull TaskListener listener) throws IOException {
        AzureUtils.updateDefaultProxy();
        // Get storage account
        AzureStorageAccount.StorageAccountCredential credential = getStorageAccountCredential(run.getParent());
        if (credential == null) {
            throw new AbortException(String.format("Cannot find storage account credentials with ID: '%s'",
                    getStorageCredentialId()));
        }

        StorageAccountInfo storageAccountInfo = AzureStorageAccount.convertToStorageAccountInfo(credential);
        try {

            // Validate input data and resolve storage account
            validateData(run, listener, storageAccountInfo);

            final EnvVars envVars = run.getEnvironment(listener);

            final DownloadServiceData builderServiceData =
                    new DownloadServiceData(run, workspace, launcher, listener, storageAccountInfo);

            // Resolve include patterns
            String expIncludePattern = Utils.replaceMacro(includeFilesPattern, envVars);
            // If the include is empty, make **/*
            if (StringUtils.isBlank(expIncludePattern)) {
                expIncludePattern = "**/*";
            }

            // Resolve exclude patterns
            String expExcludePattern = Utils.replaceMacro(excludeFilesPattern, envVars);
            // Exclude archive.zip by default.
            if (!includeArchiveZips) {
                if (expExcludePattern != null) {
                    expExcludePattern += ",archive.zip";
                } else {
                    expExcludePattern = "archive.zip";
                }
            }

            // Resolve container name or share name
            final String expContainerName = Utils.replaceMacro(Util.fixNull(containerName), envVars, Locale.ENGLISH);
            final String expShareName = Utils.replaceMacro(Util.fixNull(fileShare), envVars, Locale.ENGLISH);

            // initialize service data
            builderServiceData.setIncludeFilesPattern(expIncludePattern);
            builderServiceData.setExcludeFilesPattern(expExcludePattern);
            builderServiceData.setDownloadDirLoc(Util.replaceMacro(downloadDirLoc, envVars));
            builderServiceData.setContainerName(expContainerName);
            builderServiceData.setFileShare(expShareName);
            builderServiceData.setFlattenDirectories(flattenDirectories);
            builderServiceData.setDeleteFromAzureAfterDownload(deleteFromAzureAfterDownload);
            builderServiceData.setDownloadType(getDownloadType());
            builderServiceData.setProjectName(Util.replaceMacro(projectName, envVars));
            builderServiceData.setBuildSelector(buildSelector);

            final StoragePluginService downloadService = getDownloadService(builderServiceData);
            int filesDownloaded = downloadService.execute();

            if (filesDownloaded == 0) {
                listener.getLogger().println(Messages.AzureStorageBuilder_nofiles_downloaded());
            } else {
                listener.getLogger().println(Messages.AzureStorageBuilder_files_downloaded_count(filesDownloaded));
            }
        } catch (IOException | InterruptedException | WAStorageException e) {
            e.printStackTrace(
                    listener.error(Messages.AzureStorageBuilder_download_err(storageAccountInfo.getStorageAccName())));
            run.setResult(Result.UNSTABLE);
        }
    }

    private StoragePluginService getDownloadService(DownloadServiceData data) {
        switch (getDownloadType()) {
            case DOWNLOAD_TYPE_FILE_SHARE:
                return new DownloadFromFileService(data);
            case DOWNLOAD_TYPE_PROJECT:
                return new DownloadFromBuildService(data);
            default:
                return new DownloadFromContainerService(data);
        }
    }

    private void validateData(Run<?, ?> run,
                              TaskListener listener,
                              StorageAccountInfo storageAccountInfo) {

        // No need to download artifacts if build failed
        if (run.getResult() == Result.FAILURE) {
            listener.getLogger().println(
                    Messages.AzureStorageBuilder_build_failed_err());
        }

        if (storageAccountInfo == null) {
            listener.getLogger().println(
                    Messages.WAStoragePublisher_storage_account_err());
            run.setResult(Result.UNSTABLE);
        }

        // Check if storage account credentials are valid
        try {
            AzureUtils.validateStorageAccount(storageAccountInfo);
        } catch (Exception e) {
            listener.getLogger().println(Messages.Client_SA_val_fail());
            listener.getLogger().println(storageAccountInfo.getStorageAccName());
            listener.getLogger().println(storageAccountInfo.getBlobEndPointURL());
            run.setResult(Result.UNSTABLE);
        }
    }

    @Override
    public AzureStorageBuilderDesc getDescriptor() {
        // see Descriptor javadoc for more about what a descriptor is.
        return (AzureStorageBuilderDesc) super.getDescriptor();
    }

    @Extension
    @Symbol("azureDownload")
    public static final class AzureStorageBuilderDesc extends
            BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(
                @SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return true;
        }

        public AzureStorageBuilderDesc() {
            super();
            load();
        }

        public ListBoxModel doFillStorageAccNameItems() {
            ListBoxModel m = new ListBoxModel();
            StorageAccountInfo[] storageAccounts = getStorageAccounts();

            if (storageAccounts != null) {
                for (StorageAccountInfo storageAccount : storageAccounts) {
                    m.add(storageAccount.getStorageAccName());
                }
            }
            return m;
        }

        public ListBoxModel doFillStorageCredentialIdItems(@AncestorInPath Item owner) {

            return new StandardListBoxModel().withAll(
                    CredentialsProvider.lookupCredentials(
                            AzureStorageAccount.class, owner, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()));
        }

        public AutoCompletionCandidates doAutoCompleteProjectName(@QueryParameter String value) {
            AutoCompletionCandidates projectList = new AutoCompletionCandidates();
            for (AbstractProject<?, ?> project : Utils.getJenkinsInstance().getItems(AbstractProject.class)) {
                if (project.getName().toLowerCase().startsWith(value.toLowerCase())) {
                    projectList.add(project.getName());
                }
            }
            return projectList;
        }

        public boolean configure(StaplerRequest req, JSONObject formData)
                throws FormException {
            save();
            return super.configure(req, formData);
        }

        public String getDisplayName() {
            return Messages.AzureStorageBuilder_displayName();
        }

        public StorageAccountInfo[] getStorageAccounts() {
            WAStoragePublisher.WAStorageDescriptor publisherDescriptor =
                    Utils.getJenkinsInstance().getDescriptorByType(WAStoragePublisher.WAStorageDescriptor.class);

            return publisherDescriptor.getStorageAccounts();
        }

        /**
         * Returns storage account object.
         *
         * @param storageAccountName
         * @return StorageAccount
         */
        public StorageAccountInfo getStorageAccount(String storageAccountName) {
            if ((storageAccountName == null)
                    || (storageAccountName.trim().length() == 0)) {
                return null;
            }

            StorageAccountInfo storageAcc = null;
            StorageAccountInfo[] storageAccounts = getStorageAccounts();

            if (storageAccounts != null) {
                for (StorageAccountInfo sa : storageAccounts) {
                    if (sa.getStorageAccName().equals(storageAccountName)) {
                        storageAcc = sa;

                        storageAcc.setBlobEndPointURL(Utils.getBlobEP(
                                storageAcc.getBlobEndPointURL()));
                        break;
                    }
                }
            }
            return storageAcc;
        }

        public DescriptorExtensionList<BuildSelector, Descriptor<BuildSelector>> getAvailableBuildSelectorList() {
            return DescriptorExtensionList.createDescriptorList(Jenkins.getInstance(), BuildSelector.class);
        }

    }
}
