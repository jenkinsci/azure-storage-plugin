package IntegrationTests;

import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.microsoftopentechnologies.windowsazurestorage.AzureBlobMetadataPair;
import com.microsoftopentechnologies.windowsazurestorage.AzureBlobProperties;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.service.UploadToBlobService;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadServiceData;
import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadType;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author arroyc
 */
@WithJenkins
class WAStorageClientUploadIT extends IntegrationTest {

    private static final Logger LOGGER = Logger.getLogger(WAStorageClientUploadIT.class.getName());
    private String containerName;

    @BeforeEach
    void setUp() throws Exception {
            containerName = "testupload" + TestEnvironment.generateRandomString(15);
            testEnv = new TestEnvironment(containerName);
            File directory = new File(containerName);
            if (!directory.exists()) {
                boolean mkdir = directory.mkdir();
                if (!mkdir) {
                    throw new IllegalStateException("directory " + containerName + " failed to create");
                }
            }

            testEnv.blobClient = new BlobServiceClientBuilder()
                    .credential(new StorageSharedKeyCredential(testEnv.azureStorageAccountName, testEnv.azureStorageAccountKey1))
                    .endpoint("https://" + testEnv.azureStorageAccountName + ".blob.core.windows.net")
                    .buildClient();
	    testEnv.container = testEnv.blobClient.getBlobContainerClient(containerName);
            if (!testEnv.container.exists()) {
                testEnv.container.create();
            }
            for (int i = 0; i < TestEnvironment.TOTAL_FILES; i++) {
                String tempContent = UUID.randomUUID().toString();
                File temp = new File(directory.getAbsolutePath(), "upload" + tempContent + ".txt");
                FileUtils.writeStringToFile(temp, tempContent, StandardCharsets.UTF_8);
                testEnv.uploadFileList.put(tempContent, temp);
            }
    }

    /**
     * Test of validateStorageAccount method, of class WAStorageClient.
     */
    @Test
    void testValidateStorageAccount() throws Exception {
        System.out.println("validateStorageAccount");
        StorageAccountInfo storageAccount = testEnv.sampleStorageAccount;
        boolean result = AzureUtils.validateStorageAccount(storageAccount, false);
	    assertTrue(result);
        if (testEnv.container.exists()) {
            testEnv.container.delete();
        }
    }

    /**
     * Test of validateStorageAccount method, of class WAStorageClient.
     */
    @Test
    void testInvalidateStorageAccount1() {
        System.out.println("Testing Invalid StorageAccount");

        assertThrows(WAStorageException.class, () -> AzureUtils.validateStorageAccount(new StorageAccountInfo(testEnv.azureStorageAccountName, "asdhasdh@asdas!@234=", testEnv.blobURL, testEnv.cdnURL), false));

            if (testEnv.container.exists()) {
                testEnv.container.delete();
            }
    }

    /**
     * Test of validateStorageAccount method, of class WAStorageClient.
     */
    @Test
    void testInvalidateStorageAccount2() {
        System.out.println("Testing Invalid StorageAccount");
        assertThrows(WAStorageException.class, () -> AzureUtils.validateStorageAccount(new StorageAccountInfo("rfguthio123", testEnv.azureStorageAccountKey2, testEnv.blobURL, testEnv.cdnURL), false));

            if (testEnv.container.exists()) {
                testEnv.container.delete();
            }
    }

    /**
     * Test of validateStorageAccount method, of class WAStorageClient.
     */
    @Test
    void testInvalidateStorageAccount3() {
        System.out.println("Testing Invalid StorageAccount");
        assertThrows(WAStorageException.class, () -> AzureUtils.validateStorageAccount(new StorageAccountInfo(null, null, null, null), false));

            if (testEnv.container.exists()) {
                testEnv.container.delete();
            }
    }

    /**
     * Test of upload method, of class WAStorageClient.
     */
    @Test
    void testUpload() throws Exception {
            Run mockRun = mock(Run.class);
            Launcher mockLauncher = mock(Launcher.class);
            List<AzureBlobMetadataPair> metadata = new ArrayList<>();

            File workspaceDir = new File(containerName);
            FilePath workspace = new FilePath(mockLauncher.getChannel(), workspaceDir.getAbsolutePath());

            UploadServiceData serviceData = new UploadServiceData(mockRun, workspace, mockLauncher, TaskListener.NULL, testEnv.sampleStorageAccount);
            serviceData.setContainerName(testEnv.containerName);
            serviceData.setBlobProperties(new AzureBlobProperties());
            serviceData.setPubAccessible(false);
            serviceData.setCleanUpContainerOrShare(false);
            serviceData.setFilePath("*.txt");
            serviceData.setVirtualPath("");
            serviceData.setExcludedFilesPath("");
            serviceData.setUploadType(UploadType.INDIVIDUAL);
            serviceData.setAzureBlobMetadata(metadata);

            UploadToBlobService service = new UploadToBlobService(serviceData);
            service.execute();

            for (BlobItem blobItem : testEnv.container.listBlobs()) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                testEnv.container.getBlobClient(blobItem.getName()).downloadStream(os);
                String downloadedContent = os.toString(StandardCharsets.UTF_8);
                File temp = testEnv.uploadFileList.get(downloadedContent);
                String tempContent = FileUtils.readFileToString(temp, "utf-8");
                //check for filenames
                assertEquals(tempContent, downloadedContent);
                //check for file contents
                assertEquals("upload" + downloadedContent + ".txt", temp.getName());
                temp.delete();
            }
            if (testEnv.container.exists()) {
                testEnv.container.delete();
            }
    }

    @Test
    void testBlobPropertiesAndMetadata() throws Exception {
            Run mockRun = mock(Run.class);
            Launcher mockLauncher = mock(Launcher.class);

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

            UploadServiceData serviceData = new UploadServiceData(mockRun, workspace, mockLauncher, TaskListener.NULL, testEnv.sampleStorageAccount);
            serviceData.setContainerName(testEnv.containerName);
            serviceData.setBlobProperties(blobProperties);
            serviceData.setPubAccessible(false);
            serviceData.setCleanUpContainerOrShare(false);
            serviceData.setFilePath(firstFile.getName()); // Upload the first file only for efficiency
            serviceData.setVirtualPath("");
            serviceData.setExcludedFilesPath("");
            serviceData.setUploadType(UploadType.INDIVIDUAL);
            serviceData.setAzureBlobMetadata(metadata);

            UploadToBlobService service = new UploadToBlobService(serviceData);
            service.execute();

            BlobProperties downloadedProps = testEnv.container.getBlobClient(firstFile.getName()).getBlockBlobClient().getProperties();

            assertEquals(blobProperties.getCacheControl(), downloadedProps.getCacheControl());
            assertEquals(blobProperties.getContentEncoding(), downloadedProps.getContentEncoding());
            assertEquals(blobProperties.getContentLanguage(), downloadedProps.getContentLanguage());
            assertEquals(blobProperties.getContentType(), downloadedProps.getContentType());

            Map<String, String> downloadedMeta = downloadedProps.getMetadata();
            for (AzureBlobMetadataPair pair : metadata) {
                assertEquals(pair.getValue(), downloadedMeta.get(pair.getKey()));
            }

            if (testEnv.container.exists()) {
                testEnv.container.delete();
            }
    }

    @Test
    void testEmptyBlobMetadata() throws Exception {
            Run mockRun = mock(Run.class);
            Launcher mockLauncher = mock(Launcher.class);

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

            UploadServiceData serviceData = new UploadServiceData(mockRun, workspace, mockLauncher, TaskListener.NULL, testEnv.sampleStorageAccount);
            serviceData.setContainerName(testEnv.containerName);
            serviceData.setBlobProperties(blobProperties);
            serviceData.setPubAccessible(false);
            serviceData.setCleanUpContainerOrShare(false);
            serviceData.setFilePath(firstFile.getName()); // Upload the first file only for efficiency
            serviceData.setVirtualPath("");
            serviceData.setExcludedFilesPath("");
            serviceData.setUploadType(UploadType.INDIVIDUAL);
            serviceData.setAzureBlobMetadata(metadata);

            UploadToBlobService service = new UploadToBlobService(serviceData);
            service.execute();

            BlobProperties downloadedBlob = testEnv.container.getBlobClient(firstFile.getName()).getBlockBlobClient().getProperties();

            Map<String, String> downloadedMeta = downloadedBlob.getMetadata();
            assertTrue(downloadedMeta.isEmpty());

            if (testEnv.container.exists()) {
                testEnv.container.delete();
            }
    }

    @Test
    void testEnvVarResolve() throws Exception {
        EnvVars mockEnv = new EnvVars(
                "MY_CONTENT_TYPE", "text/plain",
                "MY_META_KEY", "foo",
                "MY_META_VALUE", "bar"
        );
        Run mockRun = mock(Run.class);
        when(mockRun.getEnvironment(any(TaskListener.class))).thenReturn(mockEnv);
        Launcher mockLauncher = mock(Launcher.class);
        AzureBlobProperties blobProperties = new AzureBlobProperties(
                null,
                null,
                null,
                "${MY_CONTENT_TYPE}",
                false
        );
        List<AzureBlobMetadataPair> metadata = List.of(
		        new AzureBlobMetadataPair("${MY_META_KEY}", "${MY_META_VALUE}")
        );

        Iterator it = testEnv.uploadFileList.entrySet().iterator();
        Map.Entry firstPair = (Map.Entry) it.next();
        File firstFile = (File) firstPair.getValue();
        File workspaceDir = new File(containerName);
        FilePath workspace = new FilePath(mockLauncher.getChannel(), workspaceDir.getAbsolutePath());

        UploadServiceData serviceData = new UploadServiceData(mockRun, workspace, mockLauncher, TaskListener.NULL, testEnv.sampleStorageAccount);
        serviceData.setContainerName(testEnv.containerName);
        serviceData.setBlobProperties(blobProperties);
        serviceData.setPubAccessible(false);
        serviceData.setCleanUpContainerOrShare(false);
        serviceData.setFilePath(firstFile.getName()); // Upload the first file only for efficiency
        serviceData.setVirtualPath("");
        serviceData.setExcludedFilesPath("");
        serviceData.setUploadType(UploadType.INDIVIDUAL);
        serviceData.setAzureBlobMetadata(metadata);

        UploadToBlobService service = new UploadToBlobService(serviceData);
        service.execute();

        BlobProperties downloadedProps = testEnv.container.getBlobClient(firstFile.getName()).getBlockBlobClient().getProperties();

        assertEquals("text/plain", downloadedProps.getContentType());

        Map<String, String> downloadedMeta = downloadedProps.getMetadata();
        assertEquals("bar", downloadedMeta.get("foo"));

        if (testEnv.container.exists()) {
            testEnv.container.delete();
        }
    }

    @AfterEach
    void tearDown() {
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
        if(testEnv.container!=null) {
            if (testEnv.container.exists()) {
                testEnv.container.delete();
            }
        }
        testEnv.uploadFileList.clear();
    }

}
