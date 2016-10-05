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

import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;
import hudson.DescriptorExtensionList;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.matrix.MatrixBuild;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.copyartifact.BuildFilter;
import hudson.plugins.copyartifact.BuildSelector;
import hudson.plugins.copyartifact.DownstreamBuildSelector;
import hudson.plugins.copyartifact.LastCompletedBuildSelector;
import hudson.plugins.copyartifact.ParameterizedBuildSelector;
import hudson.plugins.copyartifact.PermalinkBuildSelector;
import hudson.plugins.copyartifact.SavedBuildSelector;
import hudson.plugins.copyartifact.SpecificBuildSelector;
import hudson.plugins.copyartifact.StatusBuildSelector;
import hudson.plugins.copyartifact.WorkspaceSelector;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class AzureStorageBuilder extends Builder implements SimpleBuildStep{

	private String storageAccName;
	private String containerName;
	private String includeFilesPattern;
	private String excludeFilesPattern;
	private String downloadDirLoc;
	private boolean flattenDirectories;
	private boolean includeArchiveZips;
	private BuildSelector buildSelector;
	private String projectName;

	@DataBoundConstructor
	public AzureStorageBuilder(String storageAccName, String containerName, BuildSelector buildSelector,
			String includeFilesPattern, String excludeFilesPattern, String downloadDirLoc, boolean flattenDirectories,
			boolean includeArchiveZips, String projectName) {
		this.storageAccName = storageAccName;
		this.containerName = containerName;
		this.includeFilesPattern = includeFilesPattern;
		this.excludeFilesPattern = excludeFilesPattern;
		this.downloadDirLoc = downloadDirLoc;
		this.flattenDirectories = flattenDirectories;
		this.includeArchiveZips = includeArchiveZips;
		this.projectName = projectName;
		if (buildSelector == null) {
			buildSelector = new StatusBuildSelector(true);
		}
		this.buildSelector = buildSelector;
	}

	public BuildSelector getBuildSelector() {
		return buildSelector;
	}

	public String getStorageAccName() {
		return storageAccName;
	}

	public void setStorageAccName(String storageAccName) {
		this.storageAccName = storageAccName;
	}

	public String getContainerName() {
		return containerName;
	}

	public void setContainerName(String containerName) {
		this.containerName = containerName;
	}
	
	public String getProjectName() {
		return projectName;
	}

	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	public String getIncludeFilesPattern() {
		return includeFilesPattern;
	}

	public void setIncludeFilesPattern(String excludeFilesPattern) {
		this.includeFilesPattern = excludeFilesPattern;
	}
	
	public String getExcludeFilesPattern() {
		return excludeFilesPattern;
	}

	public void setExcludeFilesPattern(String excludeFilesPattern) {
		this.excludeFilesPattern = excludeFilesPattern;
	}

	public String getDownloadDirLoc() {
		return downloadDirLoc;
	}

	public void setDownloadDirLoc(String downloadDirLoc) {
		this.downloadDirLoc = downloadDirLoc;
	}
	
	public boolean isIncludeArchiveZips() {
		return includeArchiveZips;
	}
	
	public void setIncludeArchiveZips(final boolean includeArchiveZips) {
		this.includeArchiveZips = includeArchiveZips;
	}
	
	public boolean isFlattenDirectories() {
		return flattenDirectories;
	}
	
	public void setFlattenDirectories(final boolean flattenDirectories){
		this.flattenDirectories = flattenDirectories;
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	public void perform(Run<?,?> run, FilePath filePath, Launcher launcher, TaskListener listener) {
		StorageAccountInfo strAcc = null;
		try {
			final EnvVars envVars = run.getEnvironment(listener);

			// Get storage account
			strAcc = getDescriptor().getStorageAccount(storageAccName);

			// Resolve include patterns
			String expIncludePattern = Util.replaceMacro(includeFilesPattern, envVars);
			
			// Resolve exclude patterns
			String expExcludePattern = Util.replaceMacro(excludeFilesPattern, envVars);
			
			String expProjectName = Util.replaceMacro(projectName, envVars);
			
			// If the include is empty, make **/*
			if (Utils.isNullOrEmpty(expIncludePattern)) {
				expIncludePattern = "**/*";
			}
			
			// Exclude archive.zip by default.
			if (!includeArchiveZips) {
				if (expExcludePattern != null) {
					expExcludePattern += ",archive.zip";
				}
				else {
					expExcludePattern = "archive.zip";
				}
			}

			// Validate input data
			validateData(run, listener, strAcc);

			Job<?, ?> job = Jenkins.getInstance().getItemByFullName(expProjectName, Job.class);
			if (job != null) {
				// Resolve download location
				BuildFilter filter = new BuildFilter();
				Run source = getBuildSelector().getBuild(job, envVars, filter, run);

				if (source instanceof MatrixBuild) {
					for (Run r : ((MatrixBuild) source).getExactRuns()) {
						downloadArtifacts(r, run, launcher, expIncludePattern, expExcludePattern, listener, strAcc);
					}
				} else {
					downloadArtifacts(source, run, launcher, expIncludePattern, expExcludePattern, listener, strAcc);
				}
			} 
			else {
				listener.getLogger().println(Messages.AzureStorageBuilder_job_invalid(expProjectName));
				run.setResult(Result.UNSTABLE);
			}
		}
		catch (Exception e) {
			e.printStackTrace(listener.error(Messages
					.AzureStorageBuilder_download_err(strAcc
							.getStorageAccName())));
			run.setResult(Result.UNSTABLE);
		}
	}
	
	private boolean downloadArtifacts(Run<?, ?> source, Run<?, ?> run, Launcher launcher, String includeFilter, String excludeFilter, TaskListener listener, StorageAccountInfo strAcc) {
		try {
			final EnvVars envVars = run.getEnvironment(listener);
			final AzureBlobAction action = source.getAction(AzureBlobAction.class);
			List<AzureBlob> blob = action.getIndividualBlobs();
			if (action.getZipArchiveBlob() != null && includeArchiveZips) {
				blob.addAll(Arrays.asList(action.getZipArchiveBlob()));
			}

			// Resolve download location
			String downloadDir = Util.replaceMacro(downloadDirLoc, envVars);

			int filesDownloaded = WAStorageClient.download(run, launcher, listener,
					strAcc, blob, includeFilter, excludeFilter,
					downloadDir, flattenDirectories);

			if (filesDownloaded == 0) { // Mark build unstable if no files are
				// downloaded
				listener.getLogger().println(Messages.AzureStorageBuilder_nofiles_downloaded());
				run.setResult(Result.UNSTABLE);
			} else {
				listener.getLogger()
						.println(Messages.AzureStorageBuilder_files_downloaded_count(filesDownloaded));
			}

		} catch (Exception e) {
			run.setResult(Result.UNSTABLE);
		}

		return true;
	}

	private void validateData(Run<?,?> run, TaskListener listener,
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
		
		public AutoCompletionCandidates doAutoCompleteProjectName(@QueryParameter String value) {
			AutoCompletionCandidates projectList = new AutoCompletionCandidates();
			for (AbstractProject<?, ?> project : Jenkins.getInstance().getItems(AbstractProject.class)) {
				if (project.getName().toLowerCase().startsWith(value.toLowerCase())) {
					projectList.add(project.getName());
				}
			}
			return projectList;
		}

		/* public FormValidation doCheckIsDirectory(@QueryParameter String val) {
			// If null or if file does not exists don't display any validation
			// error.
			File downloadDir = new File(val);
			if (Utils.isNullOrEmpty(val) || !downloadDir.exists()) {
				return FormValidation.ok();
			} else if (downloadDir.exists() && !downloadDir.isDirectory()) {
				return FormValidation.error(Messages
						.AzureStorageBuilder_downloadDir_invalid());
			} else {
				return FormValidation.ok();
			}
		} */

		public boolean configure(StaplerRequest req, JSONObject formData)
				throws FormException {
			save();
			return super.configure(req, formData);
		}

		public String getDisplayName() {
			return Messages.AzureStorageBuilder_displayName();
		}

		public StorageAccountInfo[] getStorageAccounts() {
			WAStoragePublisher.WAStorageDescriptor publisherDescriptor = Jenkins
					.getInstance().getDescriptorByType(
							WAStoragePublisher.WAStorageDescriptor.class);

			StorageAccountInfo[] sa = publisherDescriptor.getStorageAccounts();

			return sa;
		}

		/**
		 * Returns storage account object
		 * 
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
