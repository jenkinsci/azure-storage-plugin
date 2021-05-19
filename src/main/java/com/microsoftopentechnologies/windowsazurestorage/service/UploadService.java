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
import com.azure.core.http.rest.Response;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.BlobUrlParts;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.options.BlobUploadFromFileOptions;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.file.share.ShareFileClient;
import com.azure.storage.file.share.models.ShareFileUploadInfo;
import com.azure.storage.file.share.sas.ShareFileSasPermission;
import com.microsoftopentechnologies.windowsazurestorage.AzureBlob;
import com.microsoftopentechnologies.windowsazurestorage.AzureBlobMetadataPair;
import com.microsoftopentechnologies.windowsazurestorage.Messages;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.helper.Constants;
import com.microsoftopentechnologies.windowsazurestorage.service.model.PartialBlobProperties;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadServiceData;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadType;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.remoting.VirtualChannel;
import io.jenkins.plugins.azuresdk.HttpClientRetriever;
import jenkins.MasterToSlaveFileCallable;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;

import javax.xml.bind.DatatypeConverter;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class UploadService extends StoragePluginService<UploadServiceData> {
    protected static final String ZIP_FOLDER_NAME = "artifactsArchive";
    protected static final String ZIP_NAME = "archive.zip";
    private static final int UPLOAD_THREAD_COUNT = 16;
    private static final int KEEP_ALIVE_TIME = 1;
    private static final int TIME_OUT = 1;
    private static final TimeUnit TIME_OUT_UNIT = TimeUnit.DAYS;
    private static final Logger LOGGER = Logger.getLogger(UploadService.class.getName());

    private AtomicInteger filesUploaded = new AtomicInteger(0);
    private ExecutorService executorService = new ThreadPoolExecutor(UPLOAD_THREAD_COUNT, UPLOAD_THREAD_COUNT,
            KEEP_ALIVE_TIME, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());

    protected UploadService(UploadServiceData serviceData) {
        super(serviceData);
    }

    /**
     * A task to upload Azure Share Files by storage sdk.
     */
    class FileUploadThread implements Runnable {
        private ShareFileClient uploadItem;
        private FilePath filePath;
        private List<AzureBlob> azureBlobs;

        /**
         * Default constructor of FileUploadThread.
         *
         * @param uploadItem Target Share File which will be uploaded to.
         * @param filePath   The local file needed to be uploaded.
         * @param azureBlobs Records of uploaded files.
         */
        FileUploadThread(ShareFileClient uploadItem, FilePath filePath, List<AzureBlob> azureBlobs) {
            this.uploadItem = uploadItem;
            this.filePath = filePath;
            this.azureBlobs = azureBlobs;
        }

        @Override
        public void run() {
            try {
                AzureBlob azureBlob;
                String uploadedFileHash = uploadCloudFile(uploadItem, filePath);
                azureBlob = new AzureBlob(
                        uploadItem.getShareName(),
                        uploadItem.getFileUrl(),
                        uploadedFileHash,
                        filePath.length(),
                        Constants.FILE_STORAGE,
                        getServiceData().getCredentialsId()
                        );
                filesUploaded.addAndGet(1);
                azureBlobs.add(azureBlob);
            } catch (WAStorageException | InterruptedException | IOException e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                final String message = Messages.AzureStorageBuilder_download_err(
                        getServiceData().getStorageAccountInfo().getStorageAccName()) + ":" + e.getMessage();
                e.printStackTrace(error(message));
                println(message);
                setRunUnstable();
            }
        }
    }

    /**
     * Data object for https uploading command.
     */
    protected static class UploadObject implements Serializable {
        private static final long serialVersionUID = -5342773517251888877L;
        private String name;
        private FilePath src;
        private String url;
        private String sas;
        private String storageType;
        private String storageAccount;
        private PartialBlobProperties blobProperties;
        private Map<String, String> metadata;

        /**
         * Default Constructor for UploadObject.
         *
         * @param name           The name of the uploaded object.
         * @param src            The location of the uploaded object.
         * @param url            The target the url where the file will be uploaded to.
         * @param sas            Share Access Signature for upload authentication.
         * @param storageType    The storage type for the object, now only support Azure Blob.
         * @param storageAccount Storage account name for data tracing.
         */
        public UploadObject(String name, FilePath src, String url, String sas, String storageType,
                            String storageAccount, PartialBlobProperties blobProperties, Map<String, String> metadata) {
            this.name = name;
            this.src = src;
            this.url = url;
            this.sas = sas;
            this.storageType = storageType;
            this.storageAccount = storageAccount;
            this.blobProperties = blobProperties;
            this.metadata = metadata;
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }

        public String getSas() {
            return sas;
        }

        public String getStorageType() {
            return storageType;
        }

        public FilePath getSrc() {
            return src;
        }

        public String getStorageAccount() {
            return storageAccount;
        }

        public PartialBlobProperties getBlobProperties() {
            return blobProperties;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }
    }

    /**
     * Data object for https uploading result.
     */
    protected static class UploadResult implements Serializable {
        private static final long serialVersionUID = -3112548564900823521L;
        private int statusCode;
        private String responseBody;
        private String fileHash;
        private String name;
        private String url;
        private long byteSize;
        private String storageType;
        private long startTime;
        private long endTime;

        /**
         * Default constructor for UploadResult.
         *
         * @param statusCode   Status code for uploading task, the same as the http response code.
         * @param responseBody Response from the server side. Provide detailed information when the task fails.
         * @param fileHash     The hash code for the uploaded object, calculate on the agent
         *                     to save resources on master.
         * @param name         The name of the uploaded object.
         * @param url          The target url of the uploaded object.
         * @param byteSize     The byte size of the uploaded object.
         * @param storageType  Storage type of the uploaded object.
         * @param startTime    Start time of the uploading task.
         * @param endTime      End time of the uploading task.
         */
        public UploadResult(int statusCode, String responseBody, String fileHash, String name,
                            String url, long byteSize, String storageType, long startTime, long endTime) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
            this.fileHash = fileHash;
            this.name = name;
            this.url = url;
            this.byteSize = byteSize;
            this.storageType = storageType;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getResponseBody() {
            return responseBody;
        }

        public String getFileHash() {
            return fileHash;
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }

        public long getByteSize() {
            return byteSize;
        }

        public String getStorageType() {
            return storageType;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getEndTime() {
            return endTime;
        }
    }

    /**
     * A task which will be executed on Jenkins agents. It will upload targeted files to
     * Azure Storage Service using https.
     */
    static final class UploadOnSlave extends MasterToSlaveFileCallable<List<UploadResult>> {
        private static final long serialVersionUID = -7284277515594786765L;
        private final ProxyConfiguration proxy;
        private final List<UploadObject> uploadObjects;

        UploadOnSlave(ProxyConfiguration proxy, List<UploadObject> uploadObjects) {
            this.proxy = proxy;
            this.uploadObjects = uploadObjects;
        }

        @Override
        public List<UploadResult> invoke(File f, VirtualChannel channel)
                throws IOException, InterruptedException {
            ExecutorService agentExecutorService = new ThreadPoolExecutor(UPLOAD_THREAD_COUNT, UPLOAD_THREAD_COUNT,
                    KEEP_ALIVE_TIME, TimeUnit.SECONDS, new LinkedBlockingDeque<>());

            List<Future<UploadResult>> futures = new ArrayList<>();
            for (UploadObject uploadObject : uploadObjects) {

                Future<UploadResult> future = agentExecutorService.submit(
                        new UploadThread(proxy, uploadObject)
                );
                futures.add(future);
            }

            List<UploadResult> results = new ArrayList<>();
            try {
                for (Future<UploadResult> future : futures) {
                    results.add(future.get());
                }
            } catch (ExecutionException e) {
                throw new IOException(e);
            } finally {
                agentExecutorService.shutdownNow();
            }
            return results;
        }
    }

    /**
     * Update Jenkins controller's records of uploaded files.
     *
     * @param results    Response from the the agents.
     * @param azureBlobs Records of the uploaded files.
     * @throws WAStorageException throw exceptions when failing to fetch the the response.
     */
    protected void updateAzureBlobs(List<UploadResult> results,
                                    List<AzureBlob> azureBlobs) throws WAStorageException {
        for (UploadResult result : results) {
            if (result.getStatusCode() == HttpStatus.SC_CREATED) {
                UploadServiceData serviceData = getServiceData();
                AzureBlob azureBlob = new AzureBlob(
                        result.getName(),
                        result.getUrl(),
                        result.getFileHash(),
                        result.getByteSize(),
                        result.getStorageType(),
                        serviceData.getCredentialsId());

                filesUploaded.addAndGet(1);
                azureBlobs.add(azureBlob);

                long interval = result.getEndTime() - result.getStartTime();

                if (serviceData.isVerbose()) {
                    println(Messages.UploadService_https_uploaded(result.getUrl(), getTime(interval)));
                }
            }
        }
    }

    protected String generateWriteSASURL(StorageAccountInfo storageAccountInfo, String fileName,
                                         String storageType, String name) throws Exception {
        if (storageType.equalsIgnoreCase(Constants.BLOB_STORAGE)) {

            return AzureUtils.generateBlobSASURL(storageAccountInfo, name, fileName,
                    new BlobSasPermission().setWritePermission(true));
        } else if (storageType.equalsIgnoreCase(Constants.FILE_STORAGE)) {
            return AzureUtils.generateFileSASURL(storageAccountInfo, name, fileName,
                    new ShareFileSasPermission().setWritePermission(true));
        }
        throw new Exception("Unknown storage type. Please re-configure your job and build again.");
    }

    /**
     * A task to upload files to Azure Storage by using https.
     */
    static class UploadThread implements Callable<UploadResult> {
        private final ProxyConfiguration proxyConfiguration;
        private UploadObject uploadObject;

        UploadThread(ProxyConfiguration proxyConfiguration, UploadObject uploadObject) {
            this.proxyConfiguration = proxyConfiguration;
            this.uploadObject = uploadObject;
        }

        private BlobServiceClient getBlobServiceClient() {
            return new BlobServiceClientBuilder()
                    .credential(new AzureSasCredential(uploadObject.getSas()))
                    .httpClient(HttpClientRetriever.get(proxyConfiguration))
                    .endpoint(uploadObject.getUrl())
                    .buildClient();
        }

        @Override
        public UploadResult call() {
            FilePath src = uploadObject.getSrc();
            File file = new File(src.getRemote());
            long length = file.length();

            BlobServiceClient blobServiceClient = getBlobServiceClient();

            BlobUrlParts blobUrlParts = BlobUrlParts.parse(uploadObject.getUrl());

            BlobContainerClient containerClient = blobServiceClient
                    .getBlobContainerClient(blobUrlParts.getBlobContainerName());

            BlobClient blockBlobClient = containerClient
                    .getBlobClient(uploadObject.getName());

            long startTime = System.currentTimeMillis();

            BlobUploadFromFileOptions options = new BlobUploadFromFileOptions(file.getAbsolutePath())
                    .setHeaders(getBlobHttpHeaders())
                    .setMetadata(uploadObject.getMetadata());
            Response<BlockBlobItem> block = blockBlobClient
                    .uploadFromFileWithResponse(options, null, Context.NONE);

            if (!uploadObject.getMetadata().isEmpty()) {
                blockBlobClient.setMetadata(uploadObject.getMetadata());
            }
            byte[] md5 = block.getValue().getContentMd5();
            long endTime = System.currentTimeMillis();

            return new UploadResult(block.getStatusCode(), null,
                    new String(md5, StandardCharsets.UTF_8),
                    uploadObject.getName(),
                    uploadObject.getUrl(), length, uploadObject.getStorageType(),
                    startTime, endTime);
        }

        private BlobHttpHeaders getBlobHttpHeaders() {
            PartialBlobProperties blobProperties = uploadObject.blobProperties;
            BlobHttpHeaders method = new BlobHttpHeaders();
            method.setCacheControl(blobProperties.getCacheControl());
            method.setContentType(blobProperties.getContentType());
            method.setContentEncoding(blobProperties.getContentEncoding());
            method.setContentLanguage(blobProperties.getContentLanguage());
            return method;

        }
    }

    protected abstract void uploadIndividuals(String embeddedVP, FilePath[] paths,
                                              FilePath workspace) throws WAStorageException;

    protected abstract void uploadIndividuals(String embeddedVP, FilePath[] paths) throws WAStorageException;

    protected abstract void uploadArchive(String archiveIncludes) throws WAStorageException;

    @Override
    public final int execute() throws WAStorageException {
        final UploadServiceData serviceData = getServiceData();

        if (serviceData.getUploadType() == UploadType.INVALID) {
            // no files are uploaded
            println("Upload type is INVALID, nothing to do.");
            return 0;
        }

        if (serviceData.isVerbose()) {
            println(Messages.WAStoragePublisher_container_name(serviceData.getContainerName()));
            println(Messages.WAStoragePublisher_share_name(serviceData.getFileShareName()));
            println(Messages.WAStoragePublisher_filepath(serviceData.getFilePath()));
            println(Messages.WAStoragePublisher_virtualpath(serviceData.getVirtualPath()));
            println(Messages.WAStoragePublisher_excludepath(serviceData.getExcludedFilesPath()));
            println(Messages.WAStoragePublisher_excludepath(serviceData.getRemovePrefixPath()));
        }
        int filesNeedUpload = 0; // Counter to track no. of files that are need uploaded
        int filesCount = 0;
        try {
            final FilePath workspacePath = serviceData.getRemoteWorkspace();
            if (serviceData.isVerbose()) {
                println(Messages.WAStoragePublisher_uploading());
            }

            final StringBuilder archiveIncludes = new StringBuilder();

            StringTokenizer strTokens = new StringTokenizer(serviceData.getFilePath(), FP_SEPARATOR);
            while (strTokens.hasMoreElements()) {
                String fileName = strTokens.nextToken();
                String embeddedVP = null;

                if (fileName != null && fileName.contains("::")) {
                    int embVPSepIndex = fileName.indexOf("::");

                    // Separate fileName and Virtual directory name.
                    if (fileName.length() > embVPSepIndex + 1) {
                        embeddedVP = fileName.substring(embVPSepIndex + 2);

                        if (StringUtils.isBlank(embeddedVP)) {
                            embeddedVP = null;
                        } else if (!embeddedVP.endsWith(Constants.FWD_SLASH)) {
                            embeddedVP = embeddedVP + Constants.FWD_SLASH;
                        }
                    }
                    fileName = fileName.substring(0, embVPSepIndex);
                }

                // List all the paths without the zip archives.
                FilePath[] paths = workspacePath.list(fileName, excludedFilesAndZip());
                FilePath[] uploadPaths = paths;

                if (serviceData.isOnlyUploadModifiedArtifacts()) {
                    List<FilePath> modifiedPathsList = new ArrayList<>();
                    for (FilePath path : paths) {
                        if (path.lastModified() >= serviceData.getRun().getStartTimeInMillis()) {
                            modifiedPathsList.add(path);
                        }
                    }
                    uploadPaths = modifiedPathsList.toArray(new FilePath[0]);
                }

                archiveIncludes.append(",").append(fileName);
                filesNeedUpload += uploadPaths.length;
                filesCount += paths.length;

                if (uploadPaths.length != 0 && serviceData.getUploadType() != UploadType.ZIP) {
                    // the uploadType is either INDIVIDUAL or BOTH, upload included individual files thus.
                    uploadIndividuals(embeddedVP, uploadPaths, workspacePath);
                }
            }

            // if uploadType is BOTH or ZIP, create an archive.zip and upload
            if (filesCount != 0 && (serviceData.getUploadType() != UploadType.INDIVIDUAL)) {
                uploadArchive(archiveIncludes.toString());
                // archive file should not be included in downloaded file count
                filesUploaded.decrementAndGet();
            }
            if (serviceData.isVerbose()) {
                println(Messages.WAStoragePublisher_files_need_upload_count(filesNeedUpload));
            }
            waitForUploadEnd();
        } catch (IOException | InterruptedException e) {
            throw new WAStorageException(e.getMessage(), e);
        }
        if (serviceData.getUploadType() != UploadType.ZIP && filesUploaded.get() != filesNeedUpload) {
            throw new WAStorageException(String.format("Only %d/%d files are successfully uploaded.",
                    filesUploaded.get(), filesNeedUpload));
        }

        println(Messages.WAStoragePublisher_files_uploaded_count(filesUploaded.get()));
        return filesCount;
    }

    protected void waitForUploadEnd() throws InterruptedException, WAStorageException {
        executorService.shutdown();
        boolean executionFinished = executorService.awaitTermination(TIME_OUT, TIME_OUT_UNIT);
        if (!executionFinished) {
            throw new WAStorageException(Messages.WAStoragePublisher_uploaded_timeout(TIME_OUT, TIME_OUT_UNIT));
        }
    }

    protected String uploadCloudFile(ShareFileClient fileClient, FilePath localPath)
            throws WAStorageException {
        long startTime = System.currentTimeMillis();
        File file = new File(localPath.getRemote());
        try (FileInputStream fis = new FileInputStream(file); BufferedInputStream bis = new BufferedInputStream(fis)) {
            long bytes = Files.size(file.toPath());
            fileClient.create(bytes);

            ShareFileUploadInfo response = fileClient.upload(bis, bis.available(), null);

            long endTime = System.currentTimeMillis();
            if (getServiceData().isVerbose()) {
                println("Uploaded file with uri " + fileClient.getFileUrl() + " in " + getTime(endTime - startTime));
            }
            return DatatypeConverter.printHexBinary(response.getContentMd5());
        } catch (Exception e) {
            throw new WAStorageException("Failed uploading file", e);
        }
    }

    protected String excludedFilesAndZip() {
        final UploadServiceData serviceData = getServiceData();
        // Make sure we exclude the tempPath from archiving.
        String excludesWithoutZip = "**/" + ZIP_FOLDER_NAME + "*/" + ZIP_NAME;
        if (serviceData.getExcludedFilesPath() != null) {
            excludesWithoutZip = serviceData.getExcludedFilesPath() + "," + excludesWithoutZip;
        }
        return excludesWithoutZip;
    }

    protected String removePrefix(URI srcURI, UploadServiceData serviceData) {
        String tmp = srcURI.getPath();
        String removePrefixPath = serviceData.getRemovePrefixPath();
        if (!StringUtils.isBlank(removePrefixPath)) {
            if (tmp.startsWith(removePrefixPath)) {
                String tmp1 = tmp.substring(removePrefixPath.length());
                if (serviceData.isVerbose()) {
                    println(Messages.UploadService_prefixRemoved(removePrefixPath, tmp, tmp1));
                }
                tmp = tmp1;
            }  else if (serviceData.isVerbose()) {
                println(Messages.UploadService_prefixNotRemoved(removePrefixPath, tmp));
            }
        }

        return tmp;
    }

    /**
     * Convert the path on local file system to relative path on azure storage.
     *
     * @param path       the local path
     * @param embeddedVP the embedded virtual path
     * @return
     */
    protected String getItemPath(FilePath path, String embeddedVP, UploadServiceData serviceData)
            throws IOException, InterruptedException {
        final URI workspaceURI = serviceData.getRemoteWorkspace().toURI();

        // Remove the workspace bit of this path
        final URI srcURI = workspaceURI.relativize(path.toURI());

        // Remove the prefix if specified
        final String srcURIPath = removePrefix(srcURI, serviceData);

        String prefix;
        if (StringUtils.isBlank(serviceData.getVirtualPath())) {
            prefix = "";
        } else {
            prefix = serviceData.getVirtualPath();
        }
        if (!StringUtils.isBlank(embeddedVP)) {
            prefix += embeddedVP;
        }

        return prefix + srcURIPath;
    }

    protected Map<String, String> updateMetadata(Map<String, String> metadata)
            throws IOException, InterruptedException {
        final UploadServiceData serviceData = getServiceData();
        final EnvVars env = serviceData.getRun().getEnvironment(serviceData.getTaskListener());

        if (serviceData.getAzureBlobMetadata() != null) {
            for (AzureBlobMetadataPair pair : serviceData.getAzureBlobMetadata()) {
                final String resolvedKey = Util.replaceMacro(pair.getKey(), env);
                final String resolvedValue = Util.replaceMacro(pair.getValue(), env);

                // Azure does not allow null, empty or whitespace metadata key
                if (resolvedKey == null || resolvedKey.trim().length() == 0) {
                    println("Ignoring blank metadata key");
                    continue;
                }

                // Azure does not allow null, empty or whitespace metadata value
                if (resolvedValue == null || resolvedValue.trim().length() == 0) {
                    println("Ignoring blank metadata value, key: " + resolvedKey);
                    continue;
                }

                metadata.put(resolvedKey, resolvedValue);
            }
        }

        return metadata;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }
}
