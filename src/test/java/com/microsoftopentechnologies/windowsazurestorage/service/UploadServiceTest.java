package com.microsoftopentechnologies.windowsazurestorage.service;

import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadServiceData;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.mockito.Mockito.mock;

public class UploadServiceTest {

    private Run run;
    private Launcher launcher;
    private FilePath workspace;
    private UploadServiceData serviceData;

    @Before
    public void setup() {
        run = mock(Run.class);
        launcher = mock(Launcher.class);

        File workspaceDir = new File("workspace");
        workspace = new FilePath(launcher.getChannel(), workspaceDir.getAbsolutePath());

        serviceData = new UploadServiceData(run, workspace, launcher, TaskListener.NULL, null);
    }

    /**
     * Adds a 'remove prefix' path to serviceData
     * Note that a trailing slash is always added in code, see {@link com.microsoftopentechnologies.windowsazurestorage.WAStoragePublisher#perform}
     */
    private void addRemovePrefix() {
        serviceData.setRemovePrefixPath("release/build/");
    }

    /**
     * Adds a virtual path to serviceData
     * Note that a trailing slash is always added in code, see {@link com.microsoftopentechnologies.windowsazurestorage.WAStoragePublisher#perform}
     */
    private void addVirtualPath() {
        //
        serviceData.setVirtualPath("virtual/");
    }

    private void testRemovePrefix1(UploadService uploadService) throws IOException, InterruptedException {
        File testFile = new File("workspace/release/build/test.txt");
        FilePath testFilePath = new FilePath(launcher.getChannel(), testFile.getAbsolutePath());
        Assert.assertEquals("test.txt", uploadService.getItemPath(testFilePath, "", uploadService.getServiceData()));
    }

    @Test
    public void testRemovePrefix1a() throws IOException, InterruptedException {
        addRemovePrefix();

        testRemovePrefix1(new UploadToBlobService(serviceData));
    }

    @Test
    public void testRemovePrefix1b() throws IOException, InterruptedException {
        addRemovePrefix();

        testRemovePrefix1(new UploadToFileService(serviceData));
    }

    private void testRemovePrefix2(UploadService uploadService) throws IOException, InterruptedException {
        File testFile = new File("workspace/release/build/test.txt");
        FilePath testFilePath = new FilePath(launcher.getChannel(), testFile.getAbsolutePath());
        Assert.assertEquals("virtual/test.txt", uploadService.getItemPath(testFilePath, "", uploadService.getServiceData()));
    }

    @Test
    public void testRemovePrefix2a() throws IOException, InterruptedException {
        addRemovePrefix();
        addVirtualPath();

        testRemovePrefix2(new UploadToBlobService(serviceData));
    }

    @Test
    public void testRemovePrefix2b() throws IOException, InterruptedException {
        addRemovePrefix();
        addVirtualPath();

        testRemovePrefix2(new UploadToFileService(serviceData));
    }

    private void testRemovePrefix3(UploadService uploadService) throws IOException, InterruptedException {
        File testFile = new File("workspace/release/build/test.txt");
        FilePath testFilePath = new FilePath(launcher.getChannel(), testFile.getAbsolutePath());
        Assert.assertEquals("virtual/embedded/test.txt", uploadService.getItemPath(testFilePath, "embedded/", uploadService.getServiceData()));
    }

    @Test
    public void testRemovePrefix3a() throws IOException, InterruptedException {
        addRemovePrefix();
        addVirtualPath();

        testRemovePrefix3(new UploadToBlobService(serviceData));
    }

    @Test
    public void testRemovePrefix3b() throws IOException, InterruptedException {
        addRemovePrefix();
        addVirtualPath();

        testRemovePrefix3(new UploadToFileService(serviceData));
    }
}
