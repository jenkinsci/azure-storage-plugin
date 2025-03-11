package IntegrationTests;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.microsoftopentechnologies.windowsazurestorage.service.DownloadFromContainerService;
import com.microsoftopentechnologies.windowsazurestorage.service.model.DownloadServiceData;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * @author arroyc
 */
@WithJenkins
class WAStorageClientDownloadIT extends IntegrationTest {

    private static final Logger LOGGER = Logger.getLogger(WAStorageClientDownloadIT.class.getName());

    @BeforeEach
    void setUp() throws Exception {
        String containerName = "testdownload" + TestEnvironment.generateRandomString(15);
        testEnv = new TestEnvironment(containerName);
        testEnv.blobClient = new BlobServiceClientBuilder()
                .credential(new StorageSharedKeyCredential(testEnv.azureStorageAccountName, testEnv.azureStorageAccountKey1))
                .endpoint("https://" + testEnv.azureStorageAccountName + ".blob.core.windows.net")
                .buildClient();
        File directory = new File(containerName);
        if (!directory.exists()) {
            boolean mkdir = directory.mkdir();
            if (!mkdir) {
                throw new IllegalStateException("directory " + containerName + " failed to create");
            }
        }
        testEnv.container = testEnv.blobClient.getBlobContainerClient(containerName);
        if (!testEnv.container.exists()) {
            testEnv.container.create();
        }
        for (int i = 0; i < TestEnvironment.TOTAL_FILES; i++) {
            String content = TestEnvironment.generateRandomString(15);
            File file = new File(directory, "download" + UUID.randomUUID() + ".txt");
            FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8);
            testEnv.downloadFileList.put(content, file);

            LOGGER.log(Level.INFO, file.getAbsolutePath());
            BlobClient blob = testEnv.container.getBlobClient(file.getName());
            blob.uploadFromFile(file.getAbsolutePath());
        }
    }

    @Test
    void testDownloadfromContainer() throws Exception {
        System.out.print("download without zip and flatten directory");
        //try to download the same file
        LOGGER.log(Level.INFO, "container Name for testDownloadfromContainer: " + testEnv.containerName);
        Run mockRun = mock(Run.class);
        Launcher mockLauncher = mock(Launcher.class);

        File downloaded = new File(new File(".").getAbsolutePath(), TestEnvironment.generateRandomString(5));
        downloaded.mkdir();
        FilePath workspace = new FilePath(downloaded.getAbsoluteFile());

        DownloadServiceData serviceData = new DownloadServiceData(mockRun, workspace, mockLauncher, TaskListener.NULL, testEnv.sampleStorageAccount);
        serviceData.setIncludeFilesPattern("*.txt");
        serviceData.setExcludeFilesPattern("archive.zip");
        serviceData.setContainerName(testEnv.containerName);
        DownloadFromContainerService service = new DownloadFromContainerService(serviceData);

        int totalFiles = service.execute();

        assertEquals(testEnv.downloadFileList.size(), totalFiles);
        File[] listofFiles = downloaded.listFiles();
        assertEquals(testEnv.downloadFileList.size(), listofFiles.length);

        for (File each : listofFiles) {
            assertTrue(each.isFile());
            String tempContent = FileUtils.readFileToString(each, "utf-8");
            File tempFile = testEnv.downloadFileList.get(tempContent);
            assertEquals(each.getName(), tempFile.getName());
            assertEquals(FileUtils.readFileToString(tempFile, "utf-8"), tempContent);
            each.delete();

        }
        if (testEnv.container.exists()) {
            testEnv.container.delete();
        }
        FileUtils.deleteDirectory(downloaded);
    }

    @Test
    void testDownloadfromContainerwithZIP() throws Exception {
        System.out.print("download with archive ZIP");
        LOGGER.log(Level.INFO, "container Namne for testDownloadfromContainerwithZIP: " + testEnv.containerName);
        //try to download the same file
        Run mockRun = mock(Run.class);
        Launcher mockLauncher = mock(Launcher.class);

        File downloaded = new File(new File(".").getAbsolutePath(), TestEnvironment.generateRandomString(5));
        downloaded.mkdir();
        FilePath workspace = new FilePath(downloaded.getAbsoluteFile());

        DownloadServiceData serviceData = new DownloadServiceData(mockRun, workspace, mockLauncher, TaskListener.NULL, testEnv.sampleStorageAccount);
        serviceData.setIncludeFilesPattern("*.txt");
        serviceData.setContainerName(testEnv.containerName);

        DownloadFromContainerService service = new DownloadFromContainerService(serviceData);
        int totalFiles = service.execute();

        assertEquals(testEnv.downloadFileList.size(), totalFiles);
        File[] listofFiles = downloaded.listFiles();
        assertEquals(testEnv.downloadFileList.size(), listofFiles.length);

        for (File each : listofFiles) {
            assertTrue(each.isFile());
            String tempContent = FileUtils.readFileToString(each, "utf-8");
            File tempFile = testEnv.downloadFileList.get(tempContent);
            assertEquals(each.getName(), tempFile.getName());
            assertEquals(FileUtils.readFileToString(tempFile, "utf-8"), tempContent);
            each.delete();

        }
        if (testEnv.container.exists()) {
            testEnv.container.delete();
        }
        FileUtils.deleteDirectory(downloaded);
    }

    @Test
    void testDownloadfromContainerFlattenDirectory() throws Exception {
        System.out.print("download with flatten directory enabled");
        LOGGER.log(Level.INFO, "container Namne for testDownloadfromContainerFlattenDirectory: " + testEnv.containerName);
        //try to download the same file
        Run mockRun = mock(Run.class);
        Launcher mockLauncher = mock(Launcher.class);

        File downloaded = new File(new File(".").getAbsolutePath(), TestEnvironment.generateRandomString(5));
        downloaded.mkdir();
        FilePath workspace = new FilePath(downloaded.getAbsoluteFile());

        DownloadServiceData serviceData = new DownloadServiceData(mockRun, workspace, mockLauncher, TaskListener.NULL, testEnv.sampleStorageAccount);
        serviceData.setIncludeFilesPattern("*.txt");
        serviceData.setContainerName(testEnv.containerName);
        serviceData.setFlattenDirectories(true);

        DownloadFromContainerService service = new DownloadFromContainerService(serviceData);
        int totalFiles = service.execute();

        assertEquals(testEnv.downloadFileList.size(), totalFiles);
        File[] listofFiles = downloaded.listFiles();
        assertEquals(testEnv.downloadFileList.size(), listofFiles.length);

        for (File each : listofFiles) {
            assertTrue(each.isFile());
            String tempContent = FileUtils.readFileToString(each, "utf-8");
            File tempFile = testEnv.downloadFileList.get(tempContent);
            //check filename
            assertEquals(each.getName(), tempFile.getName());
            //check file content
            assertEquals(FileUtils.readFileToString(tempFile, "utf-8"), tempContent);
            each.delete();

        }
        FileUtils.deleteDirectory(downloaded);
        if (testEnv.container.exists()) {
            testEnv.container.delete();
        }

    }

    @AfterEach
    void tearDown() {
        Iterator it = testEnv.downloadFileList.entrySet().iterator();
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
        testEnv.downloadFileList.clear();
    }

}
