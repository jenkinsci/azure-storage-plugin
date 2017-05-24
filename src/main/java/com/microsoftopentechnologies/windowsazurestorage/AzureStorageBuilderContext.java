/*
 * Copyright (c) Microsoft Corporation
 *   <p/>
 *  All rights reserved.
 *   <p/>
 *  MIT License
 *   <p/>
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 *  documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 *  the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 *  to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *  <p/>
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 *  the Software.
 *   <p/>
 *  THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 *  THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 *  TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
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

import java.io.IOException;

public class AzureStorageBuilderContext {
    private Run<?, ?> run;
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
            if (!Utils.isNullOrEmpty(downloadDirLoc)) {
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
