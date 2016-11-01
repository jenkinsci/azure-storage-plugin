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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.logging.Logger;

import org.apache.commons.lang.time.DurationFormatUtils;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.DirectoryScanner;

import org.springframework.util.AntPathMatcher;

import hudson.FilePath;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.util.DirScanner.*;

import com.microsoft.windowsazure.storage.CloudStorageAccount;
import com.microsoft.windowsazure.storage.RetryNoRetry;
import com.microsoft.windowsazure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.windowsazure.storage.StorageException;
import com.microsoft.windowsazure.storage.blob.BlobContainerPermissions;
import com.microsoft.windowsazure.storage.blob.BlobContainerPublicAccessType;
import com.microsoft.windowsazure.storage.blob.BlobRequestOptions;
import com.microsoft.windowsazure.storage.blob.CloudBlob;
import com.microsoft.windowsazure.storage.blob.CloudBlobClient;
import com.microsoft.windowsazure.storage.blob.CloudBlobContainer;
import com.microsoft.windowsazure.storage.blob.CloudBlobDirectory;
import com.microsoft.windowsazure.storage.blob.CloudBlockBlob;
import com.microsoft.windowsazure.storage.blob.ListBlobItem;
import com.microsoft.windowsazure.storage.blob.SharedAccessBlobPermissions;
import com.microsoft.windowsazure.storage.blob.SharedAccessBlobPolicy;
import com.microsoftopentechnologies.windowsazurestorage.WAStoragePublisher.UploadType;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureTelemetryHelper;
import java.util.Arrays;

public class WAStorageClient {

    private static final Logger LOGGER = Logger.getLogger(WAStorageClient.class.getName());

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
     * conatiner existence.
     *
     * @param storageAccountName
     * @param storageAccountKey
     * @param blobEndPointURL
     * @return true if valid
     * @throws WAStorageException
     */
    public static boolean validateStorageAccount(
            final String storageAccountName, final String storageAccountKey,
            final String blobEndPointURL) throws WAStorageException {
        AzureTelemetryHelper telemetry = new AzureTelemetryHelper();

        try {
            // Get container reference
            CloudBlobContainer container = getBlobContainerReference(
                    storageAccountName, storageAccountKey, blobEndPointURL,
                    TEST_CNT_NAME, false, false, null);
            container.exists();
            telemetry.createErrorEvent("AzureJenkinsStorageAccountValidationSucessfull", "Validation Sucessfull for StorageAccount " + storageAccountName);

        } catch (Exception e) {
            e.printStackTrace();
            telemetry.createErrorEvent("AzureJenkinsStorageAccountValidationFailed", Messages.Client_SA_val_fail());
            throw new WAStorageException(Messages.Client_SA_val_fail());
        }
        return true;
    }

    /**
     * Returns reference of Windows Azure cloud blob container.
     *
     * @param accName storage account name
     * @param key storage account primary access key
     * @param blobURL blob service endpoint url
     * @param containerName name of the container
     * @param createCnt Indicates if container needs to be created
     * @param allowRetry sets retry policy
     * @param cntPubAccess Permissions for container
     * @return reference of CloudBlobContainer
     * @throws URISyntaxException
     * @throws StorageException
     */
    private static CloudBlobContainer getBlobContainerReference(String accName,
            String key, String blobURL, String containerName,
            boolean createCnt, boolean allowRetry, Boolean cntPubAccess)
            throws URISyntaxException, StorageException {

        CloudStorageAccount cloudStorageAccount;
        CloudBlobClient serviceClient;
        CloudBlobContainer container;
        StorageCredentialsAccountAndKey credentials;

        credentials = new StorageCredentialsAccountAndKey(accName, key);

        if (Utils.isNullOrEmpty(blobURL) || blobURL.equals(Utils.DEF_BLOB_URL)) {
            cloudStorageAccount = new CloudStorageAccount(credentials);
        } else {
            cloudStorageAccount = new CloudStorageAccount(credentials, new URI(
                    blobURL), new URI(getCustomURI(accName, QUEUE, blobURL)),
                    new URI(getCustomURI(accName, TABLE, blobURL)));
        }

        serviceClient = cloudStorageAccount.createCloudBlobClient();
        if (!allowRetry) {
            // Setting no retry policy
            RetryNoRetry rnr = new RetryNoRetry();
            serviceClient.setRetryPolicyFactory(rnr);
        }

        container = serviceClient.getContainerReference(containerName);

        boolean cntExists = container.exists();

        if (createCnt && !cntExists) {
            container.createIfNotExists();
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

    // Returns custom URL for queue and table.
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

    /*
	 * public static List<String> getContainersList( StorageAccountInfo
	 * storageAccount, boolean allowRetry) throws URISyntaxException,
	 * StorageException {
	 * 
	 * if (storageAccount == null) { return null; }
	 * 
	 * List<String> containerList = null;
	 * 
	 * CloudStorageAccount cloudStorageAccount; CloudBlobClient serviceClient;
	 * StorageCredentialsAccountAndKey credentials; String blobURL =
	 * storageAccount.getBlobEndPointURL();
	 * 
	 * credentials = new StorageCredentialsAccountAndKey(
	 * storageAccount.getStorageAccName(),
	 * storageAccount.getStorageAccountKey());
	 * 
	 * if (Utils.isNullOrEmpty(blobURL) || blobURL.equals(Utils.DEF_BLOB_URL)) {
	 * cloudStorageAccount = new CloudStorageAccount(credentials); } else {
	 * cloudStorageAccount = new CloudStorageAccount(credentials, new URI(
	 * blobURL), null, null); }
	 * 
	 * serviceClient = cloudStorageAccount.createCloudBlobClient(); if
	 * (!allowRetry && serviceClient != null) { // Setting no retry policy
	 * RetryNoRetry rnr = new RetryNoRetry();
	 * serviceClient.setRetryPolicyFactory(rnr); }
	 * 
	 * if (serviceClient != null) { for (CloudBlobContainer blobContainer :
	 * serviceClient .listContainers()) { if (containerList == null) {
	 * containerList = new ArrayList<String>(); }
	 * containerList.add(blobContainer.getName()); } } return containerList; }
	 * 
	 * public static List<String> getContainerBlobList( StorageAccountInfo
	 * storageAccountInfo, String containerName) throws URISyntaxException,
	 * StorageException {
	 * 
	 * if (storageAccountInfo == null || (containerName == null ||
	 * containerName.trim().length() == 0)) { return null; }
	 * 
	 * List<String> blobList = new ArrayList<String>();
	 * 
	 * CloudBlobContainer cloudBlobContainer = getBlobContainerReference(
	 * storageAccountInfo.getStorageAccName(),
	 * storageAccountInfo.getStorageAccountKey(),
	 * storageAccountInfo.getBlobEndPointURL(), containerName, false, false,
	 * null);
	 * 
	 * Iterable<ListBlobItem> blobItems = null; if (cloudBlobContainer != null
	 * && cloudBlobContainer.exists()) { blobItems =
	 * cloudBlobContainer.listBlobs(); }
	 * 
	 * if (blobItems != null) { for (ListBlobItem blobItem : blobItems) { // If
	 * the item is a blob, not a virtual directory if (blobItem instanceof
	 * CloudBlob) { // Download the item and save it to a file with the same //
	 * name CloudBlob blob = (CloudBlob) blobItem;
	 * 
	 * // Filter blobs with name "$$$.$$$" if
	 * (blob.getName().endsWith(EMPTY_FILE_NAME)) { continue; }
	 * 
	 * blobList.add(blob.getName()); } else if (blobItem instanceof
	 * CloudBlobDirectory) { CloudBlobDirectory blobDir = (CloudBlobDirectory)
	 * blobItem; blobList.add(blobDir.getPrefix()); // list blobs again
	 * getBlobDirectoryList(blobDir, blobList); } } } return blobList; }
	 * 
	 * public static void getBlobDirectoryList(CloudBlobDirectory blobDirectory,
	 * List<String> blobList) throws URISyntaxException, StorageException {
	 * 
	 * Iterable<ListBlobItem> blobItems = blobDirectory.listBlobs(); if
	 * (blobItems != null) { for (ListBlobItem blobItem : blobItems) { // If the
	 * item is a blob, not a virtual directory if (blobItem instanceof
	 * CloudBlob) { // Download the item and save it to a file with the same //
	 * name CloudBlob blob = (CloudBlob) blobItem;
	 * 
	 * // Filter blobs with name "$$$.$$$" if
	 * (blob.getName().endsWith(EMPTY_FILE_NAME)) { continue; }
	 * blobList.add(blob.getName()); } else if (blobItem instanceof
	 * CloudBlobDirectory) { CloudBlobDirectory blobDir = (CloudBlobDirectory)
	 * blobItem; blobList.add(blobDir.getPrefix()); // list blobs again
	 * getBlobDirectoryList(blobDir, blobList); } } } }
     */
    private static void upload(BuildListener listener, CloudBlockBlob blob, FilePath src)
            throws StorageException, IOException, InterruptedException {
        long startTime = System.currentTimeMillis();
        InputStream inputStream = src.read();
        try {
            blob.upload(inputStream, src.length(), null,
                    getBlobRequestOptions(), null);
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {

            }
        }
        long endTime = System.currentTimeMillis();
        listener.getLogger().println("Uploaded blob with uri " + blob.getUri() + " in " + getTime(endTime - startTime));
    }

    /**
     * Uploads files to Windows Azure Storage.
     *
     * @param listener
     * @param build
     * @param StorageAccountInfo storage account information.
     * @param expContainerName container name.
     * @param cntPubAccess denotes if container is publicly accessible.
     * @param expFP File Path in ant glob syntax relative to CI tool workspace.
     * @param expVP Virtual Path of blob container.
     * @param excludeFP File Path in ant glob syntax to exclude from upload
     * @return filesUploaded number of files that are uploaded.
     * @throws WAStorageException
     * @throws Exception
     */
    public static int upload(AbstractBuild<?, ?> build, BuildListener listener,
            StorageAccountInfo strAcc, String expContainerName,
            boolean cntPubAccess, boolean cleanUpContainer, String expFP,
            String expVP, String excludeFP, UploadType uploadType,
            List<AzureBlob> individualBlobs, List<AzureBlob> archiveBlobs) throws WAStorageException {

        AzureTelemetryHelper telemetry = new AzureTelemetryHelper();

        int filesUploaded = 0; // Counter to track no. of files that are uploaded

        try {
            FilePath workspacePath = build.getWorkspace();
            if (workspacePath == null) {
                listener.getLogger().println(
                        Messages.AzureStorageBuilder_ws_na());
                return filesUploaded;
            }
            StringTokenizer strTokens = new StringTokenizer(expFP, fpSeparator);

            listener.getLogger().println(
                    Messages.WAStoragePublisher_uploading());

            CloudBlobContainer container = WAStorageClient
                    .getBlobContainerReference(strAcc.getStorageAccName(),
                            strAcc.getStorageAccountKey(),
                            strAcc.getBlobEndPointURL(), expContainerName,
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

            while (strTokens.hasMoreElements()) {
                String fileName = strTokens.nextToken();

                String embeddedVP = null;

                if (fileName != null) {
                    int embVPSepIndex = fileName.indexOf("::");

                    // Separate fileName and Virtual directory name
                    if (embVPSepIndex != -1) {
                        if (fileName.length() > embVPSepIndex + 1) {
                            embeddedVP = fileName.substring(embVPSepIndex + 2,
                                    fileName.length());

                            if (Utils.isNullOrEmpty(embeddedVP)) {
                                embeddedVP = null;
                            }

                            if (embeddedVP != null
                                    && !embeddedVP.endsWith(Utils.FWD_SLASH)) {
                                embeddedVP = embeddedVP + Utils.FWD_SLASH;
                            }
                        }
                        fileName = fileName.substring(0, embVPSepIndex);
                    }
                }

                archiveIncludes += "," + fileName;

                // List all the paths without the zip archives.
                FilePath[] paths = workspacePath.list(fileName, excludesWithoutZip);
                filesUploaded += paths.length;

                URI workspaceURI = workspacePath.toURI();

                if (paths.length != 0) {
                    if (uploadType != UploadType.ZIP) {
                        for (FilePath src : paths) {
                            // Remove the workspace bit of this path
                            URI srcURI = workspaceURI.relativize(src.toURI());
                            CloudBlockBlob blob = null;
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

                            upload(listener, blob, src);

                            individualBlobs.add(new AzureBlob(blob.getName(), blob.getUri().toString().replace("http://", "https://")));
                        }
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

                telemetry.createEvent("AzureJenkinsStorageUploadEvent", filesUploaded + "files uploaded, upload type " + uploadType.toString());
                upload(listener, blob, zipPath);
                // Make sure to note the new blob as an archive blob,
                // so that it can be specially marked on the azure storage page.
                archiveBlobs.add(new AzureBlob(blob.getName(), blob.getUri().toString().replace("http://", "https://")));

                tempPath.deleteRecursive();
            }

        } catch (Exception e) {
            e.printStackTrace();
            telemetry.createErrorEvent("AzureJenkinsStorageUploadFailed", e.getMessage());
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
            throws StorageException, URISyntaxException {

        for (ListBlobItem blobItem : container.listBlobs()) {
            if (blobItem instanceof CloudBlob) {
                ((CloudBlob) blobItem).delete();
            }

            if (blobItem instanceof CloudBlobDirectory) {
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
            throws StorageException, URISyntaxException {

        for (ListBlobItem blobItem : cloudBlobDirectory.listBlobs()) {
            if (blobItem instanceof CloudBlob) {
                ((CloudBlob) blobItem).delete();
            }

            if (blobItem instanceof CloudBlobDirectory) {
                deleteContents((CloudBlobDirectory) blobItem);
            }
        }
    }

    /**
     * Downloads from Azure blob
     *
     * @param build
     * @param listener
     * @param strAcc
     * @param expContainerName
     * @param includePattern
     * @param excludePattern
     * @param downloadDirLoc
     * @return
     * @throws WAStorageException
     */
    public static int download(AbstractBuild<?, ?> build,
            BuildListener listener, StorageAccountInfo strAcc,
            String expContainerName, String includePattern, String excludePattern,
            String downloadDirLoc, boolean flattenDirectories)
            throws WAStorageException {

        int filesDownloaded = 0;
        FilePath downloadDir = null;

        try {
            FilePath workspacePath = build.getWorkspace();
            if (workspacePath == null) {
                listener.getLogger().println(
                        Messages.AzureStorageBuilder_ws_na());
                return filesDownloaded;
            }

            if (Utils.isNullOrEmpty(downloadDirLoc)) {
                downloadDir = workspacePath;
            } else {
                downloadDir = new FilePath(workspacePath, downloadDirLoc);
            }

            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }

            listener.getLogger().println(
                    Messages.AzureStorageBuilder_downloading());

            CloudBlobContainer container = WAStorageClient
                    .getBlobContainerReference(strAcc.getStorageAccName(),
                            strAcc.getStorageAccountKey(),
                            strAcc.getBlobEndPointURL(), expContainerName,
                            false, true, null);

            filesDownloaded = downloadBlobs(container, includePattern, excludePattern,
                    downloadDir, flattenDirectories, listener);

        } catch (Exception e) {
            e.printStackTrace();
            throw new WAStorageException(e.getMessage(), e.getCause());
        }
        return filesDownloaded;

    }

    private static boolean blobPathMatches(String path, String[] includePatterns, String[] excludePatterns,
            boolean isFullPath) {
        if (!isFullPath) {
            // If we don't have a full path, we can't check for exclusions
            // yet.  Consider include: **/*, exclude **/foo.txt.  Both would match
            // any dir.
            if (isPotentialMatch(path, includePatterns)) {
                return true;
            }
        } else {
            if (isExactMatch(path, includePatterns)
                    && (excludePatterns == null || !isExactMatch(path, excludePatterns))) {
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
        for (int i = 0; i < patterns.length; i++) {
            if (matcher.match(patterns[i], path)) {
                return true;
            }
        }
        return false;
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
        for (int i = 0; i < patterns.length; i++) {
            if (matcher.matchStart(patterns[i], path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Downloads blobs from container
     *
     * @param container
     * @param includePattern
     * @param excludePattern
     * @param downloadDir
     * @param listener
     * @return
     * @throws URISyntaxException
     * @throws StorageException
     * @throws IOException
     * @throws WAStorageException
     */
    private static int downloadBlobs(CloudBlobContainer container,
            String includePattern, String excludePattern,
            FilePath downloadDir, boolean flattenDirectories, BuildListener listener)
            throws URISyntaxException, StorageException, IOException,
            WAStorageException {

        String[] includePatterns = includePattern.split(fpSeparator);
        String[] excludePatterns = null;

        if (excludePattern != null) {
            excludePatterns = excludePattern.split(fpSeparator);
        }

        int filesDownloaded = 0;

        for (ListBlobItem blobItem : container.listBlobs()) {
            // If the item is a blob, not a virtual directory
            if (blobItem instanceof CloudBlob) {
                // Download the item and save it to a file with the same
                // name
                CloudBlob blob = (CloudBlob) blobItem;

                // Check whether we should download it.
                if (blobPathMatches(blob.getName(), includePatterns, excludePatterns, true)) {
                    downloadBlob(blob, downloadDir, flattenDirectories, listener);
                    filesDownloaded++;
                }

            } else if (blobItem instanceof CloudBlobDirectory) {
                CloudBlobDirectory blobDirectory = (CloudBlobDirectory) blobItem;
                filesDownloaded += downloadBlob(blobDirectory, includePatterns,
                        excludePatterns, downloadDir, flattenDirectories, listener);
            }
        }

        return filesDownloaded;
    }

    /**
     * Downloads blobs from virtual directory
     *
     * @param blobDirectory
     * @param downloadDir
     * @param listener
     * @return
     * @throws StorageException
     * @throws URISyntaxException
     * @throws IOException
     * @throws WAStorageException
     */
    private static int downloadBlob(CloudBlobDirectory blobDirectory,
            String[] includePatterns, String[] excludePatterns,
            FilePath downloadDir, boolean flattenDirectories, BuildListener listener)
            throws StorageException, URISyntaxException, IOException,
            WAStorageException {

        if (!blobPathMatches(blobDirectory.getPrefix(), includePatterns, excludePatterns, false)) {
            return 0;
        }

        int filesDownloaded = 0;

        for (ListBlobItem blobItem : blobDirectory.listBlobs()) {
            // If the item is a blob, not a virtual directory
            if (blobItem instanceof CloudBlob) {
                // Download the item and save it to a file with the same
                // name
                CloudBlob blob = (CloudBlob) blobItem;

                if (blobPathMatches(blob.getName(), includePatterns, excludePatterns, true)) {
                    downloadBlob(blob, downloadDir, flattenDirectories, listener);
                    filesDownloaded++;
                }
            } else if (blobItem instanceof CloudBlobDirectory) {
                CloudBlobDirectory blobDir = (CloudBlobDirectory) blobItem;
                filesDownloaded += downloadBlob(blobDir, includePatterns, excludePatterns,
                        downloadDir, flattenDirectories, listener);
            }
        }

        return filesDownloaded;
    }

    /**
     * Blob download from storage
     *
     * @param blob
     * @param downloadDir
     * @param listener
     * @throws URISyntaxException
     * @throws StorageException
     * @throws IOException
     * @throws InterruptedException
     */
    private static void downloadBlob(CloudBlob blob, FilePath downloadDir, boolean flattenDirectories,
            BuildListener listener) throws WAStorageException {
        OutputStream fos = null;
        try {
            FilePath downloadFile = new FilePath(downloadDir, blob.getName());

            // That filepath will contain all the directories and explicit virtual
            // paths, so if the user wanted it flattened, grab just the file name and
            // recreate the file path
            if (flattenDirectories) {
                downloadFile = new FilePath(downloadDir, downloadFile.getName());
            }

            // fos = new FileOutputStream(downloadDir + File.separator +
            // blob.getName());
            fos = downloadFile.write();

            long startTime = System.currentTimeMillis();

            blob.download(fos, null, getBlobRequestOptions(), null);

            long endTime = System.currentTimeMillis();

            listener.getLogger().println(
                    "blob " + blob.getName() + " is downloaded to "
                    + downloadDir + " in "
                    + getTime(endTime - startTime));
        } catch (Exception e) {
            e.printStackTrace();
            throw new WAStorageException(e.getMessage(), e.getCause());
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {

            }

        }

    }

    /**
     * Generates SAS URL for blob in Azure storage account
     *
     * @param storageAccountName
     * @param storageAccountKey
     * @param containerName
     * @param strBlobURL
     * @return SAS URL
     * @throws Exception
     */
    public static String generateSASURL(String storageAccountName, String storageAccountKey, String containerName, String saBlobEndPoint) throws Exception {
        StorageCredentialsAccountAndKey credentials = new StorageCredentialsAccountAndKey(storageAccountName, storageAccountKey);
        URL blobURL = new URL(saBlobEndPoint);
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

        SharedAccessBlobPolicy policy = new SharedAccessBlobPolicy();
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.setTime(new Date());

        //policy.setSharedAccessStartTime(calendar.getTime());
        calendar.add(Calendar.HOUR, 1);
        policy.setSharedAccessExpiryTime(calendar.getTime());
        policy.setPermissions(EnumSet.of(SharedAccessBlobPermissions.READ));

        BlobContainerPermissions containerPermissions = new BlobContainerPermissions();
        containerPermissions.getSharedAccessPolicies().put("jenkins" + System.currentTimeMillis(), policy);
        container.uploadPermissions(containerPermissions);

        // Create a shared access signature for the container.
        String sas = container.generateSharedAccessSignature(policy, null);

        return sas;
    }

    /**
     * Returns Blob requests options - primarily sets concurrentRequestCount to
     * number of available cores
     *
     * @return
     */
    private static BlobRequestOptions getBlobRequestOptions() {
        BlobRequestOptions options = new BlobRequestOptions();

        int concurrentRequestCount = 1;

        try {
            concurrentRequestCount = Runtime.getRuntime().availableProcessors();
        } catch (Exception e) {
            e.printStackTrace();
        }

        options.setConcurrentRequestCount(concurrentRequestCount);

        return options;
    }

    public static String getTime(long timeInMills) {
        return DurationFormatUtils.formatDuration(timeInMills, "HH:mm:ss.S")
                + " (HH:mm:ss.S)";
    }
}
