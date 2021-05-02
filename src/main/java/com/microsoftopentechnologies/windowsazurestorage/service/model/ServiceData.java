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

package com.microsoftopentechnologies.windowsazurestorage.service.model;

import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;

public abstract class ServiceData {
    private final Run<?, ?> run;
    private final FilePath workspace;
    private final Launcher launcher;
    private final TaskListener taskListener;
    private final StorageAccountInfo storageAccountInfo;
    private boolean verbose;

    protected ServiceData(Run<?, ?> run,
                          FilePath workspace,
                          Launcher launcher,
                          TaskListener taskListener,
                          StorageAccountInfo storageAccountInfo) {
        this.run = run;
        this.workspace = workspace;
        this.launcher = launcher;
        this.taskListener = taskListener;
        this.storageAccountInfo = storageAccountInfo;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public StorageAccountInfo getStorageAccountInfo() {
        return storageAccountInfo;
    }

    public Run<?, ?> getRun() {
        return run;
    }

    public Launcher getLauncher() {
        return launcher;
    }

    public TaskListener getTaskListener() {
        return taskListener;
    }

    public FilePath getWorkspace() {
        return workspace;
    }

    public FilePath getRemoteWorkspace() {
        return new FilePath(launcher.getChannel(), workspace.getRemote());
    }
}
