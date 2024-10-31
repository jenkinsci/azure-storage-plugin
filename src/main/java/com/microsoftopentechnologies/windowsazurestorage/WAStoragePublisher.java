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
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureStorageAccount;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.helper.Constants;
import com.microsoftopentechnologies.windowsazurestorage.helper.CredentialMigration;
import com.microsoftopentechnologies.windowsazurestorage.helper.CredentialRename;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;
import com.microsoftopentechnologies.windowsazurestorage.service.UploadService;
import com.microsoftopentechnologies.windowsazurestorage.service.UploadToBlobService;
import com.microsoftopentechnologies.windowsazurestorage.service.UploadToFileService;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadServiceData;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadType;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class WAStoragePublisher extends Recorder implements SimpleBuildStep {
    private static final Logger LOGGER = Logger.getLogger(WAStoragePublisher.class.getName());

    private final String storageType;
    private String containerName;
    private String fileShareName;
    private AzureBlobProperties blobProperties;
    private List<AzureBlobMetadataPair> metadata;
    /**
     * Azure storage container access.
     */
    private boolean pubAccessible;
    private boolean cleanUpContainerOrShare;
    private boolean cleanUpVirtualPath;
    private boolean allowAnonymousAccess;
    private boolean uploadArtifactsOnlyIfSuccessful;
    private boolean doNotFailIfArchivingReturnsNothing;
    private boolean uploadZips;
    private boolean doNotUploadIndividualFiles;
    private final String filesPath;
    private String excludeFilesPath = "";
    private String virtualPath = "";
    private String removePrefixPath = "";
    private boolean doNotWaitForPreviousBuild;
    private final String storageCredentialId;
    private boolean onlyUploadModifiedArtifacts;
    private boolean verbose;

    private transient AzureStorageAccount.StorageAccountCredential storageCreds;

    @DataBoundConstructor
    public WAStoragePublisher(String storageCredentialId, String filesPath, String storageType) {
        super();
        this.filesPath = filesPath.trim();
        this.storageType = storageType;
        this.storageCredentialId = storageCredentialId;
    }

    @DataBoundSetter
    public void setContainerName(String containerName) {
        this.containerName = Util.fixEmpty(containerName);
    }

    @DataBoundSetter
    public void setFileShareName(String fileShareName) {
        this.fileShareName = Util.fixEmpty(fileShareName);
    }

    @DataBoundSetter
    public void setBlobProperties(AzureBlobProperties blobProperties) {
        this.blobProperties = blobProperties;
    }

    @DataBoundSetter
    public void setPubAccessible(boolean pubAccessible) {
        this.pubAccessible = pubAccessible;
    }

    @DataBoundSetter
    public void setCleanUpContainerOrShare(boolean cleanUpContainerOrShare) {
        this.cleanUpContainerOrShare = cleanUpContainerOrShare;
    }

    @DataBoundSetter
    public void setCleanUpVirtualPath(boolean cleanUpVirtualPath) {
        this.cleanUpVirtualPath = cleanUpVirtualPath;
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

    public String getRemovePrefixPath() {
        return removePrefixPath;
    }

    @DataBoundSetter
    public void setRemovePrefixPath(String removePrefixPath) {
        this.removePrefixPath = removePrefixPath;
    }

    @DataBoundSetter
    public void setDoNotWaitForPreviousBuild(boolean doNotWaitForPreviousBuild) {
        this.doNotWaitForPreviousBuild = doNotWaitForPreviousBuild;
    }

    @DataBoundSetter
    public void setOnlyUploadModifiedArtifacts(boolean onlyUploadModifiedArtifacts) {
        this.onlyUploadModifiedArtifacts = onlyUploadModifiedArtifacts;
    }

    @DataBoundSetter
    public void setMetadata(List<AzureBlobMetadataPair> metadata) {
        this.metadata = metadata;
    }

    public boolean isVerbose() {
        return verbose;
    }

    @DataBoundSetter
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
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
     * Azure storage container name.
     */
    public String getContainerName() {
        return containerName;
    }

    public String getFileShareName() {
        return this.fileShareName;
    }

    public String getStorageType() {
        if (Constants.isValidStorageType(storageType)) {
            return storageType;
        } else {
            // Assume blob storage for old job configuration
            return Constants.BLOB_STORAGE;
        }
    }

    /**
     * Azure storage blob properties.
     */
    public AzureBlobProperties getBlobProperties() {
        return blobProperties;
    }


    public List<AzureBlobMetadataPair> getMetadata() {
        return metadata;
    }

    /**
     * Azure storage container access.
     */
    public boolean isPubAccessible() {
        return pubAccessible;
    }

    /**
     * Azure storage container cleanup option.
     */
    public boolean isCleanUpContainerOrShare() {
        return cleanUpContainerOrShare;
    }

    /**
     * Azure storage virtual path cleanup option.
     */
    public boolean isCleanUpVirtualPath() {
        return cleanUpVirtualPath;
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
     * If true, artifacts will also be uploaded as a zip rollup.
     */
    public boolean isUploadZips() {
        return uploadZips;
    }

    /**
     * If true, artifacts will not be uploaded as individual files.
     */
    public boolean isDoNotUploadIndividualFiles() {
        return doNotUploadIndividualFiles;
    }

    public boolean isDoNotWaitForPreviousBuild() {
        return doNotWaitForPreviousBuild;
    }

    public boolean isOnlyUploadModifiedArtifacts() {
        return onlyUploadModifiedArtifacts;
    }

    public String getStorageCredentialId() {
        return storageCredentialId;
    }

    private UploadType computeArtifactUploadType(boolean zips, boolean doNotUploadIndividual) {
        if (zips && !doNotUploadIndividual) {
            return UploadType.BOTH;
        } else if (!doNotUploadIndividual) {
            return UploadType.INDIVIDUAL;
        } else if (zips) {
            return UploadType.ZIP;
        } else {
            return UploadType.INVALID;
        }
    }

    public UploadType getArtifactUploadType() {
        return computeArtifactUploadType(this.uploadZips, this.doNotUploadIndividualFiles);
    }

    /**
     * @deprecated Use {@link #getStorageAccName(Item)}.
     */
    @Deprecated
    public String getStorageAccName() {
        return getStorageAccName(null);
    }

    /**
     * Azure Storage Account Name.
     */
    public String getStorageAccName(Item owner) {
        AzureStorageAccount.StorageAccountCredential credential = getStorageAccountCredentials(owner);
        if (credential != null) {
            return credential.getStorageAccountName();
        }
        return null;
    }

    public AzureStorageAccount.StorageAccountCredential getStorageAccountCredentials(Item owner) {
        if (storageCreds == null) {
            storageCreds = AzureStorageAccount.getStorageAccountCredential(owner, getStorageCredentialId());
        }
        return storageCreds;
    }

    /**
     * File Path prefix.
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
     * configuration.
     *
     * @return StorageAccount
     */
    @Deprecated
    public StorageAccountInfo getStorageAccount() {
        String accountName = getStorageAccName();
        StorageAccountInfo storageAcc = null;
        for (StorageAccountInfo sa : getDescriptor().getStorageAccounts()) {
            if (sa.getStorageAccName().equals(accountName)) {
                storageAcc = sa;
                storageAcc.setBlobEndPointURL(Utils.getBlobEP(
                        storageAcc.getBlobEndPointURL()));
                break;
            }
        }

        return storageAcc;
    }

    @Override
    public void perform(
            @NonNull Run<?, ?> run,
            @NonNull FilePath ws,
            @NonNull EnvVars env,
            @NonNull Launcher launcher,
            @NonNull TaskListener listener
    ) throws InterruptedException, IOException {
        final EnvVars envVars = run.getEnvironment(listener);

        AzureStorageAccount.StorageAccountCredential credential = getStorageAccountCredentials(run.getParent());
        if (credential == null) {
            throw new AbortException(String.format("Cannot find storage account credentials with ID: '%s'",
                    getStorageCredentialId()));
        }

        // Get storage account and set formatted blob endpoint url.
        final StorageAccountInfo storageAccountInfo = AzureStorageAccount.convertToStorageAccountInfo(credential);

        // Resolve container name or share name
        final String expContainerName = Utils.replaceMacro(Util.fixNull(containerName), envVars, Locale.ENGLISH);
        final String expShareName = Utils.replaceMacro(Util.fixNull(fileShareName), envVars, Locale.ENGLISH);

        if (!validateData(run, listener, storageAccountInfo, expContainerName, expShareName)) {
            throw new IOException("Plugin can not continue, until previous errors are addressed");
        }

        final UploadServiceData serviceData = new UploadServiceData(run, ws, launcher, listener, storageAccountInfo);
        serviceData.setContainerName(expContainerName);
        serviceData.setFileShareName(expShareName);
        serviceData.setFilePath(Utils.replaceMacro(Util.fixNull(filesPath), envVars));
        serviceData.setExcludedFilesPath(Utils.replaceMacro(Util.fixNull(excludeFilesPath), envVars));
        serviceData.setBlobProperties(blobProperties == null ? new AzureBlobProperties() : blobProperties);
        serviceData.setPubAccessible(pubAccessible);
        serviceData.setCleanUpContainerOrShare(cleanUpContainerOrShare);
        serviceData.setCleanUpVirtualPath(cleanUpVirtualPath);
        serviceData.setUploadType(getArtifactUploadType());
        serviceData.setAzureBlobMetadata(metadata);
        serviceData.setOnlyUploadModifiedArtifacts(onlyUploadModifiedArtifacts);
        serviceData.setCredentialsId(getStorageCredentialId());
        serviceData.setVerbose(isVerbose());
        // Resolve virtual path
        String expVP = Utils.replaceMacro(Util.fixNull(virtualPath), envVars);

        if (!(StringUtils.isBlank(expVP) || expVP.endsWith(Constants.FWD_SLASH))) {
            expVP += Constants.FWD_SLASH;
        }
        serviceData.setVirtualPath(expVP);

        // Resolve remove prefix path
        String rmPrefixPath = Utils.replaceMacro(Util.fixNull(removePrefixPath), envVars);
        if (!(StringUtils.isBlank(rmPrefixPath) || rmPrefixPath.endsWith(Constants.FWD_SLASH))) {
            rmPrefixPath += Constants.FWD_SLASH;
        }
        serviceData.setRemovePrefixPath(rmPrefixPath);

        final UploadService service = getUploadService(serviceData);
        try {
            int filesCount = service.execute();

            // Mark build unstable if no files are uploaded and the user
            // doesn't want the build not to fail in that case.
            if (filesCount == 0) {
                listener.getLogger().println(Messages.WAStoragePublisher_nofiles_uploaded());
                if (!doNotFailIfArchivingReturnsNothing) {
                    throw new IOException(Messages.WAStoragePublisher_nofiles_uploaded());
                }
            } else {
                AzureBlob zipArchiveBlob = null;
                if (getArtifactUploadType() != UploadType.INDIVIDUAL) {
                    zipArchiveBlob = serviceData.getArchiveBlobs().get(0);
                }

                List<AzureBlob> individualBlobs = serviceData.getIndividualBlobs();
                AzureBlobAction existAction = run.getAction(AzureBlobAction.class);
                if (existAction != null) {
                    List<AzureBlob> existActionIndividualBlobs = existAction.getIndividualBlobs();
                    existActionIndividualBlobs.addAll(individualBlobs);
                } else {
                    run.addAction(new AzureBlobAction(individualBlobs, zipArchiveBlob, allowAnonymousAccess,
                            getStorageCredentialId()));
                }
            }
        } catch (Exception e) {
            e.printStackTrace(listener.error(Messages
                    .WAStoragePublisher_uploaded_err(storageAccountInfo.getStorageAccName())));
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw new IOException(Messages.WAStoragePublisher_uploaded_err(storageAccountInfo.getStorageAccName()), e);
        }
    }

    private boolean validateData(
            Run<?, ?> run,
            TaskListener listener,
            StorageAccountInfo storageAccount,
            String expContainerName,
            String expShareName) throws IOException, InterruptedException {

        // No need to upload artifacts if build failed and the job is
        // set to not upload on success.
        if ((run.getResult() == Result.FAILURE || run.getResult() == Result.ABORTED)
                && uploadArtifactsOnlyIfSuccessful) {
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

        if (Constants.BLOB_STORAGE.equalsIgnoreCase(this.getStorageType())) {
            if (StringUtils.isBlank(expContainerName)) {
                listener.getLogger().println("Container name is null or empty");
                return false;
            }

            if (!Utils.validateContainerName(expContainerName)) {
                listener.getLogger().println("Container name contains invalid characters");
                return false;
            }
        } else if (Constants.FILE_STORAGE.equalsIgnoreCase(this.getStorageType())) {
            if (StringUtils.isBlank(expShareName)) {
                listener.getLogger().println("Share name is null or empty");
                return false;
            }

            if (!Utils.validateFileShareName(expShareName)) {
                listener.getLogger().println("Share name contains invalid characters");
                return false;
            }
        } else {
            listener.getLogger().println("Invalid storage type.");
            return false;
        }

        // Check if storage account credentials are valid
        try {
            AzureUtils.validateStorageAccount(storageAccount, true);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
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
        if (doNotWaitForPreviousBuild) {
            return BuildStepMonitor.NONE;
        } else {
            return BuildStepMonitor.STEP;
        }
    }

    private UploadService getUploadService(UploadServiceData data) {
        if (Constants.FILE_STORAGE.equalsIgnoreCase(this.getStorageType())) {
            return new UploadToFileService(data);
        }

        return new UploadToBlobService(data);
    }

    @Extension
    @Symbol("azureUpload")
    public static final class WAStorageDescriptor extends
            BuildStepDescriptor<Publisher> {

        private static final CopyOnWriteList<StorageAccountInfo> STORAGE_ACCOUNTS =
                new CopyOnWriteList<StorageAccountInfo>();

        @Initializer(before = InitMilestone.PLUGINS_STARTED)
        public static void doUpgrade() {
            try {
                CredentialMigration.upgradeStorageConfig();
                CredentialRename.renameStorageConfig();

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

            STORAGE_ACCOUNTS.replaceBy(req.bindParametersToList(
                    StorageAccountInfo.class, "was_"));
            save();
            return super.configure(req, formData);
        }

        /**
         * Validates storage account details.
         *
         * @param was_storageAccName
         * @param was_storageAccountKey
         * @param was_blobEndPointURL
         * @param was_cdnEndPointURL
         * @return
         * @throws IOException
         * @throws ServletException
         */
        public FormValidation doCheckAccount(
                //CHECKSTYLE:OFF
                @QueryParameter String was_storageAccName,
                @QueryParameter String was_storageAccountKey,
                @QueryParameter String was_blobEndPointURL,
                @QueryParameter String was_cdnEndPointURL) throws IOException, ServletException {
            //CHECKSTYLE:ON

            if (StringUtils.isBlank(was_storageAccName)) {
                return FormValidation.error(Messages
                        .WAStoragePublisher_storage_name_req());
            }

            if (StringUtils.isBlank(was_storageAccountKey)) {
                return FormValidation.error(Messages
                        .WAStoragePublisher_storage_key_req());
            }

            String blobEndPointURL = was_blobEndPointURL;
            String cdnEndPointURL = was_cdnEndPointURL;
            try {
                // Get formatted blob endpoint URL.
                blobEndPointURL = Utils.getBlobEP(blobEndPointURL);
                StorageAccountInfo storageAccount = new StorageAccountInfo(
                        was_storageAccName, was_storageAccountKey, blobEndPointURL, cdnEndPointURL);
                AzureUtils.validateStorageAccount(storageAccount, false);
            } catch (Exception e) {
                return FormValidation.error(e, "Error : " + e.getMessage());
            }
            return FormValidation.ok(Messages.WAStoragePublisher_SA_val());
        }

        /**
         * Checks for valid container name.
         *
         * @param request
         * @return FormValidation result
         * @throws IOException
         * @throws ServletException
         */
        public FormValidation doCheckContainerName(StaplerRequest request)
                throws IOException, ServletException {
            final String containerName = request.getParameter("val");
            if (!StringUtils.isBlank(containerName)) {
                // Token resolution happens dynamically at runtime , so for
                // basic validations
                // if text contain tokens considering it as valid input.
                if (Utils.containTokens(containerName) || Utils.validateContainerName(containerName)) {
                    return FormValidation.ok();
                } else {
                    return FormValidation.error(Messages.WAStoragePublisher_container_name_invalid());
                }
            } else {
                return FormValidation.error(Messages.WAStoragePublisher_container_name_req());
            }
        }

        public FormValidation doCheckFileShareName(StaplerRequest request) {
            final String fileShareName = request.getParameter("val");
            if (!StringUtils.isBlank(fileShareName)) {
                // Token resolution happens dynamically at runtime , so for
                // basic validations
                // if text contain tokens considering it as valid input.
                if (Utils.containTokens(fileShareName) || Utils.validateFileShareName(fileShareName)) {
                    return FormValidation.ok();
                } else {
                    return FormValidation.error(Messages.WAStoragePublisher_share_name_invalid());
                }
            } else {
                return FormValidation.error(Messages.WAStoragePublisher_share_name_req());
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
            return STORAGE_ACCOUNTS
                    .toArray(new StorageAccountInfo[STORAGE_ACCOUNTS.size()]);
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
                            AzureStorageAccount.class,
                            owner,
                            ACL.SYSTEM,
                            Collections.<DomainRequirement>emptyList()));
        }

        @Restricted(NoExternalUse.class)
        public List<String> getStorageCredentials() {
            Item owner = null;

            List<AzureStorageAccount> storageAccounts = CredentialsProvider.lookupCredentialsInItem(
                    AzureStorageAccount.class,
                    null,
                    ACL.SYSTEM2
            );

            List<String> allStorageCred = new ArrayList<>();
            for (AzureStorageAccount storageAccount : storageAccounts) {
                allStorageCred.add(
                        String.format("%s (%s)", storageAccount.getStorageAccountName(), storageAccount.getId())
                );
            }
            return allStorageCred;
        }

        @Restricted(NoExternalUse.class)
        public String getAjaxURI() {
            return Constants.CREDENTIALS_AJAX_URI;
        }

    }

}
