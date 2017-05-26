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
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureCredentials;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;
import com.microsoftopentechnologies.windowsazurestorage.service.DownloadFromBuildService;
import com.microsoftopentechnologies.windowsazurestorage.service.DownloadFromContainerService;
import com.microsoftopentechnologies.windowsazurestorage.service.StoragePluginService;
import com.microsoftopentechnologies.windowsazurestorage.service.model.BuilderServiceData;
import hudson.*;
import hudson.model.*;
import hudson.plugins.copyartifact.BuildSelector;
import hudson.plugins.copyartifact.StatusBuildSelector;
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

import java.io.IOException;
import java.util.Collections;

public class AzureStorageBuilder extends Builder implements SimpleBuildStep {
    final static String CONTAINER = "container";

    private final transient String storageAccName;
    private final String downloadType;
    private boolean deleteFromAzureAfterDownload;
    private String storageCredentialId;
    private String containerName = "";
    private String includeFilesPattern = "";
    private String excludeFilesPattern = "";
    private String downloadDirLoc = "";
    private boolean flattenDirectories;
    private boolean includeArchiveZips;
    private BuildSelector buildSelector;
    private String projectName = "";

    private transient AzureCredentials.StorageAccountCredential storageCreds;

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

    public boolean isDeleteFromAzureAfterDownload() {
        return deleteFromAzureAfterDownload;
    }

    @DataBoundSetter
    public void setDeleteFromAzureAfterDownload(boolean deleteFromAzureAfterDownload) {
        this.deleteFromAzureAfterDownload = deleteFromAzureAfterDownload;
    }

    public static class DownloadType {

        public final String type;
        private String containerName = "";
        private String projectName = "";
        private BuildSelector buildSelector = new StatusBuildSelector(true);

        @DataBoundConstructor
        public DownloadType(final String value) {
            this.type = value;
        }

        public String getContainerName() {
            return containerName;
        }

        @DataBoundSetter
        public void setContainerName(String containerName) {
            this.containerName = containerName;
        }

        public String getProjectName() {
            return projectName;
        }

        @DataBoundSetter
        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }
        
        public BuildSelector getBuildSelector() {
            return buildSelector;
        }

        @DataBoundSetter
        public void setBuildSelector(BuildSelector buildSelector) {
            this.buildSelector = buildSelector;
        }
    }

    @Deprecated
    public AzureStorageBuilder(
            final String strAccName,
            final String storageCredentialId,
            final DownloadType downloadType,
            final String includeFilesPattern,
            final String excludeFilesPattern,
            final String downloadDirLoc,
            final boolean flattenDirectories,
            final boolean deleteFromAzureAfterDownload,
            final boolean includeArchiveZips) {
        this.storageCredentialId = storageCredentialId;
        this.storageCreds = AzureCredentials.getStorageCreds(this.storageCredentialId, strAccName);
        this.storageAccName = storageCreds.getStorageAccountName();
        this.downloadType = downloadType.type;
        this.containerName = downloadType.containerName;
        this.includeFilesPattern = includeFilesPattern;
        this.excludeFilesPattern = excludeFilesPattern;
        this.downloadDirLoc = downloadDirLoc;
        this.flattenDirectories = flattenDirectories;
        this.deleteFromAzureAfterDownload = deleteFromAzureAfterDownload;
        this.includeArchiveZips = includeArchiveZips;
        this.projectName = downloadType.projectName;
        this.buildSelector = downloadType.buildSelector;
    }

    @DataBoundConstructor
    public AzureStorageBuilder(
            final String storageCredentialId,
            final DownloadType downloadType) {
        this.storageCredentialId = storageCredentialId;
        this.storageCreds = AzureCredentials.getStorageAccountCredential(storageCredentialId);
        this.storageAccName = storageCreds.getStorageAccountName();
        this.downloadType = downloadType.type;
        this.containerName = downloadType.containerName;
        this.projectName = downloadType.projectName;
        this.buildSelector = downloadType.buildSelector;
    }

    public BuildSelector getBuildSelector() {
        return buildSelector;
    }

    public String getStorageAccName() {
        return storageAccName;
    }

    public String getDownloadType() {
        return downloadType;
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

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public String getStorageCredentialId() {
        if (this.storageCredentialId == null && this.storageAccName != null)
            return AzureCredentials.getStorageCreds(null, this.storageAccName).getId();
        return storageCredentialId;
    }

    /**
     * @param run       environment of build
     * @param workspace filepath
     * @param launcher  env var for remote builds
     * @param listener  logging
     */
    @Override
    public synchronized void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) {
        // Get storage account
        StorageAccountInfo storageAccountInfo = AzureCredentials.convertToStorageAccountInfo(AzureCredentials.getStorageCreds(this.storageCredentialId, this.storageAccName));
        try {
            // Validate input data and resolve storage account
            validateData(run, listener, storageAccountInfo);

            final EnvVars envVars = run.getEnvironment(listener);
            if (envVars == null) {
                throw new IllegalStateException("Failed to capture information about running environment.");
            }

            final BuilderServiceData builderServiceData = new BuilderServiceData(run, workspace, launcher, listener, storageAccountInfo);

            // Resolve include patterns
            String expIncludePattern = Util.replaceMacro(includeFilesPattern, envVars);
            // If the include is empty, make **/*
            if (StringUtils.isBlank(expIncludePattern)) {
                expIncludePattern = "**/*";
            }

            // Resolve exclude patterns
            String expExcludePattern = Util.replaceMacro(excludeFilesPattern, envVars);
            // Exclude archive.zip by default.
            if (!includeArchiveZips) {
                if (expExcludePattern != null) {
                    expExcludePattern += ",archive.zip";
                } else {
                    expExcludePattern = "archive.zip";
                }
            }

            // initialize service data
            builderServiceData.setIncludeFilesPattern(expIncludePattern);
            builderServiceData.setExcludeFilesPattern(expExcludePattern);
            builderServiceData.setDownloadDirLoc(Util.replaceMacro(downloadDirLoc, envVars));
            builderServiceData.setContainerName(Util.replaceMacro(containerName, envVars));
            builderServiceData.setFlattenDirectories(flattenDirectories);
            builderServiceData.setDeleteFromAzureAfterDownload(deleteFromAzureAfterDownload);
            builderServiceData.setDownloadType(downloadType);
            builderServiceData.setProjectName(Util.replaceMacro(projectName, envVars));

            final StoragePluginService downloadBlobService = getBuilderService(builderServiceData);
            int filesDownloaded = downloadBlobService.execute();

            if (filesDownloaded == 0) { // Mark build unstable if no files are
                // downloaded
                listener.getLogger().println(Messages.AzureStorageBuilder_nofiles_downloaded());
                run.setResult(Result.UNSTABLE);
            } else {
                listener.getLogger().println(Messages.AzureStorageBuilder_files_downloaded_count(filesDownloaded));
            }
        } catch (IOException | InterruptedException | WAStorageException e) {
            e.printStackTrace(listener.error(Messages.AzureStorageBuilder_download_err(storageAccountInfo.getStorageAccName())));
            run.setResult(Result.UNSTABLE);
        }
    }

    private StoragePluginService getBuilderService(final BuilderServiceData data) {
        if (isDownloadFromContainer())
            return new DownloadFromContainerService(data);
        return new DownloadFromBuildService(data);
    }

    private boolean isDownloadFromContainer() {
        return StringUtils.isBlank(this.downloadType) || this.downloadDirLoc.equals(CONTAINER);
    }

    private void validateData(final Run<?, ?> run,
                              final TaskListener listener,
                              final StorageAccountInfo storageAccountInfo) {

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
            StorageAccountInfo[] StorageAccounts = getStorageAccounts();

            if (StorageAccounts != null) {
                for (StorageAccountInfo storageAccount : StorageAccounts) {
                    m.add(storageAccount.getStorageAccName());
                }
            }
            return m;
        }

        public ListBoxModel doFillStorageCredentialIdItems(@AncestorInPath Item owner) {

            return new StandardListBoxModel().withAll(CredentialsProvider.lookupCredentials(AzureCredentials.class, owner, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()));
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
            AzureStoragePublisher.WAStorageDescriptor publisherDescriptor = Utils.getJenkinsInstance().getDescriptorByType(
                    AzureStoragePublisher.WAStorageDescriptor.class);

            StorageAccountInfo[] sa = publisherDescriptor.getStorageAccounts();

            return sa;
        }

        /**
         * Returns storage account object
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
            StorageAccountInfo[] StorageAccounts = getStorageAccounts();

            if (StorageAccounts != null) {
                for (StorageAccountInfo sa : StorageAccounts) {
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
