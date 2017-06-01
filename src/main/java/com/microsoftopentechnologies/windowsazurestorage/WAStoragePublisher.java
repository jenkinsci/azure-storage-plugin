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
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureCredentials;
import com.microsoftopentechnologies.windowsazurestorage.helper.Constants;
import com.microsoftopentechnologies.windowsazurestorage.helper.CredentialMigration;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;

import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.*;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

public class WAStoragePublisher extends Recorder implements SimpleBuildStep {

    private final transient String storageAccName;

    private final String containerName;

    private AzureBlobProperties blobProperties;

    private List<AzureBlobMetadataPair> metadata;

    /**
     * Windows Azure storage container access.
     */
    private boolean cntPubAccess;

    private boolean cleanUpContainer;

    private boolean allowAnonymousAccess;

    private boolean uploadArtifactsOnlyIfSuccessful;

    private boolean doNotFailIfArchivingReturnsNothing;

    private boolean uploadZips;

    private boolean doNotUploadIndividualFiles;

    private final String filesPath;

    private String excludeFilesPath = "";

    private String virtualPath = "";

    private boolean doNotWaitForPreviousBuild;

    private final String storageCredentialId;

    private transient AzureCredentials.StorageAccountCredential storageCreds;

    public enum UploadType {

        INDIVIDUAL,
        ZIP,
        BOTH,
        INVALID;
    }

    @Deprecated
    public WAStoragePublisher(final String storageAccName,
                              final String storageCredentialId, final String filesPath, final String excludeFilesPath,
                              final String containerName, final boolean cntPubAccess, final String virtualPath,
                              final AzureBlobProperties blobProperties, final List<AzureBlobMetadataPair> metadata,
                              final boolean cleanUpContainer, final boolean allowAnonymousAccess,
                              final boolean uploadArtifactsOnlyIfSuccessful,
                              final boolean doNotFailIfArchivingReturnsNothing,
                              final boolean doNotUploadIndividualFiles,
                              final boolean uploadZips,
                              final boolean doNotWaitForPreviousBuild) {
        super();
        this.filesPath = filesPath.trim();
        this.excludeFilesPath = excludeFilesPath.trim();
        this.containerName = containerName.trim();
        this.cntPubAccess = cntPubAccess;
        this.virtualPath = virtualPath.trim();
        this.blobProperties = blobProperties;
        this.metadata = metadata;
        this.cleanUpContainer = cleanUpContainer;
        this.allowAnonymousAccess = allowAnonymousAccess;
        this.uploadArtifactsOnlyIfSuccessful = uploadArtifactsOnlyIfSuccessful;
        this.doNotFailIfArchivingReturnsNothing = doNotFailIfArchivingReturnsNothing;
        this.doNotUploadIndividualFiles = doNotUploadIndividualFiles;
        this.uploadZips = uploadZips;
        this.doNotWaitForPreviousBuild = doNotWaitForPreviousBuild;
        this.storageCredentialId = storageCredentialId;
        this.storageCreds = AzureCredentials.getStorageCreds(this.storageCredentialId, storageAccName);
        this.storageAccName = this.storageCreds.getStorageAccountName();
    }

    @DataBoundConstructor
    public WAStoragePublisher(final String storageCredentialId, final String filesPath,
                              final String containerName) {
        super();
        this.filesPath = filesPath.trim();
        this.containerName = containerName.trim();
        this.storageCredentialId = storageCredentialId;
        this.storageCreds = AzureCredentials.getStorageAccountCredential(storageCredentialId);
        this.storageAccName = this.storageCreds.getStorageAccountName();
    }

    @DataBoundSetter
    public void setBlobProperties(AzureBlobProperties blobProperties) {
        this.blobProperties = blobProperties;
    }

    @DataBoundSetter
    public void setCntPubAccess(boolean cntPubAccess) {
        this.cntPubAccess = cntPubAccess;
    }

    @DataBoundSetter
    public void setCleanUpContainer(boolean cleanUpContainer) {
        this.cleanUpContainer = cleanUpContainer;
    }

    @DataBoundSetter
    public void setAllowAnonymousAccess(boolean allowAnonymousAccess) {
        this.allowAnonymousAccess = allowAnonymousAccess;
    }

    @DataBoundSetter
    public void setUploadArtifactsOnlyIfSuccessful(boolean uploadArtifactsOnlyIfSuccessful) {
        this.uploadArtifactsOnlyIfSuccessful = uploadArtifactsOnlyIfSuccessful;
    }

    @DataBoundSetter
    public void setDoNotFailIfArchivingReturnsNothing(boolean doNotFailIfArchivingReturnsNothing) {
        this.doNotFailIfArchivingReturnsNothing = doNotFailIfArchivingReturnsNothing;
    }

    @DataBoundSetter
    public void setUploadZips(boolean uploadZips) {
        this.uploadZips = uploadZips;
    }

    @DataBoundSetter
    public void setDoNotUploadIndividualFiles(boolean doNotUploadIndividualFiles) {
        this.doNotUploadIndividualFiles = doNotUploadIndividualFiles;
    }

    @DataBoundSetter
    public void setExcludeFilesPath(String excludeFilesPath) {
        this.excludeFilesPath = excludeFilesPath;
    }

    @DataBoundSetter
    public void setVirtualPath(String virtualPath) {
        this.virtualPath = virtualPath;
    }

    @DataBoundSetter
    public void setDoNotWaitForPreviousBuild(boolean doNotWaitForPreviousBuild) {
        this.doNotWaitForPreviousBuild = doNotWaitForPreviousBuild;
    }

    @DataBoundSetter
    public void setMetadata(List<AzureBlobMetadataPair> metadata) {
        this.metadata = metadata;
    }

    /**
     * Files path. Ant glob syntax.
     */
    public String getFilesPath() {
        return filesPath;
    }

    /**
     * Files to exclude from archival. Ant glob syntax
     */
    public String getExcludeFilesPath() {
        return excludeFilesPath;
    }

    /**
     * Windows Azure storage container name.
     */
    public String getContainerName() {
        return containerName;
    }

    /**
     * Windows Azure storage blob properties
     */
    public AzureBlobProperties getBlobProperties() {
        return blobProperties;
    }


    public List<AzureBlobMetadataPair> getMetadata() {
        return metadata;
    }

    /**
     * Windows Azure storage container access.
     */
    public boolean isCntPubAccess() {
        return cntPubAccess;
    }

    /**
     * Windows Azure storage container cleanup option.
     */
    public boolean isCleanUpContainer() {
        return cleanUpContainer;
    }

    /**
     * Allowing anonymous access for links generated by jenkins.
     */
    public boolean isAllowAnonymousAccess() {
        return allowAnonymousAccess;
    }

    /**
     * If true, build will not be changed to UNSTABLE if archiving returns
     * nothing.
     */
    public boolean isDoNotFailIfArchivingReturnsNothing() {
        return doNotFailIfArchivingReturnsNothing;
    }

    /**
     * If true, uploads artifacts only if the build passed.
     */
    public boolean isUploadArtifactsOnlyIfSuccessful() {
        return uploadArtifactsOnlyIfSuccessful;
    }

    /**
     * If true, artifacts will also be uploaded as a zip rollup *
     */
    public boolean isUploadZips() {
        return uploadZips;
    }

    /**
     * If true, artifacts will not be uploaded as individual files *
     */
    public boolean isDoNotUploadIndividualFiles() {
        return doNotUploadIndividualFiles;
    }

    public boolean isDoNotWaitForPreviousBuild() {
        return doNotWaitForPreviousBuild;
    }

    public String getStorageCredentialId() {
        if (this.storageCredentialId == null && this.storageAccName != null)
            return AzureCredentials.getStorageCreds(null, this.storageAccName).getId();
        return storageCredentialId;
    }

    private UploadType computeArtifactUploadType(final boolean uploadZips, final boolean doNotUploadIndividualFiles) {
        if (uploadZips && !doNotUploadIndividualFiles) {
            return UploadType.BOTH;
        } else if (!uploadZips && !doNotUploadIndividualFiles) {
            return UploadType.INDIVIDUAL;
        } else if (uploadZips && doNotUploadIndividualFiles) {
            return UploadType.ZIP;
        } else {
            return UploadType.INVALID;
        }
    }

    public UploadType getArtifactUploadType() {
        return computeArtifactUploadType(this.uploadZips, this.doNotUploadIndividualFiles);
    }

    /**
     * Windows Azure Storage Account Name.
     */
    public String getStorageAccName() {
        return storageAccName;
    }

    /**
     * File Path prefix
     */
    public String getVirtualPath() {
        return virtualPath;
    }

    public WAStorageDescriptor getDescriptor() {
        return (WAStorageDescriptor) super.getDescriptor();
    }

    //Defines project actions
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
        AzureBlobProjectAction projectAction = new AzureBlobProjectAction(project);
        List<Action> projectActions = new ArrayList<Action>();
        projectActions.add(projectAction);

        return Collections.unmodifiableList(projectActions);
    }

    /**
     * Returns storage account object based on the name selected in job
     * configuration
     *
     * @return StorageAccount
     */
    public StorageAccountInfo getStorageAccount() {
        StorageAccountInfo storageAcc = null;
        for (StorageAccountInfo sa : getDescriptor().getStorageAccounts()) {
            if (sa.getStorageAccName().equals(storageAccName)) {
                storageAcc = sa;
                storageAcc.setBlobEndPointURL(Utils.getBlobEP(
                        storageAcc.getBlobEndPointURL()));
                break;
            }
        }

        return storageAcc;
    }

    public String replaceMacro(String s, Map<String, String> props, Locale locale) {
        return Util.replaceMacro(s, props).trim().toLowerCase(locale);
    }

    public String replaceMacro(String s, Map<String, String> props) {
        return Util.replaceMacro(s, props).trim();
    }

    @Override
    public synchronized void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath ws, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {

        final EnvVars envVars = run.getEnvironment(listener);

        // Get storage account and set formatted blob endpoint url.
        StorageAccountInfo strAcc = AzureCredentials.convertToStorageAccountInfo(AzureCredentials.getStorageCreds(this.storageCredentialId, this.storageAccName));
        // Resolve container name
        String expContainerName = replaceMacro(containerName, envVars, Locale.ENGLISH);

        if (!validateData(run, listener, strAcc, expContainerName)) {
            throw new IOException("Plugin can not continue, until previous errors are addressed");
        }

        // Resolve file path
        String expFP = replaceMacro(filesPath, envVars);

        // Resolve exclude paths
        String excludeFP = replaceMacro(excludeFilesPath, envVars);

        // Resolve virtual path
        String expVP = replaceMacro(virtualPath, envVars);

        if (!(StringUtils.isBlank(expVP) || expVP.endsWith(Constants.FWD_SLASH))) {
            expVP += Constants.FWD_SLASH;
        }

        try {
            List<AzureBlob> individualBlobs = new ArrayList<AzureBlob>();
            List<AzureBlob> archiveBlobs = new ArrayList<AzureBlob>();

            int filesUploaded = WAStorageClient.upload(run, launcher, listener, strAcc,
                    expContainerName, blobProperties, metadata, cntPubAccess, cleanUpContainer, expFP,
                    expVP, excludeFP, getArtifactUploadType(), individualBlobs, archiveBlobs, ws);

            listener.getLogger().println(Messages.WAStoragePublisher_files_uploaded_count(filesUploaded));

            // Mark build unstable if no files are uploaded and the user
            // doesn't want the build not to fail in that case.
            if (filesUploaded == 0) {
                listener.getLogger().println(
                        Messages.WAStoragePublisher_nofiles_uploaded());
                if (!doNotFailIfArchivingReturnsNothing) {
                    throw new IOException(Messages.WAStoragePublisher_nofiles_uploaded());
                }
            } else {
                AzureBlob zipArchiveBlob = null;
                if (getArtifactUploadType() != UploadType.INDIVIDUAL) {
                    zipArchiveBlob = archiveBlobs.get(0);
                }

                run.getActions().add(new AzureBlobAction(run, strAcc.getStorageAccName(),
                        expContainerName, individualBlobs, zipArchiveBlob, allowAnonymousAccess, storageCredentialId));
            }
        } catch (Exception e) {
            e.printStackTrace(listener.error(Messages
                    .WAStoragePublisher_uploaded_err(strAcc.getStorageAccName())));
            throw new IOException(Messages.WAStoragePublisher_uploaded_err(strAcc.getStorageAccName()));
        }
    }

    private boolean validateData(Run<?, ?> run,
                                 TaskListener listener, StorageAccountInfo storageAccount, String expContainerName) throws IOException, InterruptedException {

        // No need to upload artifacts if build failed and the job is
        // set to not upload on success.
        if ((run.getResult() == Result.FAILURE || run.getResult() == Result.ABORTED) && uploadArtifactsOnlyIfSuccessful) {
            listener.getLogger().println(
                    Messages.WAStoragePublisher_build_failed_err());
            return false;
        }

        if (storageAccount == null) {
            listener.getLogger().println(
                    Messages.WAStoragePublisher_storage_account_err());
            return false;
        }

        // Validate files path
        if (StringUtils.isBlank(filesPath)) {
            listener.getLogger().println(
                    Messages.WAStoragePublisher_filepath_err());
            return false;
        }

        if (getArtifactUploadType() == UploadType.INVALID) {
            listener.getLogger().println(
                    Messages.WAStoragePublisher_uploadtype_invalid());
            return false;
        }

        if (StringUtils.isBlank(expContainerName)) {
            listener.getLogger().println("Container name is null or empty");
            return false;
        }

        if (!Utils.validateContainerName(expContainerName)) {
            listener.getLogger().println("Container name contains invalid characters");
            return false;
        }

        // Check if storage account credentials are valid
        try {
            WAStorageClient.validateStorageAccount(storageAccount);
        } catch (Exception e) {
            listener.getLogger().println(Messages.Client_SA_val_fail());
            listener.getLogger().println(
                    "Storage Account name --->"
                            + storageAccount.getStorageAccName() + "<----");
            listener.getLogger().println(
                    "Blob end point url --->"
                            + storageAccount.getBlobEndPointURL() + "<----");
            return false;
        }
        return true;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return doNotWaitForPreviousBuild ? BuildStepMonitor.NONE : BuildStepMonitor.STEP;
    }

    @Extension
    @Symbol("azureUpload")
    public static final class WAStorageDescriptor extends
            BuildStepDescriptor<Publisher> {

        private static final CopyOnWriteList<StorageAccountInfo> storageAccounts = new CopyOnWriteList<StorageAccountInfo>();

        @Initializer(before = InitMilestone.PLUGINS_STARTED)
        public static void doUpgrade() {
            try {
                CredentialMigration.upgradeStorageConfig();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public WAStorageDescriptor() {
            super();
            load();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData)
                throws FormException {

            storageAccounts.replaceBy(req.bindParametersToList(
                    StorageAccountInfo.class, "was_"));
            save();
            return super.configure(req, formData);
        }

        /**
         * Validates storage account details.
         *
         * @param storageAccountName
         * @param blobEndPointURL
         * @param storageAccountKey
         * @return
         * @throws IOException
         * @throws ServletException
         */
        public FormValidation doCheckAccount(
                @QueryParameter String was_storageAccName,
                @QueryParameter String was_storageAccountKey,
                @QueryParameter String was_blobEndPointURL) throws IOException,
                ServletException {

            if (StringUtils.isBlank(was_storageAccName)) {
                return FormValidation.error(Messages
                        .WAStoragePublisher_storage_name_req());
            }

            if (StringUtils.isBlank(was_storageAccountKey)) {
                return FormValidation.error(Messages
                        .WAStoragePublisher_storage_key_req());
            }

            try {
                // Get formatted blob end point URL.
                was_blobEndPointURL = Utils.getBlobEP(was_blobEndPointURL);
                StorageAccountInfo storageAccount = new StorageAccountInfo(was_storageAccName, was_storageAccountKey, was_blobEndPointURL);
                WAStorageClient.validateStorageAccount(storageAccount);
            } catch (Exception e) {
                return FormValidation.error("Error : " + e.getMessage());
            }
            return FormValidation.ok(Messages.WAStoragePublisher_SA_val());
        }

        /**
         * Checks for valid container name.
         *
         * @param val name of the container
         * @return FormValidation result
         * @throws IOException
         * @throws ServletException
         */
        public FormValidation doCheckName(@QueryParameter String val)
                throws IOException, ServletException {
            if (!StringUtils.isBlank(val)) {
                // Token resolution happens dynamically at runtime , so for
                // basic validations
                // if text contain tokens considering it as valid input.
                if (Utils.containTokens(val)
                        || Utils.validateContainerName(val)) {
                    return FormValidation.ok();
                } else {
                    return FormValidation.error(Messages
                            .WAStoragePublisher_container_name_invalid());
                }
            } else {
                return FormValidation.error(Messages
                        .WAStoragePublisher_container_name_req());
            }
        }

        public FormValidation doCheckPath(@QueryParameter String val) {
            if (StringUtils.isBlank(val)) {
                return FormValidation.error(Messages
                        .WAStoragePublisher_artifacts_req());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckBlobName(@QueryParameter String val) {
            if (StringUtils.isBlank(val)) {
                return FormValidation.error(Messages
                        .AzureStorageBuilder_blobName_req());
            } else if (!Utils.validateBlobName(val)) {
                return FormValidation.error(Messages
                        .AzureStorageBuilder_blobName_invalid());
            } else {
                return FormValidation.ok();
            }

        }

        @SuppressWarnings("rawtypes")
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.WAStoragePublisher_displayName();
        }

        public StorageAccountInfo[] getStorageAccounts() {
            return storageAccounts
                    .toArray(new StorageAccountInfo[storageAccounts.size()]);
        }

        public StorageAccountInfo getStorageAccount(String name) {

            if (name == null || (name.trim().length() == 0)) {
                return null;
            }

            StorageAccountInfo storageAccountInfo = null;
            StorageAccountInfo[] storageAccountList = getStorageAccounts();

            if (storageAccountList != null) {
                for (StorageAccountInfo sa : storageAccountList) {
                    if (sa.getStorageAccName().equals(name)) {
                        storageAccountInfo = sa;
                        storageAccountInfo.setBlobEndPointURL(
                                Utils.getBlobEP(storageAccountInfo.getBlobEndPointURL()));
                        break;
                    }
                }
            }
            return storageAccountInfo;
        }

        public String getDefaultBlobURL() {
            return Utils.getDefaultBlobURL();
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
            ListBoxModel m = new StandardListBoxModel().withAll(CredentialsProvider.lookupCredentials(AzureCredentials.class, owner, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()));
            return m;
        }

        @Restricted(NoExternalUse.class)
        public List<String> getStorageCredentials() {
            Item owner = null;
            ListBoxModel allCreds = new StandardListBoxModel().withAll(CredentialsProvider.lookupCredentials(AzureCredentials.class, owner, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()));
            ArrayList<Object> res = new ArrayList<Object>();
            List<String> allStorageCred = new ArrayList<String>();
            for (int i = 0; i < allCreds.size(); i++) {
                res.add(allCreds.get(i));
                String eachStorageCredential = res.get(i).toString();
                String eachStorageAccount = eachStorageCredential.substring(0, eachStorageCredential.indexOf('='));

                allStorageCred.add(eachStorageAccount);

            }
            return allStorageCred;
        }

        @Restricted(NoExternalUse.class)
        public String getAjaxURI() {
            return Constants.CREDENTIALS_AJAX_URI;
        }

    }

}
