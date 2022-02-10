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

import com.azure.core.credential.AzureSasCredential;
import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.file.share.ShareClient;
import com.azure.storage.file.share.ShareDirectoryClient;
import com.azure.storage.file.share.ShareFileClient;
import com.azure.storage.file.share.ShareServiceClient;
import com.azure.storage.file.share.ShareServiceClientBuilder;
import com.azure.storage.file.share.models.ShareFileItem;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.helper.Constants;
import com.microsoftopentechnologies.windowsazurestorage.service.model.PartialBlobProperties;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadServiceData;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadType;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.remoting.VirtualChannel;
import hudson.util.DirScanner;
import io.jenkins.plugins.azuresdk.HttpClientRetriever;
import jenkins.MasterToSlaveFileCallable;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.http.HttpStatus;

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
        FilePath workspace = getServiceData().getRemoteWorkspace();
        try {
            final ShareClient fileShare = getCloudFileShare();
            UploadType uploadType = serviceData.getUploadType();
            if (uploadType == UploadType.INDIVIDUAL || uploadType == UploadType.BOTH) {
                cleanupFileShare(fileShare);
            }

            List<UploadObject> uploadObjects = new ArrayList<>();

            for (FilePath src : paths) {
                final String filePath = getItemPath(src, embeddedVP, serviceData);
                ShareDirectoryClient rootDirectoryClient = fileShare.getRootDirectoryClient();
                final ShareFileClient cloudFile = rootDirectoryClient.getFileClient(filePath);
                ensureDirExist(fileShare, filePath);

                UploadObject uploadObject = generateUploadObject(src, serviceData.getStorageAccountInfo(),
                        cloudFile, fileShare.getShareName(), null, updateMetadata(new HashMap<>()));
                uploadObjects.add(uploadObject);
            }

            List<UploadResult> results = workspace
                    .act(new UploadOnAgent(Jenkins.get().getProxy(), uploadObjects));

            updateAzureBlobs(results, serviceData.getIndividualBlobs());
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new WAStorageException("fail to upload individual files to azure file storage", e);
        }
    }

    private UploadObject generateUploadObject(FilePath path, StorageAccountInfo accountInfo,
                                              ShareFileClient client, String shareName,
                                              PartialBlobProperties properties,
                                              Map<String, String> metadata)
            throws MalformedURLException, URISyntaxException {
        String sas = generateWriteSASURL(accountInfo, client.getFilePath(), Constants.FILE_STORAGE, shareName);

        return new UploadObject(client.getFilePath(), path, client.getFileUrl(), sas, Constants.BLOB_STORAGE,
                client.getAccountName(), shareName, properties, metadata);
    }

    static final class UploadOnAgent extends MasterToSlaveFileCallable<List<UploadResult>> {
        private static final Logger LOGGER = Logger.getLogger(UploadOnAgent.class.getName());
        private static final int ERROR_ON_UPLOAD = 500;

        private final ProxyConfiguration proxy;
        private final List<UploadObject> uploadObjects;

        UploadOnAgent(ProxyConfiguration proxy, List<UploadObject> uploadObjects) {
            this.proxy = proxy;
            this.uploadObjects = uploadObjects;
        }

        private ShareServiceClient getFileShareClient(UploadObject uploadObject) {
            return new ShareServiceClientBuilder()
                    .credential(new AzureSasCredential(uploadObject.getSas()))
                    .httpClient(HttpClientRetriever.get(proxy))
                    .endpoint(uploadObject.getUrl())
                    .buildClient();
        }

        @Override
        public List<UploadResult> invoke(File f, VirtualChannel channel) {
            return uploadObjects.parallelStream()
                    .map(uploadObject -> {
                        ShareServiceClient fileShareClient = getFileShareClient(uploadObject);

                        ShareClient shareClient = fileShareClient
                                .getShareClient(uploadObject.getContainerOrShareName());
                        ShareFileClient fileClient = shareClient.getFileClient(uploadObject.getName());
                        return uploadCloudFile(fileClient, uploadObject);

                    })
                    .collect(Collectors.toList());
        }

        private UploadResult uploadCloudFile(ShareFileClient fileClient, UploadObject uploadObject) {
            long startTime = System.currentTimeMillis();
            File file = new File(uploadObject.getSrc().getRemote());
            try {
                long bytes = Files.size(file.toPath());
                fileClient.create(bytes);

                fileClient.uploadFromFile(file.getAbsolutePath());

                long endTime = System.currentTimeMillis();

                return new UploadResult(HttpStatus.SC_CREATED, null,
                        uploadObject.getName(),
                        uploadObject.getUrl(), file.length(), uploadObject.getStorageType(),
                        startTime, endTime);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed uploading file", e);
                return new UploadResult(ERROR_ON_UPLOAD,
                        null,
                        file.getName(),
                        uploadObject.getUrl(),
                        file.length(),
                        uploadObject.getStorageType(),
                        startTime,
                        0
                );
            }
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
