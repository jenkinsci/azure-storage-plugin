package com.microsoftopentechnologies.windowsazurestorage;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import hudson.EnvVars;
import hudson.FilePath;
import junit.framework.TestCase;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class AzureBlobPropertiesTest extends TestCase {

    private File file;
    private FilePath filePath;

    @Before
    public void setUp() throws IOException {
        file = new File("index.html");
        file.deleteOnExit();
        FileUtils.writeStringToFile(file, "<p>Hello Azure!</p>");
        filePath = new FilePath(file);
    }

    @Test
    public void testEnvVarsResolve() throws URISyntaxException, StorageException, IOException, InterruptedException {
        final EnvVars env = new EnvVars("foo", "bar");
        final CloudBlob blob = new CloudBlockBlob(new URI("https://example.com/index.html"));
        final AzureBlobProperties props = new AzureBlobProperties(
                "${foo}",
                "${foo}",
                "${foo}",
                "${foo}",
                false
        );

        props.configure(blob, filePath, env);

        assertEquals("bar", blob.getProperties().getCacheControl());
        assertEquals("bar", blob.getProperties().getContentEncoding());
        assertEquals("bar", blob.getProperties().getContentLanguage());
        assertEquals("bar", blob.getProperties().getContentType());
    }

    @Test
    public void testDetectContentType() throws URISyntaxException, StorageException, IOException, InterruptedException {
        final EnvVars env = new EnvVars();
        final CloudBlob blob = new CloudBlockBlob(new URI("https://example.com/index.html"));
        final AzureBlobProperties props = new AzureBlobProperties(null, null, null, null, true);

        props.configure(blob, filePath, env);

        assertEquals("text/html", blob.getProperties().getContentType());
    }

    @Test
    public void testDoNotDetectContentTypeIfSet() throws URISyntaxException, StorageException, IOException, InterruptedException {
        final EnvVars env = new EnvVars();
        final CloudBlob blob = new CloudBlockBlob(new URI("https://example.com/index.html"));
        final AzureBlobProperties props = new AzureBlobProperties(null, null, null, "text/plain", true);

        props.configure(blob, filePath, env);

        assertEquals("text/plain", blob.getProperties().getContentType());
    }

    @Test
    public void testDoNotDetectContentTypeIfNotEnabled() throws URISyntaxException, StorageException, IOException, InterruptedException {
        final EnvVars env = new EnvVars();
        final CloudBlob blob = new CloudBlockBlob(new URI("https://example.com/index.html"));
        final AzureBlobProperties props = new AzureBlobProperties(null, null, null, null, false);

        props.configure(blob, filePath, env);

        assertNull(blob.getProperties().getContentType());
    }
}
