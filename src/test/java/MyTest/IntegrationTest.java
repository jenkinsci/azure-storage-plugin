package MyTest;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureCredentials;
import hudson.security.ACL;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

/**
 * Created by t-yuhang on 7/25/2017.
 */
public class IntegrationTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    private static final Logger LOGGER = Logger.getLogger(IntegrationTest.class.getName());

    protected static class TestEnvironment {
        public final String storageCredentialId;
        public final String azureStorageAccountName;
        public final String azureStorageAccountKey1;
        public final String azureStorageAccountKey2;
        public final String blobURL;

        public String loadFromEnv(String name) {
            String value = System.getenv(name);
            return value;
        }

        public String GenerateRandomString(int length) {
            String uuid = UUID.randomUUID().toString();
            return uuid.replaceAll("[^a-z0-9]", "a").substring(0, length);
        }

        TestEnvironment() throws Exception {
            azureStorageAccountName = loadFromEnv("Name");              // "5wdzqrwhxmtsodiag0";
            azureStorageAccountKey1 = loadFromEnv("Key1");              // "VZHBE8V0zyvWHQJKrNHqYXABA0Zy+3bZ5kvkYadU+xF+m9o6KEmSJaJt7np/Hy3k2CagUucjpqIK8LV1hA/YUQ=="
            azureStorageAccountKey2 = loadFromEnv("Key2");              // "d+hR14Oe3mCuQ/APAGhJsqqMWDhIumyaOqx8jz9bdUVWtDuk4Wrf4g1q/PK7HH2ivJ3ydCyhDoq3cVXkceJSfA=="
            blobURL = loadFromEnv("BlobURL");               // "https://5wdzqrwhxmtsodiag0.blob.core.windows.net/"

            AzureCredentials.StorageAccountCredential storageCreds = new AzureCredentials.StorageAccountCredential(
                    azureStorageAccountName, azureStorageAccountKey1, blobURL);
            storageCredentialId = storageCreds.getId();             // "3297e8751cadd7c174af1378e9fdf3fe"
            AzureCredentials azureCredentials = new AzureCredentials(CredentialsScope.GLOBAL, storageCredentialId,
                    null, azureStorageAccountName, azureStorageAccountKey1, blobURL);

            CredentialsStore credentialsStore = CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
            credentialsStore.addCredentials(Domain.global(), azureCredentials);
        }
    }

//    @Test
//    public void ConfigureTest() throws Exception {
//        TestEnvironment testEnvironment = new TestEnvironment();
//        AzureCredentials azureCredentials = CredentialsMatchers.firstOrNull(
//                CredentialsProvider.lookupCredentials(
//                        AzureCredentials.class,
//                        Jenkins.getInstance(),
//                        ACL.SYSTEM,
//                        Collections.<DomainRequirement>emptyList()),
//                CredentialsMatchers.withId(testEnvironment.storageCredentialId));
//        assertEquals(azureCredentials.getId(), testEnvironment.storageCredentialId);
//        assertEquals(azureCredentials.getStorageAccountName(), testEnvironment.azureStorageAccountName);
//        assertEquals(azureCredentials.getStorageKey(), Secret.fromString(testEnvironment.azureStorageAccountKey1).getEncryptedValue());
//        assertEquals(azureCredentials.getBlobEndpointURL(), testEnvironment.blobURL);
//    }
}
