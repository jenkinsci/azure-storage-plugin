package MyTest;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;
import com.microsoftopentechnologies.windowsazurestorage.WAStoragePublisher;
import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.BatchFile;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Logger;

import static com.microsoftopentechnologies.windowsazurestorage.helper.Constants.BLOB_STORAGE;
import static org.junit.Assert.assertEquals;

/**
 * Created by t-yuhang on 7/26/2017.
 */
public class WAStorageClientUploadIT extends IntegrationTest {

    private static final Logger LOGGER = Logger.getLogger(WAStorageClientUploadIT.class.getName());

    private TestEnvironment testEnvironment;
    private String storageType;
    private String containername;

    private String filesPath;
    private String fileShareName;

    private CloudStorageAccount account;
    private CloudBlobClient blobClient;
    private CloudBlobContainer container;

    private HashMap<String, String> fileHashMap = new HashMap<>();

    private String command;

    @Before
    public void setUp() throws Exception{
        testEnvironment = new TestEnvironment();
        storageType = BLOB_STORAGE;
        containername = testEnvironment.GenerateRandomString(6);

        account = new CloudStorageAccount(new StorageCredentialsAccountAndKey(
                testEnvironment.azureStorageAccountName, testEnvironment.azureStorageAccountKey1));
        blobClient = account.createCloudBlobClient();
        container = blobClient.getContainerReference(containername);

        command = "";
        for (int i = 0; i < 20; i ++) {
            String content = testEnvironment.GenerateRandomString(32);
            String file = UUID.randomUUID().toString() + ".txt";
            fileHashMap.put(content, file);
            command += "echo " + content + " > " + file + "\n";
        }
        for (int i = 0; i < 30; i ++) {
            String content = testEnvironment.GenerateRandomString(32);
            String file = UUID.randomUUID().toString() + ".png";
            fileHashMap.put(content, file);
            command += "echo " + content + " > " + file + "\n";
        }
    }

    @Test
    public void AllFilesTest() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();

//        for (int i = 0; i < 20; i ++) {
//            String content = testEnvironment.GenerateRandomString(32);
//            String file = UUID.randomUUID().toString() + ".txt";
//            fileHashMap.put(content, file);
//            BatchFile batchFile = new BatchFile("echo " + content + " > " + file);
//            project.getBuildersList().add(batchFile);
//        }
//
//        for (int i = 0; i < 30; i ++) {
//            String content = testEnvironment.GenerateRandomString(32);
//            String file = UUID.randomUUID().toString() + ".png";
//            fileHashMap.put(content, file);
//            BatchFile batchFile = new BatchFile("echo " + content + " > " + file);
//            project.getBuildersList().add(batchFile);
//        }

        BatchFile batchFile = new BatchFile(command);
        project.getBuildersList().add(batchFile);

        filesPath = "*";
        WAStoragePublisher publisher = new WAStoragePublisher(
                testEnvironment.storageCredentialId, filesPath, storageType, containername, fileShareName);
        project.getPublishersList().add(publisher);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        int count = 0;
        for (ListBlobItem blobItem : container.listBlobs()) {
            count ++;
            if (blobItem instanceof CloudBlockBlob) {
                CloudBlockBlob blob = (CloudBlockBlob) blobItem;
                String content = blob.downloadText();
                assertEquals(blob.getName().trim(), fileHashMap.get(content.trim()));
            }
        }
        assertEquals(count, 50);
    }

    @Test
    public void TxtFilesTest() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();

//        for (int i = 0; i < 20; i ++) {
//            String content = testEnvironment.GenerateRandomString(32);
//            String file = UUID.randomUUID().toString() + ".txt";
//            fileHashMap.put(content, file);
//            BatchFile batchFile = new BatchFile("echo " + content + " > " + file);
//            project.getBuildersList().add(batchFile);
//        }
//
//        for (int i = 0; i < 30; i ++) {
//            String content = testEnvironment.GenerateRandomString(32);
//            String file = UUID.randomUUID().toString() + ".png";
//            fileHashMap.put(content, file);
//            BatchFile batchFile = new BatchFile("echo " + content + " > " + file);
//            project.getBuildersList().add(batchFile);
//        }

        BatchFile batchFile = new BatchFile(command);
        project.getBuildersList().add(batchFile);

        filesPath = "*.txt";
        WAStoragePublisher publisher = new WAStoragePublisher(
                testEnvironment.storageCredentialId, filesPath, storageType, containername, fileShareName);
        project.getPublishersList().add(publisher);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        int count = 0;
        for (ListBlobItem blobItem : container.listBlobs()) {
            count ++;
            if (blobItem instanceof CloudBlockBlob) {
                CloudBlockBlob blob = (CloudBlockBlob) blobItem;
                String content = blob.downloadText();
                assertEquals(blob.getName().trim(), fileHashMap.get(content.trim()));
            }
        }
        assertEquals(count, 20);
    }

    @After
    public void tearDown() throws Exception {
        container.deleteIfExists();
    }
}
