package MyTest;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.file.CloudFileShare;
import com.microsoftopentechnologies.windowsazurestorage.AzureStorageBuilder;
import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.microsoftopentechnologies.windowsazurestorage.AzureStorageBuilder.DOWNLOAD_TYPE_CONTAINER;
import static org.junit.Assert.assertEquals;

/**
 * Created by t-yuhang on 7/25/2017.
 */
public class WAStorageClientDownloadIT extends IntegrationTest {

    private static final Logger LOGGER = Logger.getLogger(WAStorageClientDownloadIT.class.getName());

    private TestEnvironment testEnvironment;
    private String downloadType;
    private String containername;

    private String includeFilesPattern;
    private String excludeFilesPattern;
    private String downloadDirLoc;

    private boolean flattenDirectories;
    private boolean includeArchiveZips;
    private boolean deleteFromAzureAfterDownload;

    private CloudStorageAccount account;
    private CloudBlobClient blobClient;
    private CloudBlobContainer container;

    private HashMap<String, String> fileHashMap = new HashMap<>();

    @Before
    public void setUp() throws Exception{
        testEnvironment = new TestEnvironment();
        downloadType = DOWNLOAD_TYPE_CONTAINER;
        containername = testEnvironment.GenerateRandomString(6);

        account = new CloudStorageAccount(new StorageCredentialsAccountAndKey(
                testEnvironment.azureStorageAccountName, testEnvironment.azureStorageAccountKey1));
        blobClient = account.createCloudBlobClient();
        container = blobClient.getContainerReference(containername);
        container.createIfNotExists();

        File dir = new File("WAStorageClientDownloadIT");
        if(!dir.exists()) {
            dir.mkdir();
        }

        for(int i = 0; i < 20; i ++) {
            String content = testEnvironment.GenerateRandomString(32);
            File file = new File(dir, UUID.randomUUID().toString() + ".txt");
            FileUtils.writeStringToFile(file, content);
            fileHashMap.put(content, file.getName());
            CloudBlockBlob blob = container.getBlockBlobReference(file.getName());
            blob.uploadFromFile(file.getAbsolutePath());
        }
        for(int i = 0; i < 30; i ++) {
            String content = testEnvironment.GenerateRandomString(32);
            File file = new File(dir, UUID.randomUUID().toString() + ".png");
            FileUtils.writeStringToFile(file, content);
            fileHashMap.put(content, file.getName());
            CloudBlockBlob blob = container.getBlockBlobReference(file.getName());
            blob.uploadFromFile(file.getAbsolutePath());
        }
    }

    @Test
    public void AllFilesTest() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        AzureStorageBuilder builder = new AzureStorageBuilder(testEnvironment.storageCredentialId, downloadType);
        builder.setContainerName(containername);
        includeFilesPattern = "*";
        builder.setIncludeFilesPattern(includeFilesPattern);
        project.getBuildersList().add(builder);
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        FilePath filePath = build.getWorkspace();
        File file = new File(filePath.getRemote());
        File[] files = file.listFiles();
        assertEquals(files.length, 50);
        for (File f : files) {
            String content = FileUtils.readFileToString(f);
            assertEquals(f.getName().trim(), fileHashMap.get(content).trim());
        }
    }

    @Test
    public void TxtFilesTest() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        AzureStorageBuilder builder = new AzureStorageBuilder(testEnvironment.storageCredentialId, downloadType);
        builder.setContainerName(containername);
        includeFilesPattern = "*.txt";
        builder.setIncludeFilesPattern(includeFilesPattern);
        project.getBuildersList().add(builder);
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        FilePath filePath = build.getWorkspace();
        File file = new File(filePath.getRemote());
        File[] files = file.listFiles();
        assertEquals(files.length, 20);
        for (File f : files) {
            String content = FileUtils.readFileToString(f);
            assertEquals(f.getName().trim(), fileHashMap.get(content).trim());
        }
    }

    @Test
    public void PngFilesTest() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        AzureStorageBuilder builder = new AzureStorageBuilder(testEnvironment.storageCredentialId, downloadType);
        builder.setContainerName(containername);
        includeFilesPattern = "*";
        builder.setIncludeFilesPattern(includeFilesPattern);
        excludeFilesPattern = "*.txt";
        builder.setExcludeFilesPattern(excludeFilesPattern);
        project.getBuildersList().add(builder);
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        FilePath filePath = build.getWorkspace();
        File file = new File(filePath.getRemote());
        File[] files = file.listFiles();
        assertEquals(files.length, 30);
        for (File f : files) {
            String content = FileUtils.readFileToString(f);
            assertEquals(f.getName().trim(), fileHashMap.get(content).trim());
        }
    }

    @Test
    public void SetDownLocTest() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        AzureStorageBuilder builder = new AzureStorageBuilder(testEnvironment.storageCredentialId, downloadType);
        builder.setContainerName(containername);
        includeFilesPattern = "*.txt";
        builder.setIncludeFilesPattern(includeFilesPattern);
        downloadDirLoc = project.getRootDir().getAbsolutePath() + "\\DownloadLoc";
        builder.setDownloadDirLoc(downloadDirLoc);
        project.getBuildersList().add(builder);
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        File file = new File(downloadDirLoc);
        File[] files = file.listFiles();
        assertEquals(files.length, 20);
        for (File f : files) {
            String content = FileUtils.readFileToString(f);
            assertEquals(f.getName().trim(), fileHashMap.get(content).trim());
        }
    }

    @After
    public void tearDown() throws Exception {
        container.deleteIfExists();
    }
}
