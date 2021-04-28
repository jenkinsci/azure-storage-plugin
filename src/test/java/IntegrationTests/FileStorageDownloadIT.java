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

import com.azure.storage.file.share.ShareDirectoryClient;
import com.azure.storage.file.share.ShareFileClient;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.service.DownloadFromFileService;
import com.microsoftopentechnologies.windowsazurestorage.service.model.DownloadServiceData;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class FileStorageDownloadIT extends IntegrationTest {
    @Before
    public void setUp() throws IOException, URISyntaxException, InterruptedException {
        final String fileShareName = "testshare" + TestEnvironment.GenerateRandomString(15);
        testEnv = new TestEnvironment(fileShareName);

        File directory = new File(fileShareName);
        if (!directory.exists()) {
            boolean mkdir = directory.mkdir();
            if (!mkdir) {
                throw new IllegalStateException("directory " + fileShareName + " failed to create");
            }
        }

        testEnv.fileServiceClient = AzureUtils.getShareClient(testEnv.sampleStorageAccount);
        testEnv.fileShare = testEnv.fileServiceClient.getShareClient(fileShareName);
        if (!testEnv.fileShare.exists()) {
            testEnv.fileShare.create();
        }

        ShareDirectoryClient directoryClient = testEnv.fileShare.getRootDirectoryClient();
        if (!directoryClient.exists()) {
            directoryClient.create();
        }
        for (int i = 0; i < TestEnvironment.TOTAL_FILES; i++) {
            String tempContent = UUID.randomUUID().toString();
            File temp = new File(directory.getAbsolutePath(), "download" + tempContent + ".txt");
            FileUtils.writeStringToFile(temp, tempContent, StandardCharsets.UTF_8);

            FilePath localPath = new FilePath(temp);
            File file = new File(localPath.getRemote());
            ShareFileClient cloudFile = directoryClient.getFileClient(temp.getName());
            cloudFile.create(localPath.length());
            try (FileInputStream fis = new FileInputStream(file); BufferedInputStream bis = new BufferedInputStream(fis)) {
                cloudFile.upload(
                        bis,
                        localPath.length());
            }
            testEnv.downloadFileList.put(tempContent, temp);
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
        serviceData.setFileShare(testEnv.fileShare.getShareName());
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
    public void tearDown() {
        for (File file : testEnv.downloadFileList.values()) {
            if (file.getParentFile().exists()) {
                try {
                    FileUtils.deleteDirectory(file.getParentFile());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (testEnv.fileShare != null) {
            testEnv.fileShare.delete();
        }
        testEnv.downloadFileList.clear();
    }
}
