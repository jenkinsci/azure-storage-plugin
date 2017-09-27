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

package IntegrationTests;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.file.CloudFile;
import com.microsoft.azure.storage.file.CloudFileDirectory;
import com.microsoft.azure.storage.file.FileRequestOptions;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;
import com.microsoftopentechnologies.windowsazurestorage.service.DownloadFromFileService;
import com.microsoftopentechnologies.windowsazurestorage.service.model.DownloadServiceData;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class FileStorageDownloadIT extends IntegrationTest {
    private static final Logger LOGGER = Logger.getLogger(WAStorageClientDownloadIT.class.getName());

    @Before
    public void setUp() throws IOException {
        try {
            disableAI();

            final String fileShareName = "testshare" + TestEnvironment.GenerateRandomString(15);
            testEnv = new TestEnvironment(fileShareName);

            File directory = new File(fileShareName);
            if (!directory.exists())
                directory.mkdir();

            testEnv.account = AzureUtils.getCloudStorageAccount(testEnv.sampleStorageAccount);
            testEnv.fileShare = testEnv.account.createCloudFileClient().getShareReference(fileShareName);
            testEnv.fileShare.createIfNotExists();

            CloudFileDirectory cloudFileDirectory = testEnv.fileShare.getRootDirectoryReference();
            final MessageDigest md = DigestUtils.getMd5Digest();
            for (int i = 0; i < testEnv.TOTAL_FILES; i++) {
                String tempContent = UUID.randomUUID().toString();
                File temp = new File(directory.getAbsolutePath(), "download" + tempContent + ".txt");
                FileUtils.writeStringToFile(temp, tempContent);

                FilePath localPath = new FilePath(temp);
                CloudFile cloudFile = cloudFileDirectory.getFileReference(temp.getName());
                try (InputStream inputStream = localPath.read();
                     DigestInputStream digestInputStream = new DigestInputStream(inputStream, md)) {
                    cloudFile.upload(
                            digestInputStream,
                            localPath.length(),
                            null,
                            new FileRequestOptions(),
                            Utils.updateUserAgent());
                }
                testEnv.downloadFileList.put(tempContent, temp);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, null, e);
            Assert.assertTrue(e.getMessage(), false);
        }
    }

    @Test
    public void testDownloadFromFile() throws IOException {
        Run mockRun = mock(Run.class);
        Launcher mockLauncher = mock(Launcher.class);

        File downloaded = new File(new File(".").getAbsolutePath(), TestEnvironment.GenerateRandomString(5));
        downloaded.mkdir();
        FilePath workspace = new FilePath(downloaded.getAbsoluteFile());

        DownloadServiceData serviceData = new DownloadServiceData(mockRun, workspace, mockLauncher, TaskListener.NULL, testEnv.sampleStorageAccount);
        serviceData.setIncludeFilesPattern("*.txt");
        serviceData.setExcludeFilesPattern("archive.zip");
        serviceData.setFileShare(testEnv.fileShare.getName());
        DownloadFromFileService service = new DownloadFromFileService(serviceData);

        int totalFiles = service.execute();

        assertEquals(testEnv.downloadFileList.size(), totalFiles);
        File[] listofFiles = downloaded.listFiles();
        assertEquals(testEnv.downloadFileList.size(), listofFiles.length);

        for (File each : listofFiles) {
            assertEquals(true, each.isFile());
            String tempContent = FileUtils.readFileToString(each, "utf-8");
            File tempFile = testEnv.downloadFileList.get(tempContent);
            assertEquals(FileUtils.readFileToString(tempFile, "utf-8"), tempContent);
            assertEquals(each.getName(), tempFile.getName());
            each.delete();
        }
        FileUtils.deleteDirectory(downloaded);
    }

    @After
    public void tearDown() throws StorageException {
        if (testEnv.fileShare != null) {
            testEnv.fileShare.deleteIfExists();
        }

        for (File file : testEnv.downloadFileList.values()) {
            if (file.getParentFile().exists()) {
                try {
                    FileUtils.deleteDirectory(file.getParentFile());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        testEnv.downloadFileList.clear();
    }
}
