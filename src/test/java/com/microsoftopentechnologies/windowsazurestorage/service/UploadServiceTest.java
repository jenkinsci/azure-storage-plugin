package com.microsoftopentechnologies.windowsazurestorage.service;

import com.microsoftopentechnologies.windowsazurestorage.service.model.UploadServiceData;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class UploadServiceTest {

    private Run run;
    private Launcher launcher;
    private FilePath workspace;
    private UploadServiceData serviceData;

    @BeforeEach
    void setup() {
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

    // remove prefix    0
    // embedded VP      0
    // virtual path     0
    private void testThatExistingBehaviorRemainsUnchanged_1(UploadService uploadService) throws IOException, InterruptedException {
        File testFile = new File("workspace/release/build/test.txt");
        FilePath testFilePath = new FilePath(launcher.getChannel(), testFile.getAbsolutePath());
        assertEquals("release/build/test.txt", uploadService.getItemPath(testFilePath, "", uploadService.getServiceData()));
    }

    @Test
    void testThatExistingBehaviorRemainsUnchanged_a_blob() throws IOException, InterruptedException {
        testThatExistingBehaviorRemainsUnchanged_1(new UploadToBlobService(serviceData));
    }

    @Test
    void testThatExistingBehaviorRemainsUnchanged_b_file() throws IOException, InterruptedException {
        testThatExistingBehaviorRemainsUnchanged_1(new UploadToFileService(serviceData));
    }

    // remove prefix    0
    // embedded VP      0
    // virtual path     1
    private void testThatExistingBehaviorRemainsUnchanged_2(UploadService uploadService) throws IOException, InterruptedException {
        File testFile = new File("workspace/release/build/test.txt");
        FilePath testFilePath = new FilePath(launcher.getChannel(), testFile.getAbsolutePath());
        assertEquals("virtual/release/build/test.txt", uploadService.getItemPath(testFilePath, "", uploadService.getServiceData()));
    }

    @Test
    void testThatExistingBehaviorRemainsUnchanged_c_blob() throws IOException, InterruptedException {
        addVirtualPath();

        testThatExistingBehaviorRemainsUnchanged_2(new UploadToBlobService(serviceData));
    }

    @Test
    void testThatExistingBehaviorRemainsUnchanged_d_file() throws IOException, InterruptedException {
        addVirtualPath();

        testThatExistingBehaviorRemainsUnchanged_2(new UploadToFileService(serviceData));
    }

    // remove prefix    0
    // embedded VP      1
    // virtual path     0
    private void testThatExistingBehaviorRemainsUnchanged_4(UploadService uploadService) throws IOException, InterruptedException {
        File testFile = new File("workspace/release/build/test.txt");
        FilePath testFilePath = new FilePath(launcher.getChannel(), testFile.getAbsolutePath());
        assertEquals("embedded/release/build/test.txt", uploadService.getItemPath(testFilePath, "embedded/", uploadService.getServiceData()));
    }

    @Test
    void testThatExistingBehaviorRemainsUnchanged_g_blob() throws IOException, InterruptedException {
        testThatExistingBehaviorRemainsUnchanged_4(new UploadToBlobService(serviceData));
    }

    @Test
    void testThatExistingBehaviorRemainsUnchanged_h_blob() throws IOException, InterruptedException {
        testThatExistingBehaviorRemainsUnchanged_4(new UploadToFileService(serviceData));
    }

    // remove prefix    0
    // embedded VP      1
    // virtual path     1
    private void testThatExistingBehaviorRemainsUnchanged_3(UploadService uploadService) throws IOException, InterruptedException {
        File testFile = new File("workspace/release/build/test.txt");
        FilePath testFilePath = new FilePath(launcher.getChannel(), testFile.getAbsolutePath());
        assertEquals("virtual/embedded/release/build/test.txt", uploadService.getItemPath(testFilePath, "embedded/", uploadService.getServiceData()));
    }

    @Test
    void testThatExistingBehaviorRemainsUnchanged_e_blob() throws IOException, InterruptedException {
        addVirtualPath();

        testThatExistingBehaviorRemainsUnchanged_3(new UploadToBlobService(serviceData));
    }

    @Test
    void testThatExistingBehaviorRemainsUnchanged_f_blob() throws IOException, InterruptedException {
        addVirtualPath();

        testThatExistingBehaviorRemainsUnchanged_3(new UploadToFileService(serviceData));
    }

    // remove prefix    1
    // embedded VP      0
    // virtual path     0
    private void testRemovePrefixNoEmbeddedVPNoVirtualPath(UploadService uploadService) throws IOException, InterruptedException {
        File testFile = new File("workspace/release/build/test.txt");
        FilePath testFilePath = new FilePath(launcher.getChannel(), testFile.getAbsolutePath());
        assertEquals("test.txt", uploadService.getItemPath(testFilePath, "", uploadService.getServiceData()));
    }

    @Test
    void testRemovePrefixNoEmbeddedVPNoVirtualPath_blob() throws IOException, InterruptedException {
        addRemovePrefix();

        testRemovePrefixNoEmbeddedVPNoVirtualPath(new UploadToBlobService(serviceData));
    }

    @Test
    void testRemovePrefixNoEmbeddedVPNoVirtualPath_file() throws IOException, InterruptedException {
        addRemovePrefix();

        testRemovePrefixNoEmbeddedVPNoVirtualPath(new UploadToFileService(serviceData));
    }

    // remove prefix    1
    // embedded VP      0
    // virtual path     1
    private void testRemovePrefixWithVirtualPath(UploadService uploadService) throws IOException, InterruptedException {
        File testFile = new File("workspace/release/build/test.txt");
        FilePath testFilePath = new FilePath(launcher.getChannel(), testFile.getAbsolutePath());
        assertEquals("virtual/test.txt", uploadService.getItemPath(testFilePath, "", uploadService.getServiceData()));
    }

    @Test
    void testRemovePrefixWithVirtualPath_blob() throws IOException, InterruptedException {
        addRemovePrefix();
        addVirtualPath();

        testRemovePrefixWithVirtualPath(new UploadToBlobService(serviceData));
    }

    @Test
    void testRemovePrefixWithVirtualPath_file() throws IOException, InterruptedException {
        addRemovePrefix();
        addVirtualPath();

        testRemovePrefixWithVirtualPath(new UploadToFileService(serviceData));
    }

    // remove prefix    1
    // embedded VP      1
    // virtual path     0
    private void testRemovePrefixWithEmbeddedVP(UploadService uploadService) throws IOException, InterruptedException {
        File testFile = new File("workspace/release/build/test.txt");
        FilePath testFilePath = new FilePath(launcher.getChannel(), testFile.getAbsolutePath());
        assertEquals("embedded/test.txt", uploadService.getItemPath(testFilePath, "embedded/", uploadService.getServiceData()));
    }

    @Test
    void testRemovePrefixWithEmbeddedVP_blob() throws IOException, InterruptedException {
        addRemovePrefix();

        testRemovePrefixWithEmbeddedVP(new UploadToBlobService(serviceData));
    }

    @Test
    void testRemovePrefixWithEmbeddedVP_file() throws IOException, InterruptedException {
        addRemovePrefix();

        testRemovePrefixWithEmbeddedVP(new UploadToFileService(serviceData));
    }

    // remove prefix    1
    // embedded VP      1
    // virtual path     1
    private void testRemovePrefixWithEmbeddedVPAndVirtualPath(UploadService uploadService) throws IOException, InterruptedException {
        File testFile = new File("workspace/release/build/test.txt");
        FilePath testFilePath = new FilePath(launcher.getChannel(), testFile.getAbsolutePath());
        assertEquals("virtual/embedded/test.txt", uploadService.getItemPath(testFilePath, "embedded/", uploadService.getServiceData()));
    }

    @Test
    void testRemovePrefixWithEmbeddedVPAndVirtualPath_blob() throws IOException, InterruptedException {
        addRemovePrefix();
        addVirtualPath();

        testRemovePrefixWithEmbeddedVPAndVirtualPath(new UploadToBlobService(serviceData));
    }

    @Test
    void testRemovePrefixWithEmbeddedVPAndVirtualPath_file() throws IOException, InterruptedException {
        addRemovePrefix();
        addVirtualPath();

        testRemovePrefixWithEmbeddedVPAndVirtualPath(new UploadToFileService(serviceData));
    }
}
