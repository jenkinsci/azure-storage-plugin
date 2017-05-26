/*
 * Copyright (c) Microsoft Corporation
 *   <p/>
 *  All rights reserved.
 *   <p/>
 *  MIT License
 *   <p/>
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 *  documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 *  the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 *  to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *  <p/>
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 *  the Software.
 *   <p/>
 *  THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 *  THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 *  TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package com.microsoftopentechnologies.windowsazurestorage.service;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoftopentechnologies.windowsazurestorage.AzureBlob;
import com.microsoftopentechnologies.windowsazurestorage.AzureBlobMetadataPair;
import com.microsoftopentechnologies.windowsazurestorage.Messages;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.helper.Constants;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;
import com.microsoftopentechnologies.windowsazurestorage.service.model.PublisherServiceData;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadType;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import hudson.util.DirScanner;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.StringTokenizer;

/**
 * Service to upload files to Windows Azure Blob Storage.
 */
public class UploadBlobService extends StoragePluginService<PublisherServiceData> {
    private static final String zipFolderName = "artifactsArchive";
    private static final String zipName = "archive.zip";

    public UploadBlobService(PublisherServiceData serviceData) {
        super(serviceData);
    }

    @Override
    public int execute() throws WAStorageException {
        if (serviceData.getUploadType() == UploadType.INVALID) {
            // no files are uploaded
            println("Upload type is INVALID, nothing to do.");
            return 0;
        }

        int filesUploaded = 0; // Counter to track no. of files that are uploaded
        try {
            final FilePath workspacePath = serviceData.getRemoteWorkspace();
            println(Messages.WAStoragePublisher_uploading());

            final CloudBlobContainer container = getCloudBlobContainer();
            final StringBuilder archiveIncludes = new StringBuilder();

            StringTokenizer strTokens = new StringTokenizer(serviceData.getFilePath(), fpSeparator);
            while (strTokens.hasMoreElements()) {
                String fileName = strTokens.nextToken();
                String embeddedVP = null;

                if (fileName != null && fileName.contains("::")) {
                    int embVPSepIndex = fileName.indexOf("::");

                    // Separate fileName and Virtual directory name
                    if (fileName.length() > embVPSepIndex + 1) {
                        embeddedVP = fileName.substring(embVPSepIndex + 2, fileName.length());

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
                archiveIncludes.append(",").append(fileName);
                filesUploaded += paths.length;

                if (paths.length != 0 && serviceData.getUploadType() != UploadType.ZIP) {
                    // the uploadType is either INDIVIDUAL or BOTH, upload included individual files thus.
                    uploadIndividuals(container, embeddedVP, paths);
                }
            }

            // if uploadType is BOTH or ZIP, create an archive.zip and upload
            if (filesUploaded != 0 && (serviceData.getUploadType() != UploadType.INDIVIDUAL)) {
                uploadArchive(container, archiveIncludes.toString());

            }

        } catch (StorageException | IOException | InterruptedException | URISyntaxException e) {
            throw new WAStorageException(e.getMessage(), e.getCause());
        }
        return filesUploaded;
    }

    private void uploadArchive(final CloudBlobContainer container, final String archiveIncludes)
            throws IOException, InterruptedException, URISyntaxException, StorageException {

        final FilePath workspacePath = serviceData.getRemoteWorkspace();
        // Create a temp dir for the upload
        final FilePath tempDir = workspacePath.createTempDir(zipFolderName, null);
        final FilePath zipPath = tempDir.child(zipName);

        // zip included files into archive.zip file.
        final DirScanner.Glob globScanner = new DirScanner.Glob(archiveIncludes, excludedFilesAndZip());
        workspacePath.zip(zipPath.write(), globScanner);

        // When uploading the zip, do not add in the tempDir to the azure
        String blobURI = zipPath.getName();
        if (!StringUtils.isBlank(serviceData.getVirtualPath())) {
            blobURI = serviceData.getVirtualPath() + blobURI;
        }

        final CloudBlockBlob blob = container.getBlockBlobReference(blobURI);
        String uploadedFileHash = uploadBlob(blob, zipPath);
        // Make sure to note the new blob as an archive blob,
        // so that it can be specially marked on the azure storage page.
        AzureBlob azureBlob = new AzureBlob(blob.getName(), blob.getUri().toString().replace("http://", "https://"), uploadedFileHash, zipPath.length());
        serviceData.getArchiveBlobs().add(azureBlob);

        tempDir.deleteRecursive();
    }

    private void uploadIndividuals(final CloudBlobContainer container, final String embeddedVP, final FilePath[] paths)
            throws IOException, InterruptedException, URISyntaxException, StorageException {
        final URI workspaceURI = serviceData.getRemoteWorkspace().toURI();

        for (FilePath src : paths) {
            // Remove the workspace bit of this path
            final URI srcURI = workspaceURI.relativize(src.toURI());
            final String srcURIPath = srcURI.getPath();
            String prefix = StringUtils.isBlank(serviceData.getVirtualPath()) ? "" : serviceData.getVirtualPath();
            if (!StringUtils.isBlank(embeddedVP)) {
                prefix += embeddedVP;
            }

            final CloudBlockBlob blob = container.getBlockBlobReference(prefix + srcURIPath);
            configureBlobPropertiesAndMetadata(blob, src);
            String uploadedFileHash = uploadBlob(blob, src);
            AzureBlob azureBlob = new AzureBlob(blob.getName(), blob.getUri().toString().replace("http://", "https://"), uploadedFileHash, src.length());
            serviceData.getIndividualBlobs().add(azureBlob);
        }
    }

    private void configureBlobPropertiesAndMetadata(final CloudBlockBlob blob, final FilePath src) throws IOException, InterruptedException {
        final EnvVars env = serviceData.getRun().getEnvironment(serviceData.getTaskListener());

        // Set blob properties
        if (serviceData.getBlobProperties() != null) {
            serviceData.getBlobProperties().configure(blob, src, env);
        }

        // Set blob metadata
        if (serviceData.getAzureBlobMetadata() != null) {
            HashMap<String, String> metadataMap = blob.getMetadata();
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
            }
            blob.setMetadata(metadataMap);
        }
    }

    private CloudBlobContainer getCloudBlobContainer() throws URISyntaxException, StorageException, IOException {
        final CloudBlobContainer container = AzureUtils.getBlobContainerReference(
                serviceData.getStorageAccountInfo(), serviceData.getContainerName(), true, true, serviceData.isPubAccessible());

        // Delete previous contents if cleanup is needed
        if (serviceData.isCleanUpContainer()) {
            AzureUtils.deleteBlobs(container.listBlobs());
        }
        return container;
    }

    private String excludedFilesAndZip() {
        // Make sure we exclude the tempPath from archiving.
        String excludesWithoutZip = "**/" + zipFolderName + "*/" + zipName;
        if (serviceData.getExcludedFilesPath() != null) {
            excludesWithoutZip = serviceData.getExcludedFilesPath() + "," + excludesWithoutZip;
        }
        return excludesWithoutZip;
    }

    /**
     * @param blob
     * @param src
     * @throws StorageException
     * @throws IOException
     * @throws InterruptedException
     * @returns Md5 hash of the uploaded file in hexadecimal encoding
     */
    private String uploadBlob(CloudBlockBlob blob, FilePath src)
            throws StorageException, IOException, InterruptedException {
        final MessageDigest md = DigestUtils.getMd5Digest();
        long startTime = System.currentTimeMillis();
        try (InputStream inputStream = src.read(); DigestInputStream digestInputStream = new DigestInputStream(inputStream, md)) {
            blob.upload(digestInputStream, src.length(), null, getBlobRequestOptions(), Utils.updateUserAgent());
        }
        long endTime = System.currentTimeMillis();

        println("Uploaded blob with uri " + blob.getUri() + " in " + getTime(endTime - startTime));
        return DatatypeConverter.printHexBinary(md.digest());
    }
}
