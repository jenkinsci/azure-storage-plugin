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
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;
import hudson.*;
import hudson.matrix.MatrixBuild;
import hudson.model.*;
import hudson.plugins.copyartifact.BuildFilter;
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
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AzureStorageBuilder extends Builder implements SimpleBuildStep {

    private final transient String storageAccName;
    private final String downloadType;
    private final String containerName;
    private final String includeFilesPattern;
    private final String excludeFilesPattern;
    private final String downloadDirLoc;
    private final boolean flattenDirectories;
    private final boolean includeArchiveZips;
    private final boolean deleteFromAzureAfterDownload;
    private final BuildSelector buildSelector;
    private final String projectName;
    private String storageCredentialId;

    private transient AzureCredentials.StorageAccountCredential storageCreds;

    public static class DownloadType {

        public final String type;
        public final String containerName;
        public final String projectName;
        BuildSelector buildSelector;

        @DataBoundConstructor
        public DownloadType(final String value, final String containerName, final String projectName, final BuildSelector buildSelector) {
            this.type = value;
            this.containerName = containerName;
            this.projectName = projectName;
            if (buildSelector == null) {
                this.buildSelector = new StatusBuildSelector(true);
            } else {
                this.buildSelector = buildSelector;
            }
        }
    }

    @DataBoundConstructor
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
            final EnvVars envVars = run.getEnvironment(listener);
            if (envVars == null) {
                throw new IllegalStateException("Failed to capture information about running environment.");
            }

            final String expProjectName = Util.replaceMacro(projectName, envVars);
            final AzureStorageBuilderContext azureStorageBuilderContext = new AzureStorageBuilderContext(launcher, run, listener);

            // Resolve include patterns
            String expIncludePattern = Util.replaceMacro(includeFilesPattern, envVars);
            // If the include is empty, make **/*
            if (Utils.isNullOrEmpty(expIncludePattern)) {
                expIncludePattern = "**/*";
            }
            azureStorageBuilderContext.setIncludeFilesPattern(expIncludePattern);

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
            azureStorageBuilderContext.setExcludeFilesPattern(expExcludePattern);

            // Validate input data and resolve storage account
            validateData(run, listener, storageAccountInfo);
            azureStorageBuilderContext.setStorageAccountInfo(storageAccountInfo);
            azureStorageBuilderContext.setDownloadDirLoc(Util.replaceMacro(downloadDirLoc, envVars));
            azureStorageBuilderContext.setContainerName(Util.replaceMacro(containerName, envVars));

            // Set advanced options
            azureStorageBuilderContext.setFlattenDirectories(flattenDirectories);
            azureStorageBuilderContext.setDeleteFromAzureAfterDownload(deleteFromAzureAfterDownload);

            int filesDownloaded = 0;
            if (downloadType == null || downloadType.equals("container")) {
                /*the null check is backward compatibility*/
                filesDownloaded += WAStorageClient.downloadFromContainer(azureStorageBuilderContext);
            } else {
                Job<?, ?> job = Utils.getJenkinsInstance().getItemByFullName(expProjectName, Job.class);
                if (job != null) {
                    // Resolve download location
                    BuildFilter filter = new BuildFilter();
                    Run source = getBuildSelector().getBuild(job, envVars, filter, run);

                    if (source instanceof MatrixBuild) {
                        for (Run r : ((MatrixBuild) source).getExactRuns()) {
                            filesDownloaded += downloadArtifacts(r, azureStorageBuilderContext);
                        }
                    } else {
                        filesDownloaded += downloadArtifacts(source, azureStorageBuilderContext);
                    }
                } else {
                    listener.getLogger().println(Messages.AzureStorageBuilder_job_invalid(expProjectName));
                    run.setResult(Result.UNSTABLE);
                }
            }

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

    private int downloadArtifacts(final Run<?, ?> source, final AzureStorageBuilderContext context) {
        int filesDownloaded = 0;
        try {
            final AzureBlobAction action = source.getAction(AzureBlobAction.class);
            List<AzureBlob> azureBlobs = action.getIndividualBlobs();
            if (action.getZipArchiveBlob() != null && includeArchiveZips) {
                azureBlobs.addAll(Arrays.asList(action.getZipArchiveBlob()));
            }

            filesDownloaded = WAStorageClient.downloadArtifacts(azureBlobs, context);

        } catch (WAStorageException e) {
            context.getRun().setResult(Result.UNSTABLE);
        }

        return filesDownloaded;
    }

    private void validateData(Run<?, ?> run, TaskListener listener,
                              StorageAccountInfo strAcc) {

        // No need to download artifacts if build failed
        if (run.getResult() == Result.FAILURE) {
            listener.getLogger().println(
                    Messages.AzureStorageBuilder_build_failed_err());
        }

        if (strAcc == null) {
            listener.getLogger().println(
                    Messages.WAStoragePublisher_storage_account_err());
            run.setResult(Result.UNSTABLE);
        }

        // Check if storage account credentials are valid
        try {
            WAStorageClient.validateStorageAccount(strAcc);
        } catch (Exception e) {
            listener.getLogger().println(Messages.Client_SA_val_fail());
            listener.getLogger().println(strAcc.getStorageAccName());
            listener.getLogger().println(strAcc.getBlobEndPointURL());
            run.setResult(Result.UNSTABLE);
        }
    }

    @Override
    public AzureStorageBuilderDesc getDescriptor() {
        // see Descriptor javadoc for more about what a descriptor is.
        return (AzureStorageBuilderDesc) super.getDescriptor();
    }

    @Extension
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
            WAStoragePublisher.WAStorageDescriptor publisherDescriptor = Utils.getJenkinsInstance().getDescriptorByType(
                    WAStoragePublisher.WAStorageDescriptor.class);

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
