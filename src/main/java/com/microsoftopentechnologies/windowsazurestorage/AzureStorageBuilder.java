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
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
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

	/**
     *
     * @param run environment of build
     * @param filePath filepath
     * @param launcher env var for remote builds
     * @param listener logging
     */
	public void perform(Run<?,?> run, FilePath filePath, Launcher launcher, TaskListener listener) {
		StorageAccountInfo strAcc = null;
		try {
			final EnvVars envVars = run.getEnvironment(listener);

			// Resolve container name
			String expContainerName = Util.replaceMacro(containerName, envVars);
			if (expContainerName != null) {
				expContainerName = expContainerName.trim().toLowerCase(
						Locale.ENGLISH);
			}
			
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

			Job<?, ?> job = Jenkins.getInstance().getItemByFullName(expProjectName, Job.class);
			// Resolve download location
			if (job != null) {
				BuildFilter filter = new BuildFilter();
				Run source = getBuildSelector().getBuild(job, envVars, filter, run);

				if (!Utils.isNullOrEmpty(containerName)) {
					// Resolve download location
					String downloadDir = Util.replaceMacro(downloadDirLoc, envVars);
					int filesDownloaded = WAStorageClient.download(run, launcher, listener,
							strAcc, expContainerName, expIncludePattern, expExcludePattern,
							downloadDir, flattenDirectories);

					if (filesDownloaded == 0) { // Mark build unstable if no files are
						// downloaded
						listener.getLogger().println(
								Messages.AzureStorageBuilder_nofiles_downloaded());
						run.setResult(Result.UNSTABLE);
					} else {
						listener.getLogger()
								.println(
										Messages.AzureStorageBuilder_files_downloaded_count(filesDownloaded));
					}
				} else if (source instanceof FreeStyleBuild) {
					downloadArtifacts(source, run, launcher, expIncludePattern, expExcludePattern, listener, strAcc);
				} else if (source instanceof MatrixBuild) {
					for (Run r : ((MatrixBuild) source).getExactRuns()) {
						downloadArtifacts(r, run, launcher, expIncludePattern, expExcludePattern, listener, strAcc);
					}
				}
			} else {
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

			// Resolve download location
			String downloadDir = Util.replaceMacro(downloadDirLoc, envVars);

			int filesDownloaded = WAStorageClient.download(run, launcher, listener,
					strAcc, blob, includeFilter, excludeFilter,
					downloadDir, flattenDirectories);

			if (filesDownloaded == 0) { // Mark build unstable if no files are
				// downloaded
				listener.getLogger().println(
						Messages.AzureStorageBuilder_nofiles_downloaded());
				run.setResult(Result.UNSTABLE);
			} else {
				listener.getLogger()
						.println(
								Messages.AzureStorageBuilder_files_downloaded_count(filesDownloaded));
			}

		} catch (Exception e) {
			run.setResult(Result.UNSTABLE);
		}

		return true;
	}

	public AzureStorageBuilderDesc getDescriptor() {
		// see Descriptor javadoc for more about what a descriptor is.
		return (AzureStorageBuilderDesc) super.getDescriptor();
	}

	@Extension
	public static final class AzureStorageBuilderDesc extends
			Descriptor<Builder> {

		public boolean isApplicable(
				@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
			return true;
		}

		public AzureStorageBuilderDesc() {
			super();
			load();
		}

		// Can be used in future for dynamic display in UI
		/*
		 * private List<String> getContainersList(String StorageAccountName) {
		 * try { return WAStorageClient.getContainersList(
		 * getStorageAccount(StorageAccountName), false); } catch (Exception e)
		 * { e.printStackTrace(); return null; } }
		 * 
		 * private List<String> getBlobsList(String StorageAccountName, String
		 * containerName) { try { return WAStorageClient.getContainerBlobList(
		 * getStorageAccount(StorageAccountName), containerName); } catch
		 * (Exception e) { e.printStackTrace(); return null; } }
		 */

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

		/*
		 * public ComboBoxModel doFillContainerNameItems(
		 * 
		 * @QueryParameter String storageAccName) { ComboBoxModel m = new
		 * ComboBoxModel();
		 * 
		 * List<String> containerList = getContainersList(storageAccName); if
		 * (containerList != null) { m.addAll(containerList); } return m; }
		 * 
		 * public ComboBoxModel doFillBlobNameItems(
		 * 
		 * @QueryParameter String storageAccName,
		 * 
		 * @QueryParameter String containerName) { ComboBoxModel m = new
		 * ComboBoxModel();
		 * 
		 * List<String> blobList = getBlobsList(storageAccName, containerName);
		 * if (blobList != null) { m.addAll(blobList); } return m; }
		 * 
		 * public AutoCompletionCandidates
		 * doAutoCompleteBlobName(@QueryParameter String storageAccName,
		 * 
		 * @QueryParameter String containerName) { List<String> blobList =
		 * getBlobsList(storageAccName, containerName); AutoCompletionCandidates
		 * autoCand = null;
		 * 
		 * if (blobList != null ) { autoCand = new AutoCompletionCandidates();
		 * autoCand.add(blobList.toArray(new String[blobList.size()])); } return
		 * autoCand; }
		 */

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
		 * @param storageAccountName storage account name
         * @return storageAcc storage account
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
			DescriptorExtensionList<BuildSelector, Descriptor<BuildSelector>> list = DescriptorExtensionList.createDescriptorList(Jenkins.getInstance(), BuildSelector.class);
			// remove unneeded build selectors
			list.remove(new DownstreamBuildSelector("", "").getDescriptor());
			list.remove(new PermalinkBuildSelector("").getDescriptor());
			list.remove(new LastCompletedBuildSelector().getDescriptor());
			list.remove(WorkspaceSelector.DESCRIPTOR);
			list.remove(SavedBuildSelector.DESCRIPTOR);
			list.remove(ParameterizedBuildSelector.DESCRIPTOR);
			list.remove(SpecificBuildSelector.DESCRIPTOR);
			return list;
		}
		
	}
}
