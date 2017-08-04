package com.microsoftopentechnologies.windowsazurestorage;

import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureCredentials;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.helper.Constants;
import hudson.model.Api;
import hudson.model.Run;
import hudson.model.RunAction;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;

@ExportedBean
public class AzureBlobAction implements RunAction {

    private final Run build;
    private final String storageAccountName;
    private final String containerName;
    private final boolean allowAnonymousAccess;
    private final AzureBlob zipArchiveBlob;
    private final List<AzureBlob> individualBlobs;
    private final String storageCredentialId;

    public AzureBlobAction(
            Run build,
            String storageAccountName,
            String containerName,
            List<AzureBlob> individualBlobs,
            AzureBlob zipArchiveBlob,
            boolean allowAnonymousAccess,
            String storageCredentialId) {
        this.storageAccountName = storageAccountName;
        this.containerName = containerName;
        this.individualBlobs = individualBlobs;
        this.allowAnonymousAccess = allowAnonymousAccess;
        this.zipArchiveBlob = zipArchiveBlob;
        this.build = build;
        this.storageCredentialId = storageCredentialId;
    }

    public Run<?, ?> getBuild() {
        return build;
    }

    @Override
    public String getDisplayName() {
        return "Azure Artifacts";
    }

    @Override
    public String getIconFileName() {
        return "/plugin/windows-azure-storage/images/24x24/Azure.png";
    }

    @Override
    public String getUrlName() {
        return "Azure";
    }

    @Exported
    public AzureBlob getZipArchiveBlob() {
        return zipArchiveBlob;
    }

    @Override
    public void onAttached(Run arg0) {
    }

    @Override
    public void onBuildComplete() {
    }

    @Override
    public void onLoad() {
    }

    public String getStorageAccountName() {
        return storageAccountName;
    }

    public String getContainerName() {
        return containerName;
    }

    @Exported
    public List<AzureBlob> getIndividualBlobs() {
        return individualBlobs;
    }

    public boolean getAllowAnonymousAccess() {
        return allowAnonymousAccess;
    }

    public void doProcessDownloadRequest(
            StaplerRequest request,
            StaplerResponse response) throws IOException, ServletException {
        AzureCredentials.StorageAccountCredential accountCredentials =
                AzureCredentials.getStorageCreds(storageCredentialId, storageAccountName);

        if (accountCredentials == null) {
            response.sendError(Constants.HTTP_INTERNAL_SERVER_ERROR,
                    "Azure Storage account global configuration is missing");
            return;
        }

        StorageAccountInfo accountInfo = AzureCredentials.convertToStorageAccountInfo(accountCredentials);

        if (!allowAnonymousAccess && isAnonymousAccess(Jenkins.getAuthentication())) {
            String url = request.getOriginalRequestURI();
            response.sendRedirect("/login?from=" + url);
            return;
        }

        String queryPath = request.getRestOfPath();

        if (queryPath == null) {
            return;
        }

        String blobName = queryPath.substring(1);

        // Check the archive blob if it is non-null
        if (zipArchiveBlob != null && zipArchiveBlob.getBlobName().equals(blobName)) {
            try {
                response.sendRedirect2(zipArchiveBlob.getBlobURL() + "?"
                        + AzureUtils.generateSASURL(accountInfo, containerName, blobName));
            } catch (Exception e) {
                response.sendError(Constants.HTTP_INTERNAL_SERVER_ERROR,
                        "Error occurred while downloading artifact " + e.getMessage());
            }
            return;
        }

        for (AzureBlob blob : individualBlobs) {
            if (blob.getBlobName().equals(blobName)) {
                try {
                    response.sendRedirect2(blob.getBlobURL() + "?"
                            + AzureUtils.generateSASURL(accountInfo, containerName, blobName));
                } catch (Exception e) {
                    response.sendError(Constants.HTTP_INTERNAL_SERVER_ERROR,
                            "Error occurred while downloading artifact " + e.getMessage());
                }
                return;
            }
        }

        response.sendError(Constants.HTTP_NOT_FOUND, "Azure artifact is not available");
    }

    public boolean isAnonymousAccess(Authentication auth) {
        return auth != null && auth.getName() != null && "anonymous".equals(auth.getName());
    }

    public Api getApi() {
        return new Api(this);
    }
}
