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
import com.microsoft.azure.storage.file.ListFileItem;
import com.microsoftopentechnologies.windowsazurestorage.AzureBlobMetadataPair;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.service.UploadService;
import com.microsoftopentechnologies.windowsazurestorage.service.UploadToFileService;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadServiceData;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadType;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class FileStorageUploadIT extends IntegrationTest {
    private static final Logger LOGGER = Logger.getLogger(FileStorageUploadIT.class.getName());
    private String fileShareName;

    @Before
    public void setUp() throws IOException {
        try {
            disableAI();

            fileShareName = "testshare-" + TestEnvironment.GenerateRandomString(10);
            testEnv = new TestEnvironment(fileShareName);
            File directory = new File(fileShareName);
            if (!directory.exists())
                directory.mkdir();

            testEnv.account = AzureUtils.getCloudStorageAccount(testEnv.sampleStorageAccount);
            testEnv.fileShare = testEnv.account.createCloudFileClient().getShareReference(fileShareName);
            testEnv.fileShare.createIfNotExists();

            for (int i = 0; i < testEnv.TOTAL_FILES; i++) {
                String tempContent = UUID.randomUUID().toString();
                File temp = new File(directory.getAbsolutePath(), "upload" + tempContent + ".txt");
                FileUtils.writeStringToFile(temp, tempContent);
                testEnv.uploadFileList.put(tempContent, temp);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, null, e);
            assertTrue(e.getMessage(), false);
        }
    }

    @Test
    public void testUploadFile() {
        try {
            final CloudFileDirectory rootDir = testEnv.fileShare.getRootDirectoryReference();
            Run mockRun = mock(Run.class);
            Launcher mockLauncher = mock(Launcher.class);
            List<AzureBlobMetadataPair> metadata = new ArrayList<>();

            File workspaceDir = new File(fileShareName);
            FilePath workspace = new FilePath(mockLauncher.getChannel(), workspaceDir.getAbsolutePath());

            UploadServiceData serviceData = new UploadServiceData(mockRun, workspace, mockLauncher, TaskListener.NULL, testEnv.sampleStorageAccount);
            serviceData.setFileShareName(fileShareName);
            serviceData.setCleanUpContainerOrShare(false);
            serviceData.setFilePath("*.txt");
            serviceData.setVirtualPath("");
            serviceData.setExcludedFilesPath("");
            serviceData.setUploadType(UploadType.INDIVIDUAL);
            serviceData.setAzureBlobMetadata(metadata);

            UploadService service = new UploadToFileService(serviceData);
            int uploaded = service.execute();

            assertEquals(testEnv.uploadFileList.size(), uploaded);
            for (ListFileItem item : testEnv.fileShare.getRootDirectoryReference().listFilesAndDirectories()) {
                if (item instanceof CloudFile) {
                    CloudFile cloudFile = (CloudFile) item;
                    String downloadedContent = cloudFile.downloadText();
                    File temp = testEnv.uploadFileList.get(downloadedContent);
                    String tempContent = FileUtils.readFileToString(temp, "utf-8");
                    //check for filenames
                    assertEquals(tempContent, downloadedContent);
                    //check for file contents
                    assertEquals("upload" + downloadedContent + ".txt", temp.getName());
                    temp.delete();
                }
            }

        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testUploadFileWithMetaData() {
        try {
            final CloudFileDirectory rootDir = testEnv.fileShare.getRootDirectoryReference();
            Run mockRun = mock(Run.class);
            Launcher mockLauncher = mock(Launcher.class);
            List<AzureBlobMetadataPair> metadata = Arrays.asList(
                    new AzureBlobMetadataPair("k1", "v1"),
                    new AzureBlobMetadataPair("k2", "v2")
            );

            Iterator it = testEnv.uploadFileList.entrySet().iterator();
            Map.Entry firstPair = (Map.Entry) it.next();
            File firstFile = (File) firstPair.getValue();

            File workspaceDir = new File(fileShareName);
            FilePath workspace = new FilePath(mockLauncher.getChannel(), workspaceDir.getAbsolutePath());

            UploadServiceData serviceData = new UploadServiceData(mockRun, workspace, mockLauncher, TaskListener.NULL, testEnv.sampleStorageAccount);
            serviceData.setFileShareName(fileShareName);
            serviceData.setCleanUpContainerOrShare(false);
            serviceData.setFilePath(firstFile.getName());
            serviceData.setVirtualPath("");
            serviceData.setExcludedFilesPath("");
            serviceData.setUploadType(UploadType.INDIVIDUAL);
            serviceData.setAzureBlobMetadata(metadata);

            UploadService service = new UploadToFileService(serviceData);
            int uploaded = service.execute();

            assertEquals(1, uploaded);
            CloudFile cloudFile = testEnv.fileShare.getRootDirectoryReference().getFileReference(firstFile.getName());
            cloudFile.downloadAttributes();

            HashMap<String, String> downloadedMeta = cloudFile.getMetadata();
            for (AzureBlobMetadataPair pair : metadata) {
                assertEquals(pair.getValue(), downloadedMeta.get(pair.getKey()));
            }
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testUploadFileWithEmptyMetaData() {
        try {
            final CloudFileDirectory rootDir = testEnv.fileShare.getRootDirectoryReference();
            Run mockRun = mock(Run.class);
            Launcher mockLauncher = mock(Launcher.class);
            List<AzureBlobMetadataPair> metadata = Arrays.asList(
                    new AzureBlobMetadataPair(null, "v1"),
                    new AzureBlobMetadataPair("", "v1"),
                    new AzureBlobMetadataPair(" ", "v1"),
                    new AzureBlobMetadataPair("k1", null),
                    new AzureBlobMetadataPair("k2", ""),
                    new AzureBlobMetadataPair("k2", " ")
            );

            Iterator it = testEnv.uploadFileList.entrySet().iterator();
            Map.Entry firstPair = (Map.Entry) it.next();
            File firstFile = (File) firstPair.getValue();

            File workspaceDir = new File(fileShareName);
            FilePath workspace = new FilePath(mockLauncher.getChannel(), workspaceDir.getAbsolutePath());

            UploadServiceData serviceData = new UploadServiceData(mockRun, workspace, mockLauncher, TaskListener.NULL, testEnv.sampleStorageAccount);
            serviceData.setFileShareName(fileShareName);
            serviceData.setCleanUpContainerOrShare(false);
            serviceData.setFilePath(firstFile.getName());
            serviceData.setVirtualPath("");
            serviceData.setExcludedFilesPath("");
            serviceData.setUploadType(UploadType.INDIVIDUAL);
            serviceData.setAzureBlobMetadata(metadata);

            UploadService service = new UploadToFileService(serviceData);
            int uploaded = service.execute();

            assertEquals(1, uploaded);
            CloudFile cloudFile = testEnv.fileShare.getRootDirectoryReference().getFileReference(firstFile.getName());
            cloudFile.downloadAttributes();

            HashMap<String, String> downloadedMeta = cloudFile.getMetadata();
            assertTrue(downloadedMeta.isEmpty());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @After
    public void tearDown() throws StorageException {
        System.gc();
        Iterator it = testEnv.uploadFileList.entrySet().iterator();
        if (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            File x = (File) pair.getValue();
            LOGGER.log(Level.INFO, x.getParent() + " will now be deleted");
            try {
                FileUtils.deleteDirectory(x.getParentFile());
            } catch (IOException ex) {
                Logger.getLogger(WAStorageClientUploadIT.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        testEnv.fileShare.deleteIfExists();
        testEnv.uploadFileList.clear();
    }
}
