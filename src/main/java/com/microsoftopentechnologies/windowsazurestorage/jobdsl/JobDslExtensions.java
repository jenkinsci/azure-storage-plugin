package com.microsoftopentechnologies.windowsazurestorage.jobdsl;

import com.microsoftopentechnologies.windowsazurestorage.AzureStorageBuilder;
import com.microsoftopentechnologies.windowsazurestorage.WAStoragePublisher;
import hudson.Extension;
import javaposse.jobdsl.dsl.helpers.publisher.PublisherContext;
import javaposse.jobdsl.dsl.helpers.step.StepContext;
import javaposse.jobdsl.plugin.ContextExtensionPoint;
import javaposse.jobdsl.plugin.DslExtensionMethod;

/**
 *
 * @author crummel
 */
@Extension(optional = true)
public class JobDslExtensions extends ContextExtensionPoint {
    @DslExtensionMethod(context = StepContext.class)
    public Object azureStorageDownload(Runnable closure) {
        AzureStorageDownloadContext context = new AzureStorageDownloadContext();
        executeInContext(closure, context);
        guardNullValue("azureStorageDownload", "storageAccountName", context.storageAccountName);
        return new AzureStorageBuilder(context.storageAccountName, context.containerName, context.buildSelector,
			context.includeFilesPattern, context.excludeFilesPattern, context.downloadDirectoryLocation, context.flattenDirectories,
			context.includeArchiveZips, context.projectName);
    }
    
    @DslExtensionMethod(context = PublisherContext.class)
    public Object azureStorageUpload(Runnable closure) {
        AzureStorageUploadContext context = new AzureStorageUploadContext();
        executeInContext(closure, context);
        guardNullValue("azureStorageUpload", "storageAccountName", context.storageAccountName);
        guardNullValue("azureStorageUpload", "filesToUpload", context.filesToUpload);
        return new WAStoragePublisher(context.storageAccountName,
			context.filesToUpload, context.excludeFilesPattern, context.containerName,
			context.makeContainerPublic, context.virtualPath,
			context.cleanUpContainer, context.allowAnonymousAccess,
			context.uploadArtifactsOnlyIfSuccessful,
			context.doNotFailIfArchivingReturnsNothing,
			context.doNotUploadIndividualFiles,
			context.uploadZips,
			context.manageArtifacts);
    }
    
    private void guardNullValue(String context, String name, Object value) {
        if (value == null || (value instanceof String && "".equals(value))) {
            throw new IllegalArgumentException(context + ": " + name + " is required.");
        }
    }
}
