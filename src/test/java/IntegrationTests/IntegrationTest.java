package IntegrationTests;

/**
 *
 * @author arroyc
 */
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.file.CloudFileShare;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureCredentials;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;
import org.junit.ClassRule;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.UUID;

/*
To execute the integration tests you need to set the credentials env variables (the ones that don't have a default) and run mvn failsafe:integration-test
*/
public class IntegrationTest {
    @ClassRule 
    public static JenkinsRule j = new JenkinsRule();
    
    protected static class TestEnvironment {
        public final String azureStorageAccountName;
        public final String azureStorageAccountKey1;
        public final String azureStorageAccountKey2;
        
        public final String blobURL;
        public final StorageAccountInfo sampleStorageAccount;
        public static final int TOTAL_FILES = 50;
        public HashMap<String, File> downloadFileList = new HashMap<>();
        public HashMap<String, File> uploadFileList = new HashMap<>();
        public String containerName;
        public String shareName;
        public CloudBlobContainer container;
        public CloudStorageAccount account;
        public CloudBlobClient blobClient;
        public CloudFileShare fileShare;

        TestEnvironment(String name) throws URISyntaxException {
            azureStorageAccountName = TestEnvironment.loadFromEnv("AZURE_STORAGE_TEST_STORAGE_ACCOUNT_NAME");
            azureStorageAccountKey1 = TestEnvironment.loadFromEnv("AZURE_STORAGE_TEST_STORAGE_ACCOUNT_KEY1");
            azureStorageAccountKey2 = TestEnvironment.loadFromEnv("AZURE_STORAGE_TEST_STORAGE_ACCOUNT_KEY2");
            
            blobURL = Utils.DEF_BLOB_URL;
            AzureCredentials.StorageAccountCredential u = new AzureCredentials.StorageAccountCredential(azureStorageAccountName, azureStorageAccountKey1, blobURL);
            sampleStorageAccount = new StorageAccountInfo(azureStorageAccountName,azureStorageAccountKey1, blobURL);
            containerName = name;
            shareName = name;
        }
        private static String loadFromEnv(final String name) {
            return TestEnvironment.loadFromEnv(name, "");
        }
        private static String loadFromEnv(final String name, final String defaultValue) {
            final String value = System.getenv(name);
            if (value == null || value.isEmpty()) {
                return defaultValue;
            } else {
                return value;
            }
        }

        public static String GenerateRandomString(int length) {
            String uuid = UUID.randomUUID().toString();
            return uuid.replaceAll("[^a-z0-9]","a").substring(0, length);
        }
    }

    protected TestEnvironment testEnv = null;

}
