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

package com.microsoftopentechnologies.windowsazurestorage;

import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;

public class AzureStorageBuilderContext {
    Run<?, ?> run;
    Launcher launcher;
    TaskListener listener;

    FilePath workspace;
    String includeFilesPattern;
    String excludeFilesPattern;
    StorageAccountInfo storageAccountInfo;
    String containerName;
    String downloadDirLoc;
    boolean flattenDirectories;
    boolean deleteFromAzureAfterDownload;

    public AzureStorageBuilderContext(final Launcher launcher, final Run<?, ?> run, final TaskListener taskListener){
        this.launcher = launcher;
        this.run = run;
        this.listener = taskListener;
    }

    public FilePath getWorkspacePath(){
        return new FilePath(launcher.getChannel(), workspace.getRemote());
    }

    public FilePath getDownloadDir() {
        FilePath downloadDir = getWorkspacePath();
        try {
            if (!StringUtils.isBlank(downloadDirLoc)) {
                final EnvVars envVars = run.getEnvironment(listener);
                downloadDir = new FilePath(getWorkspacePath(), Util.replaceMacro(downloadDirLoc, envVars));
            }

            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }
        } catch (IOException | InterruptedException e) {
            run.setResult(Result.UNSTABLE);
        }

        return downloadDir;
    }

    public Run<?, ?> getRun() {
        return run;
    }

    public void setRun(Run<?, ?> run) {
        this.run = run;
    }

    public FilePath getWorkspace() {
        return workspace;
    }

    public void setWorkspace(FilePath workspace) {
        this.workspace = workspace;
    }

    public Launcher getLauncher() {
        return launcher;
    }

    public void setLauncher(Launcher launcher) {
        this.launcher = launcher;
    }
    public TaskListener getListener() {
        return listener;
    }

    public void setListener(TaskListener listener) {
        this.listener = listener;
    }

    public String getIncludeFilesPattern() {
        return includeFilesPattern;
    }

    public void setIncludeFilesPattern(String includeFilesPattern) {
        this.includeFilesPattern = includeFilesPattern;
    }

    public String getExcludeFilesPattern() {
        return excludeFilesPattern;
    }

    public void setExcludeFilesPattern(String excludeFilesPattern) {
        this.excludeFilesPattern = excludeFilesPattern;
    }

    public StorageAccountInfo getStorageAccountInfo() {
        return storageAccountInfo;
    }

    public void setStorageAccountInfo(StorageAccountInfo storageAccountInfo) {
        this.storageAccountInfo = storageAccountInfo;
    }

    public String getDownloadDirLoc() {
        return downloadDirLoc;
    }

    public void setDownloadDirLoc(String downloadDirLoc) {
        this.downloadDirLoc = downloadDirLoc;
    }

    public boolean isFlattenDirectories() {
        return flattenDirectories;
    }

    public void setFlattenDirectories(boolean flattenDirectories) {
        this.flattenDirectories = flattenDirectories;
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public boolean isDeleteFromAzureAfterDownload() {
        return deleteFromAzureAfterDownload;
    }

    public void setDeleteFromAzureAfterDownload(boolean deleteFromAzureAfterDownload) {
        this.deleteFromAzureAfterDownload = deleteFromAzureAfterDownload;
    }
}
