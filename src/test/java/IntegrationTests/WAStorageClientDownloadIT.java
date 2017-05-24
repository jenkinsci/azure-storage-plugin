package IntegrationTests;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoftopentechnologies.windowsazurestorage.AzureStorageBuilderContext;
import com.microsoftopentechnologies.windowsazurestorage.WAStorageClient;
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
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 *
 * @author arroyc
 */
public class WAStorageClientDownloadIT extends IntegrationTest{
    
    private static final Logger LOGGER = Logger.getLogger(WAStorageClientDownloadIT.class.getName());
    
    @Before
    public void setUp() throws IOException {
        try {
            String containerName = "testdownload" + TestEnvironment.GenerateRandomString(15);
            testEnv = new TestEnvironment(containerName);
            testEnv.account = new CloudStorageAccount(new StorageCredentialsAccountAndKey(testEnv.azureStorageAccountName, testEnv.azureStorageAccountKey1));
            testEnv.blobClient = testEnv.account.createCloudBlobClient();
            File directory = new File(containerName);
            if(!directory.exists())
                directory.mkdir();
            testEnv.container = testEnv.blobClient.getContainerReference(containerName);
            testEnv.container.createIfNotExists();
            for (int i = 0; i < testEnv.TOTAL_FILES; i++) {
                String content = TestEnvironment.GenerateRandomString(15);
                File file = new File(directory, "download" + UUID.randomUUID().toString() + ".txt");
                FileUtils.writeStringToFile(file, content);
                testEnv.downloadFileList.put(content, file);
                
                LOGGER.log(Level.INFO, file.getAbsolutePath());
                CloudBlockBlob blob = testEnv.container.getBlockBlobReference(file.getName());
                blob.uploadFromFile(file.getAbsolutePath());
            }    
        } catch (Exception e){
            LOGGER.log(Level.SEVERE, null, e);
            Assert.assertTrue(e.getMessage(), false);
        }
    }

    @Test
    public void testDownloadfromContainer() {
        System.out.print("download without zip and flatten directory");
        try 
        {             
            //try to download the same file  
            LOGGER.log(Level.INFO, "container Namne for testDownloadfromContainer: "+testEnv.containerName);
            Run mockRun = mock(Run.class);
            Launcher mockLauncher = mock(Launcher.class);
            WAStorageClient mockStorageClient = spy(WAStorageClient.class);

            AzureStorageBuilderContext context = new AzureStorageBuilderContext(mockLauncher, mockRun, TaskListener.NULL);
            context.setStorageAccountInfo(testEnv.sampleStorageAccount);
            context.setIncludeFilesPattern("*.txt");
            context.setExcludeFilesPattern("archive.zip");

            File downloaded = new File(new File(".").getAbsolutePath(), TestEnvironment.GenerateRandomString(5));
            downloaded.mkdir();
            FilePath workspace= new FilePath(downloaded.getAbsoluteFile());
            context.setWorkspace(workspace);
            context.setContainerName(testEnv.containerName);
            int totalFiles = mockStorageClient.downloadFromContainer(context);
            
            
            assertEquals(testEnv.downloadFileList.size(),totalFiles);
            File[] listofFiles = downloaded.listFiles();
            assertEquals(testEnv.downloadFileList.size(),listofFiles.length);
            
            for(File each: listofFiles){
                assertEquals(true, each.isFile());
                String tempContent = FileUtils.readFileToString(each, "utf-8");
                File tempFile = testEnv.downloadFileList.get(tempContent);
                assertEquals(each.getName(), tempFile.getName());
                assertEquals(FileUtils.readFileToString(tempFile, "utf-8"), tempContent);
                each.delete();
 
            }
            testEnv.container.deleteIfExists();
            FileUtils.deleteDirectory(downloaded);
            
        }catch(Exception e){
            LOGGER.log(Level.SEVERE, null, e);
            Assert.assertTrue(e.getMessage(), false);
        }
    }
    
    
    @Test
    public void testDownloadfromContainerwithZIP() {
        System.out.print("download with archive ZIP");
        LOGGER.log(Level.INFO, "container Namne for testDownloadfromContainerwithZIP: "+testEnv.containerName);
        try {
            //try to download the same file     
            Run mockRun = mock(Run.class);
            Launcher mockLauncher = mock(Launcher.class);
            WAStorageClient mockStorageClient = spy(WAStorageClient.class);

            AzureStorageBuilderContext context = new AzureStorageBuilderContext(mockLauncher, mockRun, TaskListener.NULL);
            context.setStorageAccountInfo(testEnv.sampleStorageAccount);
            context.setIncludeFilesPattern("*.txt");

            File downloaded = new File(new File(".").getAbsolutePath(), TestEnvironment.GenerateRandomString(5));
            downloaded.mkdir();
            FilePath workspace = new FilePath(downloaded.getAbsoluteFile());
            context.setWorkspace(workspace);
            context.setContainerName(testEnv.containerName);
            int totalFiles = mockStorageClient.downloadFromContainer(context);

            assertEquals(testEnv.downloadFileList.size(), totalFiles);
            File[] listofFiles = downloaded.listFiles();
            assertEquals(testEnv.downloadFileList.size(), listofFiles.length);

            for (File each : listofFiles) {
                assertEquals(true, each.isFile());
                String tempContent = FileUtils.readFileToString(each, "utf-8");
                File tempFile = testEnv.downloadFileList.get(tempContent);
                assertEquals(each.getName(), tempFile.getName());
                assertEquals(FileUtils.readFileToString(tempFile, "utf-8"), tempContent);
                each.delete();

            }
            testEnv.container.deleteIfExists();
            FileUtils.deleteDirectory(downloaded);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, null, e);
            Assert.assertTrue(e.getMessage(), false);
        }
    }
    
    @Test
    public void testDownloadfromContainerFlattenDirectory() {
        System.out.print("download with flatten directory enabled");
        LOGGER.log(Level.INFO, "container Namne for testDownloadfromContainerFlattenDirectory: "+testEnv.containerName);
        try {
            //try to download the same file     
            Run mockRun = mock(Run.class);
            Launcher mockLauncher = mock(Launcher.class);
            WAStorageClient mockStorageClient = spy(WAStorageClient.class);

            AzureStorageBuilderContext context = new AzureStorageBuilderContext(mockLauncher, mockRun, TaskListener.NULL);
            context.setStorageAccountInfo(testEnv.sampleStorageAccount);
            context.setIncludeFilesPattern("*.txt");

            File downloaded = new File(new File(".").getAbsolutePath(), TestEnvironment.GenerateRandomString(5));
            downloaded.mkdir();
            FilePath workspace = new FilePath(downloaded.getAbsoluteFile());
            context.setWorkspace(workspace);
            context.setContainerName(testEnv.containerName);
            context.setFlattenDirectories(true);
            int totalFiles = mockStorageClient.downloadFromContainer(context);

            assertEquals(testEnv.downloadFileList.size(), totalFiles);
            File[] listofFiles = downloaded.listFiles();
            assertEquals(testEnv.downloadFileList.size(), listofFiles.length);

            for (File each : listofFiles) {
                assertEquals(true, each.isFile());
                String tempContent = FileUtils.readFileToString(each, "utf-8");
                File tempFile = testEnv.downloadFileList.get(tempContent);
                //check filename
                assertEquals(each.getName(), tempFile.getName());
                //check file content
                assertEquals(FileUtils.readFileToString(tempFile, "utf-8"), tempContent);
                each.delete();

            }
            FileUtils.deleteDirectory(downloaded);
            testEnv.container.deleteIfExists();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, null, e);
            Assert.assertTrue(e.getMessage(), false);
        }
    }
    
    @After
    public void tearDown() throws StorageException {
        System.gc();
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
