package IntegrationTests;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.ResultSegment;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;
import com.microsoftopentechnologies.windowsazurestorage.AzureBlob;
import com.microsoftopentechnologies.windowsazurestorage.WAStorageClient;
import com.microsoftopentechnologies.windowsazurestorage.WAStoragePublisher;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Ignore;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 *
 * @author arroyc
 */
public class WAStorageClientUploadIT extends IntegrationTest {

    private static final Logger LOGGER = Logger.getLogger(WAStorageClientUploadIT.class.getName());

    @Before
    public void setUp() throws IOException {
        
        try {
            String containerName = "testupload" + TestEnvironment.GenerateRandomString(15);
            testEnv = new TestEnvironment(containerName);
            File directory = new File(containerName);
            if(!directory.exists())
                directory.mkdir();

            testEnv.account = new CloudStorageAccount(new StorageCredentialsAccountAndKey(testEnv.azureStorageAccountName, testEnv.azureStorageAccountKey1));
            testEnv.blobClient = testEnv.account.createCloudBlobClient();
            testEnv.container = testEnv.blobClient.getContainerReference(containerName);
            testEnv.container.createIfNotExists();
            for (int i = 0; i < testEnv.TOTAL_FILES; i++) {
                String tempContent = UUID.randomUUID().toString();
                File temp = new File(directory.getAbsolutePath(), "upload" + tempContent + ".txt");
                FileUtils.writeStringToFile(temp, tempContent);
                testEnv.uploadFileList.put(tempContent, temp);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, null, e);
            Assert.assertTrue(e.getMessage(), false);
        }
    }

    /**
     * Test of validateStorageAccount method, of class WAStorageClient.
     */
    @Test
    public void testValidateStorageAccount() throws Exception {
        System.out.println("validateStorageAccount");
        StorageAccountInfo storageAccount = testEnv.sampleStorageAccount;
        boolean result = WAStorageClient.validateStorageAccount(storageAccount);
        assertEquals(true, result);
        assertEquals(true,WAStorageClient.validateStorageAccount(new StorageAccountInfo(testEnv.azureStorageAccountName, testEnv.azureStorageAccountKey1, "")));
        testEnv.container.deleteIfExists();
    }

    /**
     * Test of validateStorageAccount method, of class WAStorageClient.
     */
    @Test(expected=WAStorageException.class)
    public void testInvalidateStorageAccount1() throws Exception {
        System.out.println("Testing Invalid StorageAccount");
        WAStorageClient.validateStorageAccount(new StorageAccountInfo(testEnv.azureStorageAccountName, "asdhasdh@asdas!@234=", testEnv.blobURL));
        testEnv.container.deleteIfExists();
    }
    
    /**
     * Test of validateStorageAccount method, of class WAStorageClient.
     */
    @Test(expected=WAStorageException.class)
    public void testInvalidateStorageAccount2() throws Exception {
        System.out.println("Testing Invalid StorageAccount");
        WAStorageClient.validateStorageAccount(new StorageAccountInfo("rfguthio123", testEnv.azureStorageAccountKey2, testEnv.blobURL));
        testEnv.container.deleteIfExists();
    }
    
    /**
     * Test of validateStorageAccount method, of class WAStorageClient.
     */
    @Test(expected=WAStorageException.class)
    public void testInvalidateStorageAccount3() throws Exception {
        System.out.println("Testing Invalid StorageAccount");
        WAStorageClient.validateStorageAccount(new StorageAccountInfo(null, null, null));
        testEnv.container.deleteIfExists();
    }

    /**
     * Test of upload method, of class WAStorageClient.
     */
    @Test
    public void testUpload() {
        System.out.println("upload");
        try {

            WAStorageClient mockStorageClient = spy(WAStorageClient.class);
            Run mockRun = mock(Run.class);
            Launcher mockLauncher = mock(Launcher.class);
            List<AzureBlob> individualBlobs = new ArrayList<>();
            List<AzureBlob> archiveBlobs = new ArrayList<>();

            Iterator it = testEnv.uploadFileList.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry) it.next();
                File temp = (File) pair.getValue();
                FilePath workspace = new FilePath(mockLauncher.getChannel(), FilenameUtils.getFullPathNoEndSeparator(temp.getAbsolutePath()));
                LOGGER.log(Level.INFO, temp.getAbsolutePath());
                mockStorageClient.upload(mockRun, mockLauncher, TaskListener.NULL, testEnv.sampleStorageAccount, testEnv.containerName, false, false, "*.txt", "", "", WAStoragePublisher.UploadType.INDIVIDUAL, individualBlobs, archiveBlobs, workspace);
            }

            for (ListBlobItem blobItem : testEnv.container.listBlobs()) {
                if (blobItem instanceof CloudBlockBlob) {
                    CloudBlockBlob downloadedBlob = (CloudBlockBlob) blobItem;
                    String downloadedContent = downloadedBlob.downloadText("utf-8", null, null, null);
                    File temp = testEnv.uploadFileList.get(downloadedContent);
                    String tempContent = FileUtils.readFileToString(temp, "utf-8");
                    //check for filenames
                    assertEquals(tempContent, downloadedContent);
                    //check for file contents
                    assertEquals("upload" + downloadedContent + ".txt", temp.getName());
                    temp.delete();
                }
            }
            testEnv.container.deleteIfExists();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, null, e);
            Assert.assertTrue(e.getMessage(), false);
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
        }/*
        ResultSegment<CloudBlobContainer> containerList = blobClient.listContainersSegmented("testupload");
        int totalContainers = containerList.getLength();
        while(totalContainers > 0){        
            containerList.getResults().get(--totalContainers).deleteIfExists();
        }*/
        testEnv.container.deleteIfExists();
        testEnv.uploadFileList.clear();
    }

}
