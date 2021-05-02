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

import com.azure.storage.blob.BlobClient;
import com.azure.storage.file.share.ShareFileClient;
import com.microsoftopentechnologies.windowsazurestorage.Messages;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.service.model.DownloadServiceData;
import hudson.FilePath;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class DownloadService extends StoragePluginService<DownloadServiceData> {
    protected static final String DOWNLOAD = "Download";
    protected static final String DOWNLOAD_FAILED = "DownloadFailed";
    private static final int DOWNLOAD_THREAD_COUNT = 16;
    private static final int KEEP_ALIVE_TIME = 1;
    private static final int TIME_OUT = 1;
    private static final TimeUnit TIME_OUT_UNIT = TimeUnit.DAYS;

    private AtomicInteger filesDownloaded = new AtomicInteger(0);
    private ExecutorService executorService = new ThreadPoolExecutor(DOWNLOAD_THREAD_COUNT, DOWNLOAD_THREAD_COUNT,
            KEEP_ALIVE_TIME, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());

    public DownloadService(DownloadServiceData data) {
        super(data);
    }

    class DownloadThread implements Runnable {
        private Object downloadItem;

        DownloadThread(Object downloadItem) {
            this.downloadItem = downloadItem;
        }

        @Override
        public void run() {
            try {
                if (downloadItem instanceof BlobClient) {
                    downloadBlob((BlobClient) downloadItem);
                } else {
                    downloadSingleFile((ShareFileClient) downloadItem);
                }
                filesDownloaded.addAndGet(1);
            } catch (Exception e) {
                final String message = Messages.AzureStorageBuilder_download_err(
                        getServiceData().getStorageAccountInfo().getStorageAccName()) + ":" + e.getMessage();
                e.printStackTrace(error(message));
                println(message);
                setRunUnstable();
            }
        }
    }

    protected void waitForDownloadEnd() throws WAStorageException {
        executorService.shutdown();
        try {
            boolean executionFinished = executorService.awaitTermination(TIME_OUT, TIME_OUT_UNIT);
            if (!executionFinished) {
                throw new WAStorageException(Messages.AzureStorageBuilder_download_timeout(TIME_OUT, TIME_OUT_UNIT));
            }
        } catch (InterruptedException e) {
            throw new WAStorageException(e.getMessage(), e);
        }
    }

    protected void downloadSingleFile(ShareFileClient cloudFile) throws WAStorageException {
        final DownloadServiceData serviceData = getServiceData();
        try {
            if (serviceData.isVerbose()) {
                println("Downloading file:" + cloudFile.getFileUrl());
            }
            final FilePath destFile = destinationFilePath(cloudFile.getFilePath());

            final long startTime = System.currentTimeMillis();
            try (OutputStream fos = destFile.write()) {
                cloudFile.download(fos);
            }
            final long endTime = System.currentTimeMillis();
            println(String.format(
                    "blob %s is downloaded to %s in %s",
                    cloudFile.getFilePath(), destFile.getParent(), getTime(endTime - startTime)));

            if (serviceData.isDeleteFromAzureAfterDownload()) {
                if (cloudFile.exists()) {
                    cloudFile.delete();
                }
                println("cloud file " + cloudFile.getFilePath() + " is deleted from Azure.");
            }
        } catch (IOException | InterruptedException e) {
            throw new WAStorageException(e.getMessage(), e);
        }
    }

    protected void downloadBlob(BlobClient blob) throws WAStorageException {
        try {
            if (getServiceData().isVerbose()) {
                println("Downloading file:" + blob.getBlobUrl());
            }

            final FilePath destFile = destinationFilePath(blob.getBlobName());
            final long startTime = System.currentTimeMillis();
            try (OutputStream fos = destFile.write()) {
                blob.download(fos);
            }
            final long endTime = System.currentTimeMillis();
            println(String.format("blob %s is downloaded to %s in %s",
                    blob.getBlobName(), destFile.getParent(), getTime(endTime - startTime)));

            if (getServiceData().isDeleteFromAzureAfterDownload()) {
                if (blob.exists()) {
                    blob.delete();
                }
                println("blob " + blob.getBlobName() + " is deleted from Azure.");
            }
        } catch (IOException | InterruptedException e) {
            throw new WAStorageException(e.getMessage(), e);
        }
    }

    protected boolean shouldDownload(
            String includePattern,
            String excludePattern,
            String blobName,
            boolean isFullPath) {
        String[] includePatterns = includePattern.split(FP_SEPARATOR);
        String[] excludePatterns = null;

        if (excludePattern != null) {
            excludePatterns = excludePattern.split(FP_SEPARATOR);
        }

        return blobPathMatches(blobName, includePatterns, excludePatterns, isFullPath);
    }

    private FilePath destinationFilePath(String name) {
        final DownloadServiceData serviceData = getServiceData();
        final FilePath downloadDir = serviceData.getDownloadDir();
        FilePath downloadFile = new FilePath(downloadDir, name);

        // That filepath will contain all the directories and explicit virtual
        // paths, so if the user wanted it flattened, grab just the file name and
        // recreate the file path
        if (serviceData.isFlattenDirectories()) {
            downloadFile = new FilePath(downloadDir, downloadFile.getName());
        }

        return downloadFile;
    }

    private boolean blobPathMatches(
            String path,
            String[] includePatterns,
            String[] excludePatterns,
            boolean isFullPath) {
        if (!isFullPath) {
            // If we don't have a full path, we can't check for exclusions
            // yet.  Consider include: **/*, exclude **/foo.txt.  Both would match
            // any dir.
            return isPotentialMatch(path, includePatterns);
        } else {
            return isExactMatch(path, includePatterns)
                    && (excludePatterns == null || !isExactMatch(path, excludePatterns));
        }
    }

    /**
     * Determines whether the path is a potential match to any of the provided
     * patterns.
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
     * patterns.
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

    public int getFilesDownloaded() {
        return filesDownloaded.get();
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }
}
