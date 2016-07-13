package com.microsoftopentechnologies.windowsazurestorage.jobdsl;

import javaposse.jobdsl.dsl.Context;

/**
 *
 * @author crummel
 */
public class AzureStorageUploadContext implements Context {
    String storageAccountName;
    String containerName;
    String filesToUpload;
    String excludeFilesPattern;
    String virtualPath;
    boolean makeContainerPublic;
    boolean cleanUpContainer;
    boolean allowAnonymousAccess;
    boolean uploadArtifactsOnlyIfSuccessful;
    boolean doNotFailIfArchivingReturnsNothing;
    boolean uploadZips;
    boolean doNotUploadIndividualFiles;
    boolean manageArtifacts;
    
    public void storageAccountName(Object value) {
        storageAccountName = value.toString();
    }
    
    public void containerName(Object value) {
        containerName = value.toString();
    }
    
    public void filesToUpload(Object value) {
        filesToUpload = value.toString();
    }
    
    public void excludeFilesPattern(Object value) {
        excludeFilesPattern = value.toString();
    }
    
    public void virtualPath(Object value) {
        virtualPath = value.toString();
    }
    
    public void makeContainerPublic(boolean value) {
        makeContainerPublic = value;
    }
    
    public void cleanUpContainer(boolean value) {
        cleanUpContainer = value;
    }
    
    public void allowAnonymousAccess(boolean value) {
        allowAnonymousAccess = value;
    }
    
    public void uploadArtifactsOnlyIfSuccessful(boolean value) {
        uploadArtifactsOnlyIfSuccessful = value;
    }
    
    public void doNotFailIfArchivingReturnsNothing(boolean value) {
        doNotFailIfArchivingReturnsNothing = value;
    }
    
    public void uploadZips(boolean value) {
        uploadZips = value;
    }
    
    public void doNotUploadIndividualFiles(boolean value) {
        doNotUploadIndividualFiles = value;
    }
    
    public void manageArtifacts(boolean value) {
        manageArtifacts = value;
    }
}
