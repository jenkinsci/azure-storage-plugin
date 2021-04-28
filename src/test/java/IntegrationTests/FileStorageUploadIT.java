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
import com.azure.storage.file.share.models.ShareFileItem;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
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
    public void setUp() throws IOException, URISyntaxException {
        fileShareName = "testshare-" + TestEnvironment.GenerateRandomString(10);
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

        for (int i = 0; i < testEnv.TOTAL_FILES; i++) {
            String tempContent = UUID.randomUUID().toString();
            File temp = new File(directory.getAbsolutePath(), "upload" + tempContent + ".txt");
            FileUtils.writeStringToFile(temp, tempContent, StandardCharsets.UTF_8);
            testEnv.uploadFileList.put(tempContent, temp);
        }
    }

    @Test
    public void testUploadFile() {
        try {
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
            ShareDirectoryClient rootDirectoryClient = testEnv.fileShare.getRootDirectoryClient();
            for (ShareFileItem item : rootDirectoryClient.listFilesAndDirectories()) {
                if (!item.isDirectory()) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    rootDirectoryClient.getFileClient(item.getName()).download(output);
                    String downloadedContent = output.toString(StandardCharsets.UTF_8.name());
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
    public void testUploadFileWithEmptyMetaData() {
        try {
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
            ShareFileClient cloudFile = testEnv.fileShare.getRootDirectoryClient().getFileClient(firstFile.getName());
            Map<String, String> downloadedMeta = cloudFile.getProperties().getMetadata();
            assertTrue(downloadedMeta.isEmpty());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @After
    public void tearDown() {
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

        if (testEnv.fileShare.exists()) {
            testEnv.fileShare.delete();
        }
        testEnv.uploadFileList.clear();
    }
}
