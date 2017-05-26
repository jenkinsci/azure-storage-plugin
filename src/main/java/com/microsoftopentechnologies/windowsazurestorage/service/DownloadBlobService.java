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
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.CloudBlobDirectory;
import com.microsoft.azure.storage.blob.ListBlobItem;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;
import com.microsoftopentechnologies.windowsazurestorage.service.model.BuilderServiceData;
import hudson.FilePath;
import org.springframework.util.AntPathMatcher;

import java.io.OutputStream;
import java.net.URISyntaxException;

public abstract class DownloadBlobService extends StoragePluginService<BuilderServiceData> {

    public DownloadBlobService(BuilderServiceData data) {
        super(data);
    }

    protected int downloadBlobs(final Iterable<ListBlobItem> blobItems)
            throws URISyntaxException, StorageException, WAStorageException {
        int filesDownloaded = 0;
        for (final ListBlobItem blobItem : blobItems) {
            // If the item is a blob, not a virtual directory
            if (blobItem instanceof CloudBlob) {
                // Download the item and save it to a file with the same
                final CloudBlob blob = (CloudBlob) blobItem;

                // Check whether we should download it.
                if (shouldDownload(serviceData.getIncludeFilesPattern(), serviceData.getExcludeFilesPattern(), blob.getName(), true)) {
                    downloadBlob(blob);
                    filesDownloaded++;
                }

            } else if (blobItem instanceof CloudBlobDirectory) {
                final CloudBlobDirectory blobDirectory = (CloudBlobDirectory) blobItem;
                if (shouldDownload(serviceData.getIncludeFilesPattern(), serviceData.getExcludeFilesPattern(), blobDirectory.getPrefix(), false)) {
                    filesDownloaded += downloadBlobs(blobDirectory.listBlobs());
                }
            }
        }
        return filesDownloaded;
    }

    protected void downloadBlob(final CloudBlob blob)
            throws WAStorageException {
        try {
            final FilePath downloadDir = serviceData.getDownloadDir();
            FilePath downloadFile = new FilePath(downloadDir, blob.getName());

            // That filepath will contain all the directories and explicit virtual
            // paths, so if the user wanted it flattened, grab just the file name and
            // recreate the file path
            if (serviceData.isFlattenDirectories()) {
                downloadFile = new FilePath(downloadDir, downloadFile.getName());
            }

            final long startTime = System.currentTimeMillis();
            try (OutputStream fos = downloadFile.write()) {
                blob.download(fos, null, getBlobRequestOptions(), Utils.updateUserAgent());
            }
            final long endTime = System.currentTimeMillis();
            println(String.format("blob %s is downloaded to %s in %s", blob.getName(), downloadDir, getTime(endTime - startTime)));

            if (serviceData.isDeleteFromAzureAfterDownload()) {
                blob.deleteIfExists();
                println("blob " + blob.getName() + " is deleted from Azure.");
            }
        } catch (Exception e) {
            throw new WAStorageException(e.getMessage(), e.getCause());
        }
    }

    protected boolean shouldDownload(String includePattern, String excludePattern, String blobName, boolean isFullPath) {
        String[] includePatterns = includePattern.split(fpSeparator);
        String[] excludePatterns = null;

        if (excludePattern != null) {
            excludePatterns = excludePattern.split(fpSeparator);
        }

        return blobPathMatches(blobName, includePatterns, excludePatterns, isFullPath);
    }

    private boolean blobPathMatches(String path, String[] includePatterns, String[] excludePatterns, boolean isFullPath) {
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
    private boolean isPotentialMatch(String path, String[] patterns) {
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
    private boolean isExactMatch(String path, String[] patterns) {
        AntPathMatcher matcher = new AntPathMatcher();
        for (String pattern : patterns) {
            if (matcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
