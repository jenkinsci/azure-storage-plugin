/*
 Copyright 2017 Microsoft Open Technologies, Inc.

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

package com.microsoftopentechnologies.windowsazurestorage.service;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.file.share.ShareClient;
import com.azure.storage.file.share.ShareDirectoryClient;
import com.azure.storage.file.share.ShareFileClient;
import com.azure.storage.file.share.ShareServiceClient;
import com.azure.storage.file.share.models.ShareFileItem;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadServiceData;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadType;
import hudson.FilePath;
import hudson.util.DirScanner;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class UploadToFileService extends UploadService {
    public UploadToFileService(UploadServiceData serviceData) {
        super(serviceData);
    }

    @Override
    protected void uploadIndividuals(String embeddedVP, FilePath[] paths, FilePath workspace)
            throws WAStorageException {
        uploadIndividuals(embeddedVP, paths);
    }

    @Override
    protected void uploadIndividuals(String embeddedVP, FilePath[] paths) throws WAStorageException {
        final UploadServiceData serviceData = getServiceData();
        try {
            final ShareClient fileShare = getCloudFileShare();
            UploadType uploadType = serviceData.getUploadType();
            if (uploadType == UploadType.INDIVIDUAL || uploadType == UploadType.BOTH) {
                cleanupFileShare(fileShare);
            }

            for (FilePath src : paths) {
                final String filePath = getItemPath(src, embeddedVP, serviceData);
                ShareDirectoryClient rootDirectoryClient = fileShare.getRootDirectoryClient();
                final ShareFileClient cloudFile = rootDirectoryClient.getFileClient(filePath);
                ensureDirExist(fileShare, filePath);
                getExecutorService().submit(new FileUploadThread(cloudFile, src, serviceData.getIndividualBlobs()));
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new WAStorageException("fail to upload individual files to azure file storage", e);
        }
    }

    @Override
    protected void uploadArchive(String archiveIncludes) throws WAStorageException {
        final UploadServiceData serviceData = getServiceData();
        try {
            final ShareClient fileShare = getCloudFileShare();
            if (serviceData.getUploadType() == UploadType.ZIP) {
                cleanupFileShare(fileShare);
            }

            final FilePath workspacePath = serviceData.getRemoteWorkspace();
            // Create a temp dir for the upload
            final FilePath tempDir = workspacePath.createTempDir(ZIP_FOLDER_NAME, null);
            final FilePath zipPath = tempDir.child(ZIP_NAME);

            // zip included files into archive.zip file.
            final DirScanner.Glob globScanner = new DirScanner.Glob(archiveIncludes, excludedFilesAndZip());
            workspacePath.zip(zipPath.write(), globScanner);

            // When uploading the zip, do not add in the tempDir to the azure
            String azureFileName = zipPath.getName();
            if (!StringUtils.isBlank(serviceData.getVirtualPath())) {
                azureFileName = serviceData.getVirtualPath() + azureFileName;
            }

            final ShareFileClient cloudFile = fileShare.getRootDirectoryClient().getFileClient(azureFileName);
            Future<?> archiveUploadFuture = getExecutorService().submit(new FileUploadThread(cloudFile,
                    zipPath, serviceData.getArchiveBlobs()));
            archiveUploadFuture.get();
            tempDir.deleteRecursive();
        } catch (IOException | InterruptedException | URISyntaxException | ExecutionException e) {
            throw new WAStorageException("Fail to upload individual files to blob", e);
        }
    }

    private ShareClient getCloudFileShare() throws URISyntaxException, MalformedURLException {
        final UploadServiceData serviceData = getServiceData();
        final ShareServiceClient cloudStorageAccount =
                AzureUtils.getShareClient(serviceData.getStorageAccountInfo());
        final ShareClient fileShare = cloudStorageAccount.getShareClient(serviceData.getFileShareName());
        if (!fileShare.exists()) {
            fileShare.create();
        }
        return fileShare;
    }

    private void cleanupFileShare(ShareClient fileShare) throws URISyntaxException {
        final UploadServiceData serviceData = getServiceData();
        // Delete previous contents if cleanup is needed
        if (serviceData.isCleanUpContainerOrShare() && fileShare.exists()) {
            println("Clean up existing files in file share " + serviceData.getFileShareName());
            deleteFiles(fileShare, fileShare.getRootDirectoryClient().listFilesAndDirectories());
        } else if (serviceData.isCleanUpVirtualPath()
                && StringUtils.isNotBlank(serviceData.getVirtualPath()) && fileShare.exists()) {
            ShareDirectoryClient directory = fileShare.getDirectoryClient(serviceData.getVirtualPath());
            if (directory.exists()) {
                println("Clean up existing files in file share directory " + serviceData.getVirtualPath());
                deleteFiles(fileShare, directory.listFilesAndDirectories());
            }
        }
    }

    private void ensureDirExist(ShareClient shareClient, String filePath) {
        ShareDirectoryClient rootDirectoryClient = shareClient.getRootDirectoryClient();
        if (!rootDirectoryClient.exists()) {
            rootDirectoryClient.create();
        }

        String[] directories = filePath.split("/");
        if (directories.length > 1) {
            // remove filename
            if (!filePath.endsWith("/")) {
                directories = Arrays.copyOf(directories, directories.length - 1);
            }

            for (int i = 0; i < directories.length; i++) {
                String path = getPath(directories, i);
                ShareDirectoryClient directoryClient = shareClient.getDirectoryClient(path);
                if (!directoryClient.exists()) {
                    directoryClient.create();
                }
            }
        }
    }

    private String getPath(String[] directories, int i) {
        String[] partialDirPath = Arrays.copyOfRange(directories, 0, i + 1);
        return String.join("/", partialDirPath);
    }

    private void deleteFiles(ShareClient fileShare, PagedIterable<ShareFileItem> fileItems)  {
        for (final ShareFileItem fileItem : fileItems) {
            if (fileItem.isDirectory()) {
                final ShareDirectoryClient directory = fileShare.getDirectoryClient(fileItem.getName());
                deleteFiles(fileShare, directory.listFilesAndDirectories());
            } else {
                fileShare.getFileClient(fileItem.getName()).delete();
            }
        }
    }
}
