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

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.RetryNoRetry;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.*;
import com.microsoftopentechnologies.windowsazurestorage.WAStoragePublisher.UploadType;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.helper.Constants;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.DirScanner.Glob;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.springframework.util.AntPathMatcher;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.*;

public class WAStorageClient {

    /*
     * A random name for container name to test validity of storage account
     * details
     */
    private static final String TEST_CNT_NAME = "testcheckfromjenkins";
    private static final String BLOB = "blob";
    private static final String QUEUE = "queue";
    private static final String TABLE = "table";

    private static final String fpSeparator = ",";

    /**
     * This method validates Storage Account credentials by checking for a dummy
     * container existence.
     *
     * @param storageAccount
     * @return true if valid
     * @throws WAStorageException
     */
    public static boolean validateStorageAccount(
            final StorageAccountInfo storageAccount) throws WAStorageException {
        try {
            // Get container reference
            CloudBlobContainer container = getBlobContainerReference(
                    storageAccount, TEST_CNT_NAME, false, false, null);
            container.exists();

        } catch (Exception e) {
            throw new WAStorageException(Messages.Client_SA_val_fail());
        }
        return true;
    }

    /**
     * Returns reference of Windows Azure cloud blob container.
     *
     * @param storageAccount storage account info
     * @param containerName  name of the container
     * @param createCnt      Indicates if container needs to be created
     * @param allowRetry     sets retry policy
     * @param cntPubAccess   Permissions for container
     * @return reference of CloudBlobContainer
     * @throws URISyntaxException
     * @throws StorageException
     */
    private static CloudBlobContainer getBlobContainerReference(StorageAccountInfo storageAccount, String containerName,
                                                                boolean createCnt, boolean allowRetry, Boolean cntPubAccess)
            throws URISyntaxException, StorageException, MalformedURLException, IOException {

        CloudStorageAccount cloudStorageAccount;
        CloudBlobClient serviceClient;
        CloudBlobContainer container;
        StorageCredentialsAccountAndKey credentials;
        String accName = storageAccount.getStorageAccName();
        String blobURL = storageAccount.getBlobEndPointURL();

        credentials = new StorageCredentialsAccountAndKey(accName, storageAccount.getStorageAccountKey());

        if (Utils.isNullOrEmpty(blobURL) || blobURL.equals(Constants.DEF_BLOB_URL)) {
            cloudStorageAccount = new CloudStorageAccount(credentials);
        } else {
            String endpointSuffix = getEndpointSuffix(blobURL);
            if (Utils.isNullOrEmpty(endpointSuffix))
                throw new URISyntaxException(blobURL, "The blob endpoint is not correct!");
            cloudStorageAccount = new CloudStorageAccount(credentials, false, endpointSuffix);
        }

        serviceClient = cloudStorageAccount.createCloudBlobClient();
        if (!allowRetry) {
            // Setting no retry policy
            RetryNoRetry rnr = new RetryNoRetry();
            // serviceClient.setRetryPolicyFactory(rnr);
            serviceClient.getDefaultRequestOptions().setRetryPolicyFactory(rnr);
        }

        container = serviceClient.getContainerReference(containerName);

        boolean cntExists = container.exists();

        if (createCnt && !cntExists) {
            container.createIfNotExists(null, Utils.updateUserAgent());
        }

        // Apply permissions only if container is created newly
        if (!cntExists && cntPubAccess != null) {
            // Set access permissions on container.
            BlobContainerPermissions cntPerm;
            cntPerm = new BlobContainerPermissions();
            if (cntPubAccess) {
                cntPerm.setPublicAccess(BlobContainerPublicAccessType.CONTAINER);
            } else {
                cntPerm.setPublicAccess(BlobContainerPublicAccessType.OFF);
            }
            container.uploadPermissions(cntPerm);
        }

        return container;
    }

    /**
     * Returns custom URL for queue and table.
     *
     * @param storageAccountName
     * @param type
     * @param blobURL
     * @return
     */
    private static String getCustomURI(String storageAccountName, String type,
                                       String blobURL) {
        if (QUEUE.equalsIgnoreCase(type)) {
            return blobURL.replace(storageAccountName + "." + BLOB,
                    storageAccountName + "." + type);
        } else if (TABLE.equalsIgnoreCase(type)) {
            return blobURL.replace(storageAccountName + "." + BLOB,
                    storageAccountName + "." + type);
        } else {
            return null;
        }
    }

    /**
     * Returns suffix for blob endpoint.
     *
     * @param blobURL endpoint
     * @return the endpoint suffix
     */
    private static String getEndpointSuffix(String blobURL) {
        int endSuffixStartIndex = blobURL.toLowerCase().indexOf(Utils.BLOB_ENDPOINT_ENDSUFFIX_KEYWORD);
        if (endSuffixStartIndex < 0) {
            return null;
        } else {
            return blobURL.substring(endSuffixStartIndex);
        }
    }

    /**
     * @param listener
     * @param blob
     * @param src
     * @throws StorageException
     * @throws IOException
     * @throws InterruptedException
     * @returns Md5 hash of the uploaded file in hexadecimal encoding
     */
    protected static String upload(TaskListener listener, CloudBlockBlob blob, FilePath src)
            throws StorageException, IOException, InterruptedException {
        MessageDigest md = DigestUtils.getMd5Digest();
        long startTime = System.currentTimeMillis();
        try (InputStream inputStream = src.read(); DigestInputStream digestInputStream = new DigestInputStream(inputStream, md)) {
            blob.upload(digestInputStream, src.length(), null, getBlobRequestOptions(), Utils.updateUserAgent());
        }
        long endTime = System.currentTimeMillis();
        listener.getLogger().println("Uploaded blob with uri " + blob.getUri() + " in " + getTime(endTime - startTime));
        return DatatypeConverter.printHexBinary(md.digest());
    }

    /**
     * Uploads files to Windows Azure Storage.
     *
     * @param run              environment of build
     * @param listener         logging
     * @param launcher         env vars for remote builds
     * @param strAcc           storage account information.
     * @param expContainerName container name.
     * @param cntPubAccess     denotes if container is publicly accessible.
     * @param expFP            File Path in ant glob syntax relative to CI tool workspace.
     * @param expVP            Virtual Path of blob container.
     * @param excludeFP        File Path in ant glob syntax to exclude from upload
     * @param uploadType       upload file type
     * @param individualBlobs  blobs from build
     * @param archiveBlobs     blobs from build in archive files
     * @param cleanUpContainer if container is cleaned
     * @return filesUploaded number of files that are uploaded.
     * @throws WAStorageException throws exception
     */
    public static int upload(Run<?, ?> run, Launcher launcher, TaskListener listener,
                             StorageAccountInfo strAcc, String expContainerName,
                             AzureBlobProperties blobProperties, List<AzureBlobMetadataPair> metadata,
                             boolean cntPubAccess, boolean cleanUpContainer, String expFP,
                             String expVP, String excludeFP, UploadType uploadType,
                             List<AzureBlob> individualBlobs, List<AzureBlob> archiveBlobs, FilePath workspace) throws WAStorageException {

        int filesUploaded = 0; // Counter to track no. of files that are uploaded

        try {
            FilePath workspacePath = new FilePath(launcher.getChannel(), workspace.getRemote());
            final EnvVars env = run.getEnvironment(listener);

            listener.getLogger().println(
                    Messages.WAStoragePublisher_uploading());

            CloudBlobContainer container = WAStorageClient
                    .getBlobContainerReference(strAcc, expContainerName,
                            true, true, cntPubAccess);

            // Delete previous contents if cleanup is needed
            if (cleanUpContainer) {
                deleteContents(container);
            }

            final String zipFolderName = "artifactsArchive";
            final String zipName = "archive.zip";
            // Make sure we exclude the tempPath from archiving.
            String excludesWithoutZip = "**/" + zipFolderName + "*/" + zipName;
            if (excludeFP != null) {
                excludesWithoutZip = excludeFP + "," + excludesWithoutZip;
            }
            String archiveIncludes = "";

            StringTokenizer strTokens = new StringTokenizer(expFP, fpSeparator);
            while (strTokens.hasMoreElements()) {
                String fileName = strTokens.nextToken();

                String embeddedVP = null;

                if (fileName != null && fileName.contains("::")) {
                    int embVPSepIndex = fileName.indexOf("::");

                    // Separate fileName and Virtual directory name
                    if (fileName.length() > embVPSepIndex + 1) {
                        embeddedVP = fileName.substring(embVPSepIndex + 2,
                                fileName.length());

                        if (Utils.isNullOrEmpty(embeddedVP)) {
                            embeddedVP = null;
                        } else if (!embeddedVP.endsWith(Constants.FWD_SLASH)) {
                            embeddedVP = embeddedVP + Constants.FWD_SLASH;
                        }
                    }
                    fileName = fileName.substring(0, embVPSepIndex);
                }

                archiveIncludes += "," + fileName;

                // List all the paths without the zip archives.
                FilePath[] paths = workspacePath.list(fileName, excludesWithoutZip);
                filesUploaded += paths.length;

                URI workspaceURI = workspacePath.toURI();

                if (uploadType == UploadType.INVALID) {
                    // no files are uploaded
                    return 0;
                }

                if (paths.length != 0 && uploadType != UploadType.ZIP) {
                    for (FilePath src : paths) {
                        // Remove the workspace bit of this path
                        URI srcURI = workspaceURI.relativize(src.toURI());

                        CloudBlockBlob blob;
                        String srcPrefix = srcURI.getPath();
                        if (Utils.isNullOrEmpty(expVP)
                                && Utils.isNullOrEmpty(embeddedVP)) {
                            blob = container.getBlockBlobReference(srcPrefix);
                        } else {
                            String prefix = expVP;

                            if (!Utils.isNullOrEmpty(embeddedVP)) {
                                if (Utils.isNullOrEmpty(expVP)) {
                                    prefix = embeddedVP;
                                } else {
                                    prefix = expVP + embeddedVP;
                                }
                            }
                            blob = container.getBlockBlobReference(prefix + srcPrefix);
                        }

                        // Set blob properties
                        if (blobProperties != null) {
                            blobProperties.configure(blob);
                        }

                        // Set blob metadata
                        if (metadata != null) {
                            HashMap<String, String> metadataMap = blob.getMetadata();
                            for (AzureBlobMetadataPair pair : metadata) {
                                metadataMap.put(
                                    Util.replaceMacro(pair.getKey(), env),
                                    Util.replaceMacro(pair.getValue(), env)
                                );
                            }
                            blob.setMetadata(metadataMap);
                        }

                        String uploadedFileHash = upload(listener, blob, src);
                        individualBlobs.add(new AzureBlob(blob.getName(), blob.getUri().toString().replace("http://", "https://"), uploadedFileHash, src.length()));
                    }
                }
            }

            if (filesUploaded != 0 && (uploadType != UploadType.INDIVIDUAL)) {
                // Create a temp dir for the upload
                FilePath tempPath = workspacePath.createTempDir(zipFolderName, null);

                Glob globScanner = new Glob(archiveIncludes, excludesWithoutZip);

                FilePath zipPath = tempPath.child(zipName);
                workspacePath.zip(zipPath.write(), globScanner);

                // When uploading the zip, do not add in the tempDir to the block
                // blob reference.
                String blobURI = zipPath.getName();

                if (!Utils.isNullOrEmpty(expVP)) {
                    blobURI = expVP + blobURI;
                }

                CloudBlockBlob blob = container.getBlockBlobReference(blobURI);

                String uploadedFileHash = upload(listener, blob, zipPath);
                // Make sure to note the new blob as an archive blob,
                // so that it can be specially marked on the azure storage page.
                archiveBlobs.add(new AzureBlob(blob.getName(), blob.getUri().toString().replace("http://", "https://"), uploadedFileHash, zipPath.length()));

                tempPath.deleteRecursive();
            }

        } catch (StorageException | IOException | InterruptedException | URISyntaxException e) {
            throw new WAStorageException(e.getMessage(), e.getCause());
        }
        return filesUploaded;
    }

    /**
     * Deletes contents of container
     *
     * @param container
     * @throws StorageException
     * @throws URISyntaxException
     */
    private static void deleteContents(CloudBlobContainer container)
            throws StorageException, URISyntaxException, MalformedURLException, IOException {

        for (ListBlobItem blobItem : container.listBlobs()) {
            if (blobItem instanceof CloudBlob) {
                ((CloudBlob) blobItem).uploadProperties(null, null, Utils.updateUserAgent());
                ((CloudBlob) blobItem).delete();
            } else if (blobItem instanceof CloudBlobDirectory) {
                deleteContents((CloudBlobDirectory) blobItem);
            }
        }
    }

    /**
     * Deletes contents of virtual directory
     *
     * @param cloudBlobDirectory
     * @throws StorageException
     * @throws URISyntaxException
     */
    private static void deleteContents(CloudBlobDirectory cloudBlobDirectory)
            throws StorageException, URISyntaxException, IOException {

        for (ListBlobItem blobItem : cloudBlobDirectory.listBlobs()) {
            if (blobItem instanceof CloudBlob) {
                ((CloudBlob) blobItem).uploadProperties(null, null, Utils.updateUserAgent());
                ((CloudBlob) blobItem).delete();
            } else if (blobItem instanceof CloudBlobDirectory) {
                deleteContents((CloudBlobDirectory) blobItem);
            }
        }
    }

    public static int downloadFromContainer(AzureStorageBuilderContext context)
            throws WAStorageException {
        try {
            context.getListener().getLogger().println(
                    Messages.AzureStorageBuilder_downloading());
            final CloudBlobContainer container = getBlobContainerReference(context.getStorageAccountInfo(), context.getContainerName(),
                    false, true, null);
            return downloadBlobs(container.listBlobs(), context);
        } catch (WAStorageException e) {
            throw e;
        } catch (Exception e) {
            throw new WAStorageException(e.getMessage(), e.getCause());
        }
    }

    /**
     * Downloads from Azure blob
     *
     * @param azureBlobs blobs from build
     * @param context    the download context
     * @return filesDownloaded number of files that are downloaded
     * @throws WAStorageException throws exception
     */
    public static int downloadArtifacts(final List<AzureBlob> azureBlobs, final AzureStorageBuilderContext context)
            throws WAStorageException {

        int filesDownloaded = 0;

        for (final AzureBlob blob : azureBlobs) {
            try {
                context.getListener().getLogger().println(Messages.AzureStorageBuilder_downloading());

                final URL blobURL = new URL(blob.getBlobURL());
                final String filePath = blobURL.getFile();

                final CloudBlobContainer container = getBlobContainerReference(context.getStorageAccountInfo(),
                        filePath.split("/")[1], false, true, null);

                if (shouldDownload(context.getIncludeFilesPattern(), context.getExcludeFilesPattern(), blob.getBlobName(), true)) {
                    final CloudBlockBlob cbb = container.getBlockBlobReference(blob.getBlobName());
                    downloadBlob(cbb, context);
                    filesDownloaded++;
                }
            } catch (StorageException | URISyntaxException | IOException e) {
                throw new WAStorageException(e.getMessage(), e.getCause());
            }
        }
        return filesDownloaded;
    }

    private static int downloadBlobs(final Iterable<ListBlobItem> blobItems, final AzureStorageBuilderContext context)
            throws URISyntaxException, StorageException, WAStorageException {
        int filesDownloaded = 0;
        for (final ListBlobItem blobItem : blobItems) {
            // If the item is a blob, not a virtual directory
            if (blobItem instanceof CloudBlob) {
                // Download the item and save it to a file with the same
                final CloudBlob blob = (CloudBlob) blobItem;

                // Check whether we should download it.
                if (shouldDownload(context.getIncludeFilesPattern(), context.getExcludeFilesPattern(), blob.getName(), true)) {
                    downloadBlob(blob, context);
                    filesDownloaded++;
                }

            } else if (blobItem instanceof CloudBlobDirectory) {
                final CloudBlobDirectory blobDirectory = (CloudBlobDirectory) blobItem;
                if (shouldDownload(context.getIncludeFilesPattern(), context.getExcludeFilesPattern(), blobDirectory.getPrefix(), false)) {
                    filesDownloaded += downloadBlobs(blobDirectory.listBlobs(), context);
                }
            }
        }
        return filesDownloaded;
    }

    private static void downloadBlob(final CloudBlob blob, final AzureStorageBuilderContext context)
            throws WAStorageException {
        try {
            final FilePath downloadDir = context.getDownloadDir();
            FilePath downloadFile = new FilePath(downloadDir, blob.getName());

            // That filepath will contain all the directories and explicit virtual
            // paths, so if the user wanted it flattened, grab just the file name and
            // recreate the file path
            if (context.isFlattenDirectories()) {
                downloadFile = new FilePath(downloadDir, downloadFile.getName());
            }

            final long startTime = System.currentTimeMillis();
            try (OutputStream fos = downloadFile.write()) {
                blob.download(fos, null, getBlobRequestOptions(), Utils.updateUserAgent());
            }
            final long endTime = System.currentTimeMillis();

            context.getListener().getLogger().println(
                    "blob " + blob.getName() + " is downloaded to "
                            + downloadDir + " in "
                            + getTime(endTime - startTime));

            if (context.isDeleteFromAzureAfterDownload()) {
                blob.deleteIfExists();
                context.getListener().getLogger().println(
                        "blob " + blob.getName() + " is deleted from Azure.");
            }
        } catch (Exception e) {
            throw new WAStorageException(e.getMessage(), e.getCause());
        }
    }

    private static boolean shouldDownload(String includePattern, String excludePattern, String blobName, boolean isFullPath) {
        String[] includePatterns = includePattern.split(fpSeparator);
        String[] excludePatterns = null;

        if (excludePattern != null) {
            excludePatterns = excludePattern.split(fpSeparator);
        }

        return blobPathMatches(blobName, includePatterns, excludePatterns, isFullPath);
    }

    private static boolean blobPathMatches(String path, String[] includePatterns, String[] excludePatterns, boolean isFullPath) {
        if (!isFullPath) {
            // If we don't have a full path, we can't check for exclusions
            // yet.  Consider include: **/*, exclude **/foo.txt.  Both would match
            // any dir.
            return isPotentialMatch(path, includePatterns);
        } else {
            return isExactMatch(path, includePatterns) && (excludePatterns == null || !isExactMatch(path, excludePatterns));
        }
    }

    /**
     * Determines whether the path is a potential match to any of the provided
     * patterns
     *
     * @param path
     * @param patterns
     * @return
     */
    private static boolean isPotentialMatch(String path, String[] patterns) {
        AntPathMatcher matcher = new AntPathMatcher();
        for (String pattern : patterns) {
            if (matcher.matchStart(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines whether the path is an exact match to any of the provided
     * patterns
     *
     * @param path
     * @param patterns
     * @return
     */
    private static boolean isExactMatch(String path, String[] patterns) {
        AntPathMatcher matcher = new AntPathMatcher();
        for (String pattern : patterns) {
            if (matcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generates SAS URL for blob in Azure storage account
     *
     * @param storageAccount
     * @param blobName
     * @param containerName  container name
     * @return SAS URL
     * @throws Exception
     */
    public static String generateSASURL(StorageAccountInfo storageAccount, String containerName, String blobName) throws Exception {
        String storageAccountName = storageAccount.getStorageAccName();
        StorageCredentialsAccountAndKey credentials = new StorageCredentialsAccountAndKey(storageAccountName, storageAccount.getStorageAccountKey());
        URL blobURL = new URL(storageAccount.getBlobEndPointURL());
        String saBlobURI = new StringBuilder().append(blobURL.getProtocol()).append("://").append(storageAccountName).append(".")
                .append(blobURL.getHost()).append("/").toString();
        CloudStorageAccount cloudStorageAccount = new CloudStorageAccount(credentials, new URI(saBlobURI),
                new URI(getCustomURI(storageAccountName, QUEUE, saBlobURI)),
                new URI(getCustomURI(storageAccountName, TABLE, saBlobURI)));
        // Create the blob client.
        CloudBlobClient blobClient = cloudStorageAccount.createCloudBlobClient();
        CloudBlobContainer container = blobClient.getContainerReference(containerName);

        // At this point need to throw an error back since container itself did not exist.
        if (!container.exists()) {
            throw new Exception("WAStorageClient: generateSASURL: Container " + containerName
                    + " does not exist in storage account " + storageAccountName);
        }

        CloudBlob blob = container.getBlockBlobReference(blobName);
        String sas = blob.generateSharedAccessSignature(generatePolicy(), null);

        return sas;
    }

    public static SharedAccessBlobPolicy generatePolicy() {
        SharedAccessBlobPolicy policy = new SharedAccessBlobPolicy();
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.setTime(new Date());

        calendar.add(Calendar.HOUR, 1);
        policy.setSharedAccessExpiryTime(calendar.getTime());
        policy.setPermissions(EnumSet.of(SharedAccessBlobPermissions.READ));

        return policy;
    }

    /**
     * Returns Blob requests options - primarily sets concurrentRequestCount to
     * number of available cores
     *
     * @return
     */
    private static BlobRequestOptions getBlobRequestOptions() {
        BlobRequestOptions options = new BlobRequestOptions();
        options.setConcurrentRequestCount(Runtime.getRuntime().availableProcessors());

        return options;
    }

    public static String getTime(long timeInMills) {
        return DurationFormatUtils.formatDuration(timeInMills, "HH:mm:ss.S")
                + " (HH:mm:ss.S)";
    }
}
