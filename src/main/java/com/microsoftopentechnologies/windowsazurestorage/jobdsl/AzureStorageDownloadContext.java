package com.microsoftopentechnologies.windowsazurestorage.jobdsl;

import hudson.plugins.copyartifact.BuildSelector;
import hudson.plugins.copyartifact.StatusBuildSelector;
import hudson.plugins.copyartifact.TriggeredBuildSelector;
import javaposse.jobdsl.dsl.Context;

/**
 *
 * @author crummel
 */
public class AzureStorageDownloadContext implements Context {
    String storageAccountName;
    String containerName;
    String includeFilesPattern;
    String excludeFilesPattern;
    String downloadDirectoryLocation;
    String projectName;
    boolean flattenDirectories;
    boolean includeArchiveZips;
    BuildSelector buildSelector;
    
    public void latestSuccessfulBuildSelector(boolean stableBuildOnly) {
        guardSingleBuildSelector();
        buildSelector = new StatusBuildSelector(stableBuildOnly);
    }
    
    public void upstreamBuildSelector(boolean useLastSuccessfulAsFallback) {
        upstreamBuildSelector(useLastSuccessfulAsFallback, "Global", false);
    }
    
    public void upstreamBuildSelector(boolean useLastSuccessfulAsFallback, Object upstreamFilterStrategy) {
        upstreamBuildSelector(useLastSuccessfulAsFallback, upstreamFilterStrategy, false);
    }
    
    public void upstreamBuildSelector(boolean useLastSuccessfulAsFallback, Object upstreamFilterStrategy, boolean allowUpstreamDependencies) {
        guardSingleBuildSelector();
        TriggeredBuildSelector.UpstreamFilterStrategy strategy;
        if ("Global".equals(upstreamFilterStrategy.toString()))
        {
            strategy = TriggeredBuildSelector.UpstreamFilterStrategy.UseGlobalSetting;
        }
        else if ("Newest".equals(upstreamFilterStrategy.toString()))
        {
            strategy = TriggeredBuildSelector.UpstreamFilterStrategy.UseNewest;
        }
        else if ("Oldest".equals(upstreamFilterStrategy.toString()))
        {
            strategy = TriggeredBuildSelector.UpstreamFilterStrategy.UseOldest;
        }
        else
        {
            throw new IllegalArgumentException("Valid values for upstream filter strategy are: Global, Newest, Oldest.");
        }
        buildSelector = new TriggeredBuildSelector(useLastSuccessfulAsFallback, strategy, allowUpstreamDependencies);
    }
    
    private void guardSingleBuildSelector() {
        if (buildSelector != null) {
            throw new IllegalArgumentException("Only one build selector is allowed per Azure Storage Download.");
        }
    }
    
    public void storageAccountName(Object value) {
        storageAccountName = value.toString();
    }
    
    public void containerName(Object value) {
        containerName = value.toString();
    }
    
    public void includeFilesPattern(Object value) {
        includeFilesPattern = value.toString();
    }
    
    public void excludeFilesPattern(Object value) {
        excludeFilesPattern = value.toString();
    }
    
    public void downloadDirectoryLocation(Object value) {
        downloadDirectoryLocation = value.toString();
    }
    
    public void flattenDirectories(boolean value) {
        flattenDirectories = value;
    }
    
    public void includeArchiveZips(boolean value) {
        includeArchiveZips = value;
    }
    
    public void projectName(Object value) {
        projectName = value.toString();
    }
}
