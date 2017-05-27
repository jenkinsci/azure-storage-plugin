package IntegrationTests;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobProperties;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;
import com.microsoftopentechnologies.windowsazurestorage.AzureBlob;
import com.microsoftopentechnologies.windowsazurestorage.AzureBlobMetadataPair;
import com.microsoftopentechnologies.windowsazurestorage.AzureBlobProperties;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.service.UploadBlobService;
import com.microsoftopentechnologies.windowsazurestorage.service.model.PublisherServiceData;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadType;
import hudson.EnvVars;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author arroyc
 */
public class WAStorageClientUploadIT extends IntegrationTest {

    private static final Logger LOGGER = Logger.getLogger(WAStorageClientUploadIT.class.getName());

    private String containerName;

    @Before
    public void setUp() throws IOException {
        try {
            containerName = "testupload" + TestEnvironment.GenerateRandomString(15);
            testEnv = new TestEnvironment(containerName);
            File directory = new File(containerName);
            if (!directory.exists())
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
            assertTrue(e.getMessage(), false);
        }
    }

    /**
     * Test of validateStorageAccount method, of class WAStorageClient.
     */
    @Test
    public void testValidateStorageAccount() throws Exception {
        System.out.println("validateStorageAccount");
        StorageAccountInfo storageAccount = testEnv.sampleStorageAccount;
        boolean result = AzureUtils.validateStorageAccount(storageAccount);
        assertEquals(true, result);
        assertEquals(true, AzureUtils.validateStorageAccount(new StorageAccountInfo(testEnv.azureStorageAccountName, testEnv.azureStorageAccountKey1, "")));
        testEnv.container.deleteIfExists();
    }

    /**
     * Test of validateStorageAccount method, of class WAStorageClient.
     */
    @Test(expected = WAStorageException.class)
    public void testInvalidateStorageAccount1() throws Exception {
        System.out.println("Testing Invalid StorageAccount");
        AzureUtils.validateStorageAccount(new StorageAccountInfo(testEnv.azureStorageAccountName, "asdhasdh@asdas!@234=", testEnv.blobURL));
        testEnv.container.deleteIfExists();
    }

    /**
     * Test of validateStorageAccount method, of class WAStorageClient.
     */
    @Test(expected = WAStorageException.class)
    public void testInvalidateStorageAccount2() throws Exception {
        System.out.println("Testing Invalid StorageAccount");
        AzureUtils.validateStorageAccount(new StorageAccountInfo("rfguthio123", testEnv.azureStorageAccountKey2, testEnv.blobURL));
        testEnv.container.deleteIfExists();
    }

    /**
     * Test of validateStorageAccount method, of class WAStorageClient.
     */
    @Test(expected = WAStorageException.class)
    public void testInvalidateStorageAccount3() throws Exception {
        System.out.println("Testing Invalid StorageAccount");
        AzureUtils.validateStorageAccount(new StorageAccountInfo(null, null, null));
        testEnv.container.deleteIfExists();
    }

    /**
     * Test of upload method, of class WAStorageClient.
     */
    @Test
    public void testUpload() {
        try {
            Run mockRun = mock(Run.class);
            Launcher mockLauncher = mock(Launcher.class);
            List<AzureBlob> individualBlobs = new ArrayList<>();
            List<AzureBlob> archiveBlobs = new ArrayList<>();
            AzureBlobProperties blobProperties = mock(AzureBlobProperties.class);
            List<AzureBlobMetadataPair> metadata = new ArrayList<>();

            File workspaceDir = new File(containerName);
            FilePath workspace = new FilePath(mockLauncher.getChannel(), workspaceDir.getAbsolutePath());

            PublisherServiceData serviceData = new PublisherServiceData(mockRun, workspace, mockLauncher, TaskListener.NULL, testEnv.sampleStorageAccount);
            serviceData.setContainerName(testEnv.containerName);
            serviceData.setBlobProperties(blobProperties);
            serviceData.setPubAccessible(false);
            serviceData.setCleanUpContainer(false);
            serviceData.setFilePath("*.txt");
            serviceData.setVirtualPath("");
            serviceData.setExcludedFilesPath("");
            serviceData.setUploadType(UploadType.INDIVIDUAL);
            serviceData.setAzureBlobMetadata(metadata);

            UploadBlobService service = new UploadBlobService(serviceData);
            service.execute();

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
            assertTrue(e.getMessage(), false);
        }
    }

    @Test
    public void testBlobPropertiesAndMetadata() {
        try {
            Run mockRun = mock(Run.class);
            Launcher mockLauncher = mock(Launcher.class);
            List<AzureBlob> individualBlobs = new ArrayList<>();
            List<AzureBlob> archiveBlobs = new ArrayList<>();
            AzureBlobProperties blobProperties = new AzureBlobProperties(
                "no-cache",
                "identity",
                "en-US",
                "text/plain",
                false
            );
            List<AzureBlobMetadataPair> metadata = Arrays.asList(
                    new AzureBlobMetadataPair("k1", "v1"),
                    new AzureBlobMetadataPair("k2", "v2")
            );

            Iterator it = testEnv.uploadFileList.entrySet().iterator();
            Map.Entry firstPair = (Map.Entry) it.next();
            File firstFile = (File) firstPair.getValue();
            File workspaceDir = new File(containerName);
            FilePath workspace = new FilePath(mockLauncher.getChannel(), workspaceDir.getAbsolutePath());

            PublisherServiceData serviceData = new PublisherServiceData(mockRun, workspace, mockLauncher, TaskListener.NULL, testEnv.sampleStorageAccount);
            serviceData.setContainerName(testEnv.containerName);
            serviceData.setBlobProperties(blobProperties);
            serviceData.setPubAccessible(false);
            serviceData.setCleanUpContainer(false);
            serviceData.setFilePath(firstFile.getName()); // Upload the first file only for efficiency
            serviceData.setVirtualPath("");
            serviceData.setExcludedFilesPath("");
            serviceData.setUploadType(UploadType.INDIVIDUAL);
            serviceData.setAzureBlobMetadata(metadata);

            UploadBlobService service = new UploadBlobService(serviceData);
            service.execute();

            CloudBlockBlob downloadedBlob = testEnv.container.getBlockBlobReference(firstFile.getName());
            downloadedBlob.downloadAttributes();

            BlobProperties downloadedProps = downloadedBlob.getProperties();
            assertEquals(blobProperties.getCacheControl(), downloadedProps.getCacheControl());
            assertEquals(blobProperties.getContentEncoding(), downloadedProps.getContentEncoding());
            assertEquals(blobProperties.getContentLanguage(), downloadedProps.getContentLanguage());
            assertEquals(blobProperties.getContentType(), downloadedProps.getContentType());

            HashMap<String, String> downloadedMeta = downloadedBlob.getMetadata();
            for (AzureBlobMetadataPair pair : metadata) {
                assertEquals(pair.getValue(), downloadedMeta.get(pair.getKey()));
            }

            testEnv.container.deleteIfExists();

        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testEmptyBlobMetadata() {
        try {
            Run mockRun = mock(Run.class);
            Launcher mockLauncher = mock(Launcher.class);
            List<AzureBlob> individualBlobs = new ArrayList<>();
            List<AzureBlob> archiveBlobs = new ArrayList<>();
            AzureBlobProperties blobProperties = new AzureBlobProperties(null, null, null, null, false);
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
            File workspaceDir = new File(containerName);
            FilePath workspace = new FilePath(mockLauncher.getChannel(), workspaceDir.getAbsolutePath());

            PublisherServiceData serviceData = new PublisherServiceData(mockRun, workspace, mockLauncher, TaskListener.NULL, testEnv.sampleStorageAccount);
            serviceData.setContainerName(testEnv.containerName);
            serviceData.setBlobProperties(blobProperties);
            serviceData.setPubAccessible(false);
            serviceData.setCleanUpContainer(false);
            serviceData.setFilePath(firstFile.getName()); // Upload the first file only for efficiency
            serviceData.setVirtualPath("");
            serviceData.setExcludedFilesPath("");
            serviceData.setUploadType(UploadType.INDIVIDUAL);
            serviceData.setAzureBlobMetadata(metadata);

            UploadBlobService service = new UploadBlobService(serviceData);
            service.execute();

            CloudBlockBlob downloadedBlob = testEnv.container.getBlockBlobReference(firstFile.getName());
            downloadedBlob.downloadAttributes();

            HashMap<String, String> downloadedMeta = downloadedBlob.getMetadata();
            assertTrue(downloadedMeta.isEmpty());

            testEnv.container.deleteIfExists();

        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testEnvVarResolve() {
        try {
            EnvVars mockEnv = new EnvVars(
                    "MY_CONTENT_TYPE", "text/plain",
                    "MY_META_KEY", "foo",
                    "MY_META_VALUE", "bar"
            );
            Run mockRun = mock(Run.class);
            when(mockRun.getEnvironment(any(TaskListener.class))).thenReturn(mockEnv);
            Launcher mockLauncher = mock(Launcher.class);
            List<AzureBlob> individualBlobs = new ArrayList<>();
            List<AzureBlob> archiveBlobs = new ArrayList<>();
            AzureBlobProperties blobProperties = new AzureBlobProperties(
                    null,
                    null,
                    null,
                    "${MY_CONTENT_TYPE}",
                    false
            );
            List<AzureBlobMetadataPair> metadata = Arrays.asList(
                    new AzureBlobMetadataPair("${MY_META_KEY}", "${MY_META_VALUE}")
            );

            Iterator it = testEnv.uploadFileList.entrySet().iterator();
            Map.Entry firstPair = (Map.Entry) it.next();
            File firstFile = (File) firstPair.getValue();
            File workspaceDir = new File(containerName);
            FilePath workspace = new FilePath(mockLauncher.getChannel(), workspaceDir.getAbsolutePath());

            PublisherServiceData serviceData = new PublisherServiceData(mockRun, workspace, mockLauncher, TaskListener.NULL, testEnv.sampleStorageAccount);
            serviceData.setContainerName(testEnv.containerName);
            serviceData.setBlobProperties(blobProperties);
            serviceData.setPubAccessible(false);
            serviceData.setCleanUpContainer(false);
            serviceData.setFilePath(firstFile.getName()); // Upload the first file only for efficiency
            serviceData.setVirtualPath("");
            serviceData.setExcludedFilesPath("");
            serviceData.setUploadType(UploadType.INDIVIDUAL);
            serviceData.setAzureBlobMetadata(metadata);

            UploadBlobService service = new UploadBlobService(serviceData);
            service.execute();

            CloudBlockBlob downloadedBlob = testEnv.container.getBlockBlobReference(firstFile.getName());
            downloadedBlob.downloadAttributes();

            BlobProperties downloadedProps = downloadedBlob.getProperties();
            assertEquals("text/plain", downloadedProps.getContentType());

            HashMap<String, String> downloadedMeta = downloadedBlob.getMetadata();
            assertEquals("bar", downloadedMeta.get("foo"));

            testEnv.container.deleteIfExists();

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
        /*
        ResultSegment<CloudBlobContainer> containerList = blobClient.listContainersSegmented("testupload");
        int totalContainers = containerList.getLength();
        while(totalContainers > 0){
            containerList.getResults().get(--totalContainers).deleteIfExists();
        }*/
        testEnv.container.deleteIfExists();
        testEnv.uploadFileList.clear();
    }

}
