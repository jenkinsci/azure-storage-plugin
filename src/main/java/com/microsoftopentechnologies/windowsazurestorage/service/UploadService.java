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

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.SharedAccessBlobPermissions;
import com.microsoft.azure.storage.file.CloudFile;
import com.microsoft.azure.storage.file.FileRequestOptions;
import com.microsoft.azure.storage.file.SharedAccessFilePermissions;
import com.microsoft.jenkins.azurecommons.telemetry.AppInsightsConstants;
import com.microsoft.jenkins.azurecommons.telemetry.AppInsightsUtils;
import com.microsoftopentechnologies.windowsazurestorage.AzureBlob;
import com.microsoftopentechnologies.windowsazurestorage.AzureBlobMetadataPair;
import com.microsoftopentechnologies.windowsazurestorage.AzureStoragePlugin;
import com.microsoftopentechnologies.windowsazurestorage.Messages;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.helper.Constants;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;
import com.microsoftopentechnologies.windowsazurestorage.service.model.PartialBlobProperties;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadServiceData;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadType;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import jenkins.model.Jenkins;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
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
    private static final Logger LOGGER = Logger.getLogger(UploadService.class.getName());
    protected static final String ZIP_FOLDER_NAME = "artifactsArchive";
    protected static final String ZIP_NAME = "archive.zip";
    protected static final String UPLOAD = "Upload";
    protected static final String UPLOAD_FAILED = "UploadFailed";
    private static final int UPLOAD_THREAD_COUNT = 16;
    private static final int KEEP_ALIVE_TIME = 1;
    private static final int TIME_OUT = 1;
    private static final TimeUnit TIME_OUT_UNIT = TimeUnit.DAYS;
    private static final CloseableHttpClient CLIENT;

    private AtomicInteger filesUploaded = new AtomicInteger(0);
    private ExecutorService executorService = new ThreadPoolExecutor(UPLOAD_THREAD_COUNT, UPLOAD_THREAD_COUNT,
            KEEP_ALIVE_TIME, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());

    static {
        HttpClientBuilder httpClientBuilder;
        try {
            httpClientBuilder = HttpClientBuilder.create()
                    .setConnectionManager(new PoolingHttpClientConnectionManager())
                    .setRetryHandler(new DefaultHttpRequestRetryHandler());

            Jenkins jenkinsInstance = Utils.getJenkinsInstance();
            if (jenkinsInstance != null) {
                ProxyConfiguration proxyConfig = jenkinsInstance.proxy;
                if (proxyConfig != null) {
                    HttpHost proxy = new HttpHost(proxyConfig.name, proxyConfig.port, HttpHost.DEFAULT_SCHEME_NAME);
                    httpClientBuilder.setProxy(proxy);
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, null, t);
            throw t;
        }
        CLIENT = httpClientBuilder.build();
    }

    protected UploadService(UploadServiceData serviceData) {
        super(serviceData);
    }

    /**
     * A task to upload Azure Share Files by storage sdk.
     */
    class FileUploadThread implements Runnable {
        private CloudFile uploadItem;
        private FilePath filePath;
        private List<AzureBlob> azureBlobs;

        /**
         * Default constructor of FileUploadThread.
         *
         * @param uploadItem Target Share File which will be uploaded to.
         * @param filePath   The local file needed to be uploaded.
         * @param azureBlobs Records of uploaded files.
         */
        FileUploadThread(CloudFile uploadItem, FilePath filePath, List<AzureBlob> azureBlobs) {
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
                        uploadItem.getName(),
                        uploadItem.getUri().toString().replace("http://", "https://"),
                        uploadedFileHash,
                        filePath.length(),
                        Constants.FILE_STORAGE);
                filesUploaded.addAndGet(1);
                azureBlobs.add(azureBlob);
            } catch (WAStorageException | InterruptedException | IOException e) {
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
        private List<UploadObject> uploadObjects;

        UploadOnSlave(List<UploadObject> uploadObjects) {
            this.uploadObjects = uploadObjects;
        }

        @Override
        public List<UploadResult> invoke(File f, VirtualChannel channel)
                throws IOException, InterruptedException {
            ExecutorService agentExecutorService = new ThreadPoolExecutor(UPLOAD_THREAD_COUNT, UPLOAD_THREAD_COUNT,
                    KEEP_ALIVE_TIME, TimeUnit.SECONDS, new LinkedBlockingDeque<>());

            List<Future<UploadResult>> futures = new ArrayList<>();
            for (UploadObject uploadObject : uploadObjects) {

                Future<UploadResult> future = agentExecutorService.submit(new UploadThread(uploadObject));
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
     * Update Jenkins master's records of uploaded files.
     *
     * @param results    Response from the the agents.
     * @param azureBlobs Records of the uploaded files.
     * @throws WAStorageException throw exceptions when failing to fetch the the response.
     */
    protected void updateAzureBlobs(List<UploadResult> results,
                                    List<AzureBlob> azureBlobs) throws WAStorageException {
        for (UploadResult result : results) {
            if (result.getStatusCode() == HttpStatus.SC_CREATED) {
                AzureBlob azureBlob = new AzureBlob(
                        result.getName(),
                        result.getUrl(),
                        result.getFileHash(),
                        result.getByteSize(),
                        result.getStorageType());

                filesUploaded.addAndGet(1);
                azureBlobs.add(azureBlob);

                long interval = result.getEndTime() - result.getStartTime();
                println(Messages.UploadService_https_uploaded(result.getUrl(), getTime(interval)));
            }
        }
    }

    protected String generateWriteSASURL(StorageAccountInfo storageAccountInfo, String fileName,
                                         String storageType, String name) throws Exception {
        if (storageType.equalsIgnoreCase(Constants.BLOB_STORAGE)) {
            return AzureUtils.generateBlobSASURL(storageAccountInfo, name, fileName,
                    EnumSet.of(SharedAccessBlobPermissions.WRITE));
        } else if (storageType.equalsIgnoreCase(Constants.FILE_STORAGE)) {
            return AzureUtils.generateFileSASURL(storageAccountInfo, name, fileName,
                    EnumSet.of(SharedAccessFilePermissions.WRITE));
        }
        throw new Exception("Unknown storage type. Please re-configure your job and build again.");
    }

    /**
     * A task to upload files to Azure Storage by using https.
     */
    static class UploadThread implements Callable<UploadResult> {
        private UploadObject uploadObject;
        private static final int BLOCK_SIZE = 100 * 1024 * 1024;
        private static final String TEMP_FILE_PATTERN = "%s/%ssplit.%d";
        private static final Logger LOGGER = Logger.getLogger(UploadThread.class.getName());

        UploadThread(UploadObject uploadObject) {
            this.uploadObject = uploadObject;
        }

        static void readWrite(RandomAccessFile raf, BufferedOutputStream bw, long numBytes) throws IOException {
            byte[] buf = new byte[(int) numBytes];
            int val = raf.read(buf);
            if (val != -1) {
                bw.write(buf);
            }
        }

        private String getEncodedBlockId(int index) {
            String blockId = String.format("%05d", index);
            return Base64.getEncoder().encodeToString(blockId.getBytes(Charset.forName("UTF-8")));
        }

        @Override
        public UploadResult call() throws Exception {
            FilePath src = uploadObject.getSrc();
            File file = new File(src.getRemote());
            long length = file.length();
            List<String> blockIdList = new ArrayList<>();

            String md;
            try (InputStream is = src.read()) {
                md = DigestUtils.md5Hex(is);
            }

            ImmutablePair<Integer, String> response;
            long startTime = System.currentTimeMillis();
            if (length > BLOCK_SIZE) {
                String tempDirectoryName = uploadObject.getName().replace('/', '.');
                Path tempDirectory = Files.createTempDirectory(tempDirectoryName);

                try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                    int blockCount = (int) (length / BLOCK_SIZE);
                    long remainingBytes = length % BLOCK_SIZE;

                    // Put block blobs
                    for (int index = 0; index < blockCount; index++) {
                        String splitFileName = String.format(TEMP_FILE_PATTERN,
                                tempDirectory, src.getBaseName(), index);
                        try (BufferedOutputStream bw = new BufferedOutputStream(new FileOutputStream(splitFileName
                        ))) {
                            readWrite(raf, bw, BLOCK_SIZE);

                            String encodedBlockId = getEncodedBlockId(index);
                            blockIdList.add(encodedBlockId);

                            HttpEntity requestEntity = new FileEntity(new File(splitFileName));
                            putBlob(encodedBlockId, requestEntity);
                        }
                    }

                    // Put last block blob
                    String splitFileName = String.format(TEMP_FILE_PATTERN,
                            tempDirectory, src.getBaseName(), blockCount + 1);
                    if (remainingBytes > 0) {
                        try (BufferedOutputStream bw = new BufferedOutputStream(
                                new FileOutputStream(splitFileName))) {
                            readWrite(raf, bw, remainingBytes);

                            String encodedBlockId = getEncodedBlockId(blockCount + 1);
                            blockIdList.add(encodedBlockId);

                            HttpEntity requestEntity = new FileEntity(new File(splitFileName));
                            putBlob(encodedBlockId, requestEntity);
                        }
                    }
                }

                // put block list
                response = putBlobList(blockIdList);
                try {
                    FileUtils.deleteDirectory(tempDirectory.toFile());
                } catch (IOException e) {
                    LOGGER.warning(String.format("Failed to delete temporary directory %s, ignore.", tempDirectory));
                }
            } else {
                HttpEntity requestEntity = new FileEntity(new File(src.getRemote()));
                HttpPut method = generateBlobWriteMethod(uploadObject.getUrl(), uploadObject.getSas());
                method.setEntity(requestEntity);
                response = execute(method);
            }
            long endTime = System.currentTimeMillis();

            return new UploadResult(response.getLeft(), response.getRight(), md, uploadObject.getName(),
                    uploadObject.getUrl(), length, uploadObject.getStorageType(),
                    startTime, endTime);
        }

        private String generateBlockListBody(List<String> blockIds) throws WAStorageException {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            try {
                DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
                Document document = documentBuilder.newDocument();
                Element blockList = document.createElement("BlockList");
                for (String blockId : blockIds) {
                    Element latest = document.createElement("Latest");
                    latest.appendChild(document.createTextNode(blockId));
                    blockList.appendChild(latest);
                }
                document.appendChild(blockList);

                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                StringWriter writer = new StringWriter();
                transformer.transform(new DOMSource(document), new StreamResult(writer));
                return writer.getBuffer().toString();
            } catch (ParserConfigurationException | TransformerException e) {
                throw new WAStorageException("Failed to generate put block list xml file.");
            }
        }

        private ImmutablePair<Integer, String> putBlobList(List<String> blockIdList) throws WAStorageException {
            HttpPut putMethod = generateBlockListWriteMethod(uploadObject.getUrl(), uploadObject.getSas());
            String blockListBody = generateBlockListBody(blockIdList);
            putMethod.setEntity(new StringEntity(blockListBody, StandardCharsets.UTF_8));
            return execute(putMethod);
        }

        private ImmutablePair<Integer, String> putBlob(String blockId, HttpEntity requestEntity) throws
                WAStorageException, IOException, InterruptedException {
            FilePath src = uploadObject.getSrc();
            String storageType = uploadObject.getStorageType();
            String url = uploadObject.getUrl();
            String sas = uploadObject.getSas();
            String hashedStorageAcc = AppInsightsUtils.hash(uploadObject.getStorageAccount());

            ImmutablePair<Integer, String> response;
            if (Constants.BLOB_STORAGE.equals(storageType)) {
                HttpPut method = generateBlobWriteMethod(url, sas, blockId);
                method.setEntity(requestEntity);
                response = execute(method);
            } else {
                throw new WAStorageException("Now only support Azure Blob Service for https uploading.");
            }

            if (response.getLeft() == HttpStatus.SC_CREATED) {
                // send AI event.
                AzureStoragePlugin.sendEvent(AppInsightsConstants.AZURE_FILE_STORAGE, UPLOAD,
                        "StorageAccount", hashedStorageAcc,
                        "ContentLength", String.valueOf(src.length()));
            } else {
                AzureStoragePlugin.sendEvent(AppInsightsConstants.AZURE_FILE_STORAGE, UPLOAD_FAILED,
                        "StorageAccount", hashedStorageAcc,
                        "Message", response.getRight());
                throw new WAStorageException(String.format("Failed to upload %s with error code %d",
                        uploadObject.getName(), response.getLeft()));
            }
            return response;
        }

        /**
         * Execute target http method.
         *
         * @param method http method needs to be executed.
         * @return Pair of http code and response message.
         */
        private ImmutablePair<Integer, String> execute(HttpRequestBase method) {
            int code = 0;
            String responseBody = null;
            try {
                HttpResponse response = CLIENT.execute(method);
                code = response.getStatusLine().getStatusCode();
                HttpEntity entity = response.getEntity();
                responseBody = entity != null ? EntityUtils.toString(entity) : null;
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                method.releaseConnection();
            }
            responseBody = StringUtils.trimToEmpty(responseBody);
            return new ImmutablePair<>(code, responseBody);
        }

        private HttpPut generateBlockListWriteMethod(String url, String sas) {
            String sasUrl = String.format("%s?comp=blocklist&%s", url, sas);
            HttpPut method = new HttpPut(sasUrl);
            updateHeader(method);
            return method;
        }

        private void updateHeader(HttpPut method) {
            PartialBlobProperties blobProperties = uploadObject.blobProperties;
            method.addHeader("Cache-Control", blobProperties.getCacheControl());
            method.addHeader("x-ms-blob-content-type", blobProperties.getContentType());
            method.addHeader("x-ms-blob-content-encoding", blobProperties.getContentEncoding());
            method.addHeader("x-ms-blob-content-language", blobProperties.getContentLanguage());
            for (Map.Entry<String, String> node : uploadObject.getMetadata().entrySet()) {
                method.addHeader(String.format("x-ms-meta-%s", node.getKey()), node.getValue());
            }
        }

        private HttpPut generateBlobWriteMethod(String url, String sas) {
            String sasUrl = url + "?" + sas;
            return generateBlobWriteMethod(sasUrl);
        }

        private HttpPut generateBlobWriteMethod(String url, String sas,
                                                String blockId) {
            String sasUrl = String.format("%s?comp=block&blockid=%s&%s", url, blockId, sas);
            return generateBlobWriteMethod(sasUrl);
        }

        private HttpPut generateBlobWriteMethod(String sasUrl) {
            HttpPut method = new HttpPut(sasUrl);
            method.addHeader("x-ms-blob-type", "BlockBlob");
            updateHeader(method);
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

        println(Messages.WAStoragePublisher_container_name(serviceData.getContainerName()));
        println(Messages.WAStoragePublisher_share_name(serviceData.getFileShareName()));
        println(Messages.WAStoragePublisher_filepath(serviceData.getFilePath()));
        println(Messages.WAStoragePublisher_virtualpath(serviceData.getVirtualPath()));
        println(Messages.WAStoragePublisher_excludepath(serviceData.getExcludedFilesPath()));
        int filesNeedUpload = 0; // Counter to track no. of files that are need uploaded
        int filesCount = 0;
        try {
            final FilePath workspacePath = serviceData.getRemoteWorkspace();
            println(Messages.WAStoragePublisher_uploading());

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
            println(Messages.WAStoragePublisher_files_need_upload_count(filesNeedUpload));
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

    protected String uploadCloudFile(CloudFile cloudFile, FilePath localPath)
            throws WAStorageException {
        String hashedStorageAcc = AppInsightsUtils.hash(cloudFile.getServiceClient().getCredentials().getAccountName());
        try {
            cloudFile.setMetadata(updateMetadata(cloudFile.getMetadata()));

            final MessageDigest md = DigestUtils.getMd5Digest();
            long startTime = System.currentTimeMillis();
            try (InputStream inputStream = localPath.read();
                 DigestInputStream digestInputStream = new DigestInputStream(inputStream, md)) {
                cloudFile.upload(
                        digestInputStream,
                        localPath.length(),
                        null,
                        new FileRequestOptions(),
                        Utils.updateUserAgent(localPath.length()));
            }
            long endTime = System.currentTimeMillis();

            // send AI event.
            AzureStoragePlugin.sendEvent(AppInsightsConstants.AZURE_FILE_STORAGE, UPLOAD,
                    "StorageAccount", hashedStorageAcc,
                    "ContentLength", String.valueOf(localPath.length()));

            println("Uploaded blob with uri " + cloudFile.getUri() + " in " + getTime(endTime - startTime));
            return DatatypeConverter.printHexBinary(md.digest());
        } catch (IOException | InterruptedException | StorageException | URISyntaxException e) {
            AzureStoragePlugin.sendEvent(AppInsightsConstants.AZURE_FILE_STORAGE, UPLOAD_FAILED,
                    "StorageAccount", hashedStorageAcc,
                    "Message", e.getMessage());
            throw new WAStorageException("fail to upload file to azure file storage", e);
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

    /**
     * Convert the path on local file sytem to relative path on azure storage.
     *
     * @param path       the local path
     * @param embeddedVP the embedded virtual path
     * @return
     */
    protected String getItemPath(FilePath path, String embeddedVP)
            throws IOException, InterruptedException {
        final UploadServiceData serviceData = getServiceData();
        final URI workspaceURI = serviceData.getRemoteWorkspace().toURI();

        // Remove the workspace bit of this path
        final URI srcURI = workspaceURI.relativize(path.toURI());
        final String srcURIPath = srcURI.getPath();
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

    protected HashMap<String, String> updateMetadata(HashMap<String, String> metadata)
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
