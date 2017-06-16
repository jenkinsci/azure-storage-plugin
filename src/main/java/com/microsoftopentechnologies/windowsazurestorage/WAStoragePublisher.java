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
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.helper.Constants;
import com.microsoftopentechnologies.windowsazurestorage.helper.CredentialMigration;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;
import com.microsoftopentechnologies.windowsazurestorage.service.UploadService;
import com.microsoftopentechnologies.windowsazurestorage.service.UploadToBlobService;
import com.microsoftopentechnologies.windowsazurestorage.service.UploadToFileService;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadServiceData;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadType;
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

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WAStoragePublisher extends Recorder implements SimpleBuildStep {
    private final transient String storageAccName;
    private final String storageType;
    private final String containerName;
    private final String fileShareName;
    private AzureBlobProperties blobProperties;
    private List<AzureBlobMetadataPair> metadata;
    /**
     * Windows Azure storage container access.
     */
    private boolean pubAccessible;
    private boolean cleanUpContainerOrShare;
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

    @Deprecated
    public WAStoragePublisher(final String storageAccName,
                              final String storageCredentialId, final String filesPath, final String excludeFilesPath,
                              final String containerName, final boolean pubAccessible, final String virtualPath,
                              final String storageType, final String fileShareName,
                              final AzureBlobProperties blobProperties, final List<AzureBlobMetadataPair> metadata,
                              final boolean cleanUpContainerOrShare, final boolean allowAnonymousAccess,
                              final boolean uploadArtifactsOnlyIfSuccessful,
                              final boolean doNotFailIfArchivingReturnsNothing,
                              final boolean doNotUploadIndividualFiles,
                              final boolean uploadZips,
                              final boolean doNotWaitForPreviousBuild) {
        super();
        this.filesPath = filesPath.trim();
        this.excludeFilesPath = excludeFilesPath.trim();
        this.storageType = storageType;
        this.fileShareName = fileShareName.trim();
        this.containerName = containerName.trim();
        this.pubAccessible = pubAccessible;
        this.virtualPath = virtualPath.trim();
        this.blobProperties = blobProperties;
        this.metadata = metadata;
        this.cleanUpContainerOrShare = cleanUpContainerOrShare;
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
                              final String storageType, final String containerName, final String fileShareName) {
        super();
        this.filesPath = filesPath.trim();
        this.storageType = storageType;
        this.containerName = StringUtils.trimToEmpty(containerName);
        this.fileShareName = StringUtils.trimToEmpty(fileShareName);
        this.storageCredentialId = storageCredentialId;
        this.storageCreds = AzureCredentials.getStorageAccountCredential(storageCredentialId);
        this.storageAccName = this.storageCreds.getStorageAccountName();
    }

    @DataBoundSetter
    public void setBlobProperties(final AzureBlobProperties blobProperties) {
        this.blobProperties = blobProperties;
    }

    @DataBoundSetter
    public void setPubAccessible(final boolean pubAccessible) {
        this.pubAccessible = pubAccessible;
    }

    @DataBoundSetter
    public void setCleanUpContainerOrShare(final boolean cleanUpContainerOrShare) {
        this.cleanUpContainerOrShare = cleanUpContainerOrShare;
    }

    @DataBoundSetter
    public void setAllowAnonymousAccess(final boolean allowAnonymousAccess) {
        this.allowAnonymousAccess = allowAnonymousAccess;
    }

    @DataBoundSetter
    public void setUploadArtifactsOnlyIfSuccessful(final boolean uploadArtifactsOnlyIfSuccessful) {
        this.uploadArtifactsOnlyIfSuccessful = uploadArtifactsOnlyIfSuccessful;
    }

    @DataBoundSetter
    public void setDoNotFailIfArchivingReturnsNothing(final boolean doNotFailIfArchivingReturnsNothing) {
        this.doNotFailIfArchivingReturnsNothing = doNotFailIfArchivingReturnsNothing;
    }

    @DataBoundSetter
    public void setUploadZips(final boolean uploadZips) {
        this.uploadZips = uploadZips;
    }

    @DataBoundSetter
    public void setDoNotUploadIndividualFiles(final boolean doNotUploadIndividualFiles) {
        this.doNotUploadIndividualFiles = doNotUploadIndividualFiles;
    }

    @DataBoundSetter
    public void setExcludeFilesPath(final String excludeFilesPath) {
        this.excludeFilesPath = excludeFilesPath;
    }

    @DataBoundSetter
    public void setVirtualPath(final String virtualPath) {
        this.virtualPath = virtualPath;
    }

    @DataBoundSetter
    public void setDoNotWaitForPreviousBuild(final boolean doNotWaitForPreviousBuild) {
        this.doNotWaitForPreviousBuild = doNotWaitForPreviousBuild;
    }

    @DataBoundSetter
    public void setMetadata(final List<AzureBlobMetadataPair> metadata) {
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

    public String getFileShareName() {
        return this.fileShareName;
    }

    public String getStorageType() {
        return storageType;
    }

    /**
     * Windows Azure storage blob properties.
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
    public boolean isPubAccessible() {
        return pubAccessible;
    }

    /**
     * Windows Azure storage container cleanup option.
     */
    public boolean isCleanUpContainerOrShare() {
        return cleanUpContainerOrShare;
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

    public String getStorageCredentialId() {
        if (this.storageCredentialId == null && this.storageAccName != null) {
            return AzureCredentials.getStorageCreds(null, this.storageAccName).getId();
        }
        return storageCredentialId;
    }

    private UploadType computeArtifactUploadType(final boolean zips, final boolean doNotUploadIndividual) {
        if (zips && !doNotUploadIndividual) {
            return UploadType.BOTH;
        } else if (!zips && !doNotUploadIndividual) {
            return UploadType.INDIVIDUAL;
        } else if (zips && doNotUploadIndividual) {
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
     * File Path prefix.
     */
    public String getVirtualPath() {
        return virtualPath;
    }

    public WAStorageDescriptor getDescriptor() {
        return (WAStorageDescriptor) super.getDescriptor();
    }

    //Defines project actions
    public Collection<? extends Action> getProjectActions(final AbstractProject<?, ?> project) {
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

    public String replaceMacro(final String s, final Map<String, String> props, final Locale locale) {
        return Util.replaceMacro(s, props).trim().toLowerCase(locale);
    }

    public String replaceMacro(final String s, final Map<String, String> props) {
        return Util.replaceMacro(s, props).trim();
    }

    @Override
    public synchronized void perform(
            @Nonnull final Run<?, ?> run,
            @Nonnull final FilePath ws,
            @Nonnull final Launcher launcher,
            @Nonnull final TaskListener listener) throws InterruptedException, IOException {

        final EnvVars envVars = run.getEnvironment(listener);

        // Get storage account and set formatted blob endpoint url.
        final StorageAccountInfo storageAccountInfo = AzureCredentials.convertToStorageAccountInfo(
                AzureCredentials.getStorageCreds(this.storageCredentialId, this.storageAccName));

        // Resolve container name or share name
        final String expContainerName = replaceMacro(containerName, envVars, Locale.ENGLISH);
        final String expShareName = replaceMacro(fileShareName, envVars, Locale.ENGLISH);

        if (!validateData(run, listener, storageAccountInfo, expContainerName, expShareName)) {
            throw new IOException("Plugin can not continue, until previous errors are addressed");
        }

        final UploadServiceData serviceData = new UploadServiceData(run, ws, launcher, listener, storageAccountInfo);
        serviceData.setContainerName(expContainerName);
        serviceData.setFileShareName(expShareName);
        serviceData.setFilePath(replaceMacro(filesPath, envVars));
        serviceData.setExcludedFilesPath(replaceMacro(excludeFilesPath, envVars));
        serviceData.setBlobProperties(blobProperties);
        serviceData.setPubAccessible(pubAccessible);
        serviceData.setCleanUpContainerOrShare(cleanUpContainerOrShare);
        serviceData.setUploadType(getArtifactUploadType());
        serviceData.setAzureBlobMetadata(metadata);
        // Resolve virtual path
        String expVP = replaceMacro(virtualPath, envVars);

        if (!(StringUtils.isBlank(expVP) || expVP.endsWith(Constants.FWD_SLASH))) {
            expVP += Constants.FWD_SLASH;
        }
        serviceData.setVirtualPath(expVP);

        final UploadService service = getUploadService(serviceData);
        try {
            int filesUploaded = service.execute();

            listener.getLogger().println(Messages.WAStoragePublisher_files_uploaded_count(filesUploaded));

            // Mark build unstable if no files are uploaded and the user
            // doesn't want the build not to fail in that case.
            if (filesUploaded == 0) {
                listener.getLogger().println(Messages.WAStoragePublisher_nofiles_uploaded());
                if (!doNotFailIfArchivingReturnsNothing) {
                    throw new IOException(Messages.WAStoragePublisher_nofiles_uploaded());
                }
            } else {
                AzureBlob zipArchiveBlob = null;
                if (getArtifactUploadType() != UploadType.INDIVIDUAL) {
                    zipArchiveBlob = serviceData.getArchiveBlobs().get(0);
                }

                run.getActions().add(new AzureBlobAction(run, storageAccountInfo.getStorageAccName(), expContainerName,
                        serviceData.getIndividualBlobs(), zipArchiveBlob, allowAnonymousAccess, storageCredentialId));
            }
        } catch (Exception e) {
            e.printStackTrace(listener.error(Messages
                    .WAStoragePublisher_uploaded_err(storageAccountInfo.getStorageAccName())));
            throw new IOException(Messages.WAStoragePublisher_uploaded_err(storageAccountInfo.getStorageAccName()));
        }
    }

    private boolean validateData(
            final Run<?, ?> run,
            final TaskListener listener,
            final StorageAccountInfo storageAccount,
            final String expContainerName,
            final String expShareName) throws IOException, InterruptedException {

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
            AzureUtils.validateStorageAccount(storageAccount);
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
        if (doNotWaitForPreviousBuild) {
            return BuildStepMonitor.NONE;
        } else {
            return BuildStepMonitor.STEP;
        }
    }

    private UploadService getUploadService(final UploadServiceData data) {
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

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public WAStorageDescriptor() {
            super();
            load();
        }

        @Override
        public boolean configure(final StaplerRequest req, final JSONObject formData)
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
         * @return
         * @throws IOException
         * @throws ServletException
         */
        public FormValidation doCheckAccount(
                //CHECKSTYLE:OFF
                @QueryParameter final String was_storageAccName,
                @QueryParameter final String was_storageAccountKey,
                @QueryParameter final String was_blobEndPointURL) throws IOException,
                //CHECKSTYLE:ON
                ServletException {

            if (StringUtils.isBlank(was_storageAccName)) {
                return FormValidation.error(Messages
                        .WAStoragePublisher_storage_name_req());
            }

            if (StringUtils.isBlank(was_storageAccountKey)) {
                return FormValidation.error(Messages
                        .WAStoragePublisher_storage_key_req());
            }

            String blobEndPointURL = was_blobEndPointURL;
            try {
                // Get formatted blob end point URL.
                blobEndPointURL = Utils.getBlobEP(blobEndPointURL);
                StorageAccountInfo storageAccount = new StorageAccountInfo(
                        was_storageAccName, was_storageAccountKey, blobEndPointURL);
                AzureUtils.validateStorageAccount(storageAccount);
            } catch (Exception e) {
                return FormValidation.error("Error : " + e.getMessage());
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
        public FormValidation doCheckContainerName(final StaplerRequest request)
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

        public FormValidation doCheckFileShareName(final StaplerRequest request) {
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

        public FormValidation doCheckPath(@QueryParameter final String val) {
            if (StringUtils.isBlank(val)) {
                return FormValidation.error(Messages
                        .WAStoragePublisher_artifacts_req());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckBlobName(@QueryParameter final String val) {
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
        public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
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

        public StorageAccountInfo getStorageAccount(final String name) {

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

        public ListBoxModel doFillStorageCredentialIdItems(@AncestorInPath final Item owner) {
            ListBoxModel m = new StandardListBoxModel().withAll(
                    CredentialsProvider.lookupCredentials(
                            AzureCredentials.class,
                            owner,
                            ACL.SYSTEM,
                            Collections.<DomainRequirement>emptyList()));
            return m;
        }

        @Restricted(NoExternalUse.class)
        public List<String> getStorageCredentials() {
            Item owner = null;
            ListBoxModel allCreds = new StandardListBoxModel().withAll(
                    CredentialsProvider.lookupCredentials(
                            AzureCredentials.class,
                            owner,
                            ACL.SYSTEM,
                            Collections.<DomainRequirement>emptyList()));
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
