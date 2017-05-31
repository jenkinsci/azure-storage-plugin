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
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertTrue;

public class FileStorageUploadIT extends IntegrationTest {
    private static final Logger LOGGER = Logger.getLogger(FileStorageUploadIT.class.getName());
    private String fileShareName;

    @Before
    public void setUp() throws IOException {
        try {
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
    public void testComplexDirPath() {
        try {
            final CloudFileDirectory rootDir = testEnv.fileShare.getRootDirectoryReference();
            CloudFile cloudFile= rootDir.getFileReference("sub/sub2/test.txt");
            cloudFile.getParent().getParent().createIfNotExists();
            cloudFile.getParent().createIfNotExists();
            cloudFile.setMetadata(null);
            cloudFile.uploadFromFile("D:\\temp\\1111.txt");
            String name = cloudFile.getName();
            String parent = cloudFile.getParent().getName();
            URI uri = cloudFile.getUri();
            String a="1";
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
