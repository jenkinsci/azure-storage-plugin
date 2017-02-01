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
import com.microsoft.azure.storage.blob.BlobContainerPermissions;
import com.microsoft.azure.storage.blob.BlobContainerPublicAccessType;
import com.microsoft.azure.storage.blob.BlobRequestOptions;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlobDirectory;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;
import com.microsoft.azure.storage.blob.SharedAccessBlobPermissions;
import com.microsoft.azure.storage.blob.SharedAccessBlobPolicy;
import com.microsoftopentechnologies.windowsazurestorage.WAStoragePublisher.UploadType;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.helper.Constants;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.DirScanner.Glob;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.springframework.util.AntPathMatcher;

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
     * conatiner existence.
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
    private static CloudBlobContainer getBlobContainerReference(StorageAccountInfo storageAccount, String containerName,
	    boolean createCnt, boolean allowRetry, Boolean cntPubAccess)
	    throws URISyntaxException, StorageException {

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
	    cloudStorageAccount = new CloudStorageAccount(credentials, new URI(
		    blobURL), new URI(getCustomURI(accName, QUEUE, blobURL)),
		    new URI(getCustomURI(accName, TABLE, blobURL)));
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
     *
     * @param listener
     * @param blob
     * @param src
     * @throws StorageException
     * @throws IOException
     * @throws InterruptedException
     */
    private static void upload(TaskListener listener, CloudBlockBlob blob, FilePath src)
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
     * @param run environment of build
     * @param listener logging
     * @param launcher env vars for remote builds
     * @param strAcc storage account information.
     * @param expContainerName container name.
     * @param cntPubAccess denotes if container is publicly accessible.
     * @param expFP File Path in ant glob syntax relative to CI tool workspace.
     * @param expVP Virtual Path of blob container.
     * @param excludeFP File Path in ant glob syntax to exclude from upload
     * @param uploadType upload file type
     * @param individualBlobs blobs from build
     * @param archiveBlobs blobs from build in archive files
     * @param cleanUpContainer if container is cleaned
     * @return filesUploaded number of files that are uploaded.
     * @throws WAStorageException throws exception
     */
    public static int upload(Run<?, ?> run, Launcher launcher, TaskListener listener,
	    StorageAccountInfo strAcc, String expContainerName,
	    boolean cntPubAccess, boolean cleanUpContainer, String expFP,
	    String expVP, String excludeFP, UploadType uploadType,
	    List<AzureBlob> individualBlobs, List<AzureBlob> archiveBlobs, FilePath workspace) throws WAStorageException {

	int filesUploaded = 0; // Counter to track no. of files that are uploaded

	try {
	    FilePath workspacePath = new FilePath(launcher.getChannel(), workspace.getRemote());

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

			InputStream inputStream = src.read();
			String md5hex = DigestUtils.md5Hex(inputStream);
			long sizeInBytes = src.length();

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

			upload(listener, blob, src);
			individualBlobs.add(new AzureBlob(blob.getName(), blob.getUri().toString().replace("http://", "https://"), md5hex, sizeInBytes));
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

		InputStream inputStream = zipPath.read();
		String md5hex = DigestUtils.md5Hex(inputStream);
		long sizeInBytes = zipPath.length();

		if (!Utils.isNullOrEmpty(expVP)) {
		    blobURI = expVP + blobURI;
		}

		CloudBlockBlob blob = container.getBlockBlobReference(blobURI);

		upload(listener, blob, zipPath);
		// Make sure to note the new blob as an archive blob,
		// so that it can be specially marked on the azure storage page.
		archiveBlobs.add(new AzureBlob(blob.getName(), blob.getUri().toString().replace("http://", "https://"), md5hex, sizeInBytes));

		tempPath.deleteRecursive();
	    }

	} catch (Exception e) {
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
	    throws StorageException, URISyntaxException {

	for (ListBlobItem blobItem : cloudBlobDirectory.listBlobs()) {
	    if (blobItem instanceof CloudBlob) {
		((CloudBlob) blobItem).delete();
	    } else if (blobItem instanceof CloudBlobDirectory) {
		deleteContents((CloudBlobDirectory) blobItem);
	    }
	}
    }

    /**
     * Downloads from Azure blob
     *
     * @param run environment of build
     * @param launcher env vars for remote builds
     * @param listener logging
     * @param strAcc storage account
     * @param includePattern pattern to download
     * @param excludePattern pattern to not download
     * @param downloadDirLoc dir to download to
     * @param flattenDirectories if directories are flattened
     * @param workspace workspace of build
     * @param containerName container with blobs
     * @return filesDownloaded number of files that are downloaded
     * @throws WAStorageException throws exception
     */
    public static int download(Run<?, ?> run, Launcher launcher,
            TaskListener listener, StorageAccountInfo strAcc, String includePattern, String excludePattern,
            String downloadDirLoc, boolean flattenDirectories, FilePath workspace, String containerName)
            throws WAStorageException {

        int filesDownloaded = 0;
        try {
            FilePath workspacePath = new FilePath(launcher.getChannel(), workspace.getRemote());
            FilePath downloadDir = getDownloadDir(workspacePath, downloadDirLoc);
            listener.getLogger().println(
                    Messages.AzureStorageBuilder_downloading());
            CloudBlobContainer container = WAStorageClient
                    .getBlobContainerReference(strAcc, containerName,
                            false, true, null);
            filesDownloaded = downloadBlobs(container, includePattern, excludePattern,
                    downloadDir, flattenDirectories, listener);
        } catch (Exception e) {
            throw new WAStorageException(e.getMessage(), e.getCause());
        }
        return filesDownloaded;
    }

    /**
     * Downloads from Azure blob
     *
     * @param run environment of build
     * @param launcher env vars for remote builds
     * @param listener logging
     * @param strAcc storage account
     * @param blobs blobs from build
     * @param includePattern pattern to download
     * @param excludePattern pattern to not download
     * @param downloadDirLoc dir to download to
     * @param flattenDirectories if directories are flattened
     * @param workspace workspace of build
     * @return filesDownloaded number of files that are downloaded
     * @throws WAStorageException throws exception
     */
    public static int download(Run<?, ?> run, Launcher launcher,
	    TaskListener listener, StorageAccountInfo strAcc,
	    List<AzureBlob> blobs, String includePattern, String excludePattern,
	    String downloadDirLoc, boolean flattenDirectories, FilePath workspace)
	    throws WAStorageException {

	int filesDownloaded = 0;

	for (AzureBlob blob : blobs) {
	    try {
		FilePath workspacePath = new FilePath(launcher.getChannel(), workspace.getRemote());

		FilePath downloadDir = getDownloadDir(workspacePath, downloadDirLoc);

		listener.getLogger().println(
			Messages.AzureStorageBuilder_downloading());

		URL blobURL = new URL(blob.getBlobURL());
		String filePath = blobURL.getFile();

		CloudBlobContainer container = WAStorageClient
			.getBlobContainerReference(strAcc, filePath.split("/")[1],
				false, true, null);

		if (shouldDownload(includePattern, excludePattern, blob.getBlobName())) {
		    filesDownloaded += downloadBlobs(container, blob, downloadDir, flattenDirectories, listener);
		}

	    } catch (Exception e) {
		throw new WAStorageException(e.getMessage(), e.getCause());
	    }
	}
	return filesDownloaded;
    }

    private static boolean shouldDownload(String includePattern, String excludePattern, String blobName) {
	String[] includePatterns = includePattern.split(fpSeparator);
	String[] excludePatterns = null;

	if (excludePattern != null) {
	    excludePatterns = excludePattern.split(fpSeparator);
	}

	return blobPathMatches(blobName, includePatterns, excludePatterns);
    }

    private static FilePath getDownloadDir(FilePath workspacePath, String downloadDirLoc) {
	FilePath downloadDir;
	if (Utils.isNullOrEmpty(downloadDirLoc)) {
	    downloadDir = workspacePath;
	} else {
	    downloadDir = new FilePath(workspacePath, downloadDirLoc);
	}
	try {
	    if (!downloadDir.exists()) {
		downloadDir.mkdirs();
	    }
	} catch (Exception e) {
	}

	return downloadDir;
    }

    private static boolean blobPathMatches(String path, String[] includePatterns, String[] excludePatterns) {
	return isExactMatch(path, includePatterns) && (excludePatterns == null || !isExactMatch(path, excludePatterns));
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
     * Downloads blobs
     *
     * @param container
     * @param blob
     * @param downloadDir
     * @param flattenDirectories
     * @param listener
     * @return
     */
    private static int downloadBlobs(CloudBlobContainer container, AzureBlob blob, FilePath downloadDir, boolean flattenDirectories, TaskListener listener) {
	int filesDownloaded = 0;
	try {
	    CloudBlockBlob cbb = container.getBlockBlobReference(blob.getBlobName());
	    downloadBlob(cbb, downloadDir, flattenDirectories, listener);
	    filesDownloaded++;
	} catch (URISyntaxException ex) {
	    Logger.getLogger(WAStorageClient.class.getName()).log(Level.SEVERE, null, ex);
	} catch (StorageException ex) {
	    Logger.getLogger(WAStorageClient.class.getName()).log(Level.SEVERE, null, ex);
	} catch (WAStorageException ex) {
	    listener.getLogger().println("blob " + blob.getBlobName() + " was not found");
	}
	return filesDownloaded;
    }

    /**
     *
     * @param container
     * @param includePattern
     * @param excludePattern
     * @param downloadDir
     * @param flattenDirectories
     * @param listener
     * @return
     * @throws WAStorageException
     * @throws URISyntaxException
     * @throws IOException
     * @throws StorageException
     */
    private static int downloadBlobs(CloudBlobContainer container,
            String includePattern, String excludePattern,
            FilePath downloadDir, boolean flattenDirectories, TaskListener listener) throws WAStorageException, URISyntaxException, IOException, StorageException {
        int filesDownloaded = 0;
        String[] includePatterns = includePattern.split(fpSeparator);
        String[] excludePatterns = null;

        if (excludePattern != null) {
            excludePatterns = excludePattern.split(fpSeparator);
        }

        for (ListBlobItem blobItem : container.listBlobs()) {
            // If the item is a blob, not a virtual directory
            if (blobItem instanceof CloudBlob) {
                // Download the item and save it to a file with the same
                // name
                CloudBlob blob = (CloudBlob) blobItem;

                // Check whether we should download it.
                if (blobPathMatches(blob.getName(), includePatterns, excludePatterns, true)) {
                    downloadBlob((CloudBlockBlob) blob, downloadDir, flattenDirectories, listener);
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
	    TaskListener listener) throws WAStorageException {
	OutputStream fos = null;
	try {
	    FilePath downloadFile = new FilePath(downloadDir, blob.getName());

	    // That filepath will contain all the directories and explicit virtual
	    // paths, so if the user wanted it flattened, grab just the file name and
	    // recreate the file path
	    if (flattenDirectories) {
		downloadFile = new FilePath(downloadDir, downloadFile.getName());
	    }

	    fos = downloadFile.write();

	    long startTime = System.currentTimeMillis();

	    blob.download(fos, null, getBlobRequestOptions(), null);

	    long endTime = System.currentTimeMillis();

	    listener.getLogger().println(
		    "blob " + blob.getName() + " is downloaded to "
		    + downloadDir + " in "
		    + getTime(endTime - startTime));
	} catch (Exception e) {
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
     * Blob download from storage
     *
     * @param blobDirectory
     * @param includePatterns
     * @param excludePaterns
     * @param downloadDir
     * @param flattenDirectories
     * @param listener
     * @throws URISyntaxException
     * @throws StorageException
     * @throws IOException
     * @throws WAStorageException
     */
    private static int downloadBlob(CloudBlobDirectory blobDirectory,
            String[] includePatterns, String[] excludePatterns,
            FilePath downloadDir, boolean flattenDirectories, TaskListener listener)
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
     * Generates SAS URL for blob in Azure storage account
     *
     * @param storageAccount
     * @param blobName
     * @param containerName container name
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
