package com.microsoftopentechnologies.windowsazurestorage;

import com.microsoft.azure.storage.blob.SharedAccessBlobPermissions;
import com.microsoft.azure.storage.file.SharedAccessFilePermissions;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureStorageAccount;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureUtils;
import com.microsoftopentechnologies.windowsazurestorage.helper.Constants;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.Api;
import hudson.model.Run;
import jenkins.model.Jenkins;
import jenkins.model.RunAction2;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.springframework.security.core.Authentication;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.EnumSet;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.net.URLDecoder;

@ExportedBean
public class AzureBlobAction implements RunAction2 {

    private transient Run<?, ?> build;
    private final String containerName;
    private final String fileShareName;
    private final String storageType;
    private final boolean allowAnonymousAccess;
    private final AzureBlob zipArchiveBlob;
    private final List<AzureBlob> individualBlobs;
    private final String storageCredentialId;

    public AzureBlobAction(
            String containerName,
            String shareName,
            String storageType,
            List<AzureBlob> individualBlobs,
            AzureBlob zipArchiveBlob,
            boolean allowAnonymousAccess,
            String storageCredentialId) {
        this.containerName = containerName;
        this.fileShareName = shareName;
        this.storageType = storageType;
        this.individualBlobs = individualBlobs;
        this.allowAnonymousAccess = allowAnonymousAccess;
        this.zipArchiveBlob = zipArchiveBlob;
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
    public void onAttached(Run<?, ?> r) {
        build = r;
    }

    public String getContainerName() {
        return containerName;
    }

    public String getFileShareName() {
        return fileShareName;
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        build = r;
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
        if (!allowAnonymousAccess && isAnonymousAccess(Jenkins.getAuthentication2())) {
            String url = request.getOriginalRequestURI();
            response.sendRedirect(request.getContextPath() + "/login?from=" + url);
            return;
        }

        String queryPath = request.getRestOfPath();

        if (queryPath == null) {
            return;
        }

        String blobName = queryPath.substring(1);

        // Check the archive blob if it is non-null
        if (zipArchiveBlob != null
                && blobName.equals(URLDecoder.decode(zipArchiveBlob.getBlobName(),
                StandardCharsets.UTF_8.toString()))) {
            StorageAccountInfo accountInfo = getStorageAccountInfo(storageCredentialId);

            if (accountInfo == null) {
                response.sendError(Constants.HTTP_INTERNAL_SERVER_ERROR,
                        "Azure Storage account global configuration is missing");
                return;
            }

            try {
                response.sendRedirect2(zipArchiveBlob.getBlobURL() + "?"
                        + generateReadSASURL(accountInfo, zipArchiveBlob.getBlobName()));
            } catch (Exception e) {
                response.sendError(Constants.HTTP_INTERNAL_SERVER_ERROR,
                        "Error occurred while downloading artifact " + e.getMessage());
            }
            return;
        }

        for (AzureBlob blob : individualBlobs) {
            if (blobName.equals(URLDecoder.decode(blob.getBlobName(), StandardCharsets.UTF_8.toString()))) {
                StorageAccountInfo accountInfo = getStorageAccountInfo(blob.getCredentialsId());

                if (accountInfo == null) {
                    response.sendError(Constants.HTTP_INTERNAL_SERVER_ERROR,
                            "Azure Storage account global configuration is missing");
                    return;
                }

                try {
                    response.sendRedirect2(blob.getBlobURL() + "?"
                            + generateReadSASURL(accountInfo, blob.getBlobName()));
                } catch (Exception e) {
                    response.sendError(Constants.HTTP_INTERNAL_SERVER_ERROR,
                            "Error occurred while downloading artifact " + e.getMessage());
                }
                return;
            }
        }

        response.sendError(Constants.HTTP_NOT_FOUND, "Azure artifact is not available");
    }

    @CheckForNull
    private StorageAccountInfo getStorageAccountInfo(String credentialsId) {
        AzureStorageAccount.StorageAccountCredential accountCredentials =
                AzureStorageAccount.getStorageAccountCredential(build.getParent(), credentialsId);

        if (accountCredentials == null) {
            return null;
        }

        return AzureStorageAccount.convertToStorageAccountInfo(accountCredentials);
    }

    private String generateReadSASURL(StorageAccountInfo storageAccountInfo, String fileName) throws Exception {
        if (getStorageType().equalsIgnoreCase(Constants.BLOB_STORAGE)) {
            return AzureUtils.generateBlobSASURL(storageAccountInfo, containerName, fileName,
                    EnumSet.of(SharedAccessBlobPermissions.READ));
        } else if (getStorageType().equalsIgnoreCase(Constants.FILE_STORAGE)) {
            return AzureUtils.generateFileSASURL(storageAccountInfo, fileShareName, fileName,
                    EnumSet.of(SharedAccessFilePermissions.READ));
        }
        throw new Exception("Unknown storage type. Please re-configure your job and build again.");
    }

    public boolean isAnonymousAccess(Authentication auth) {
        return auth != null && auth.getName() != null && "anonymous".equals(auth.getName());
    }

    public Api getApi() {
        return new Api(this);
    }

    public String getStorageType() {
        if (Constants.FILE_STORAGE.equals(storageType)) {
            return storageType;
        }
        return Constants.BLOB_STORAGE;
    }
}
