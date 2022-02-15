package com.microsoftopentechnologies.windowsazurestorage;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.Serializable;

@ExportedBean
public class AzureBlob implements Serializable {

    private static final long serialVersionUID = -1873484056669542679L;

    private final String containerOrFileShare;
    private final String blobName;
    private final String blobURL;
    private final long byteSize;
    private final String storageType;
    private final String credentialsId;

    public AzureBlob(
            String blobName,
            String blobURL,
            long byteSize,
            String storageType,
            String credentialsId,
            String containerOrFileShare
    ) {
        this.blobName = blobName;
        this.blobURL = blobURL;
        this.byteSize = byteSize;
        this.storageType = storageType;
        this.credentialsId = credentialsId;
        this.containerOrFileShare = containerOrFileShare;
    }

    @Exported
    public String getCredentialsId() {
        return credentialsId;
    }

    public String getBlobName() {
        return blobName;
    }

    @Exported
    public String getBlobURL() {
        return blobURL;
    }

    @Exported
    public long getSizeInBytes() {
        return byteSize;
    }

    @Exported
    public String getStorageType() {
        return storageType;
    }

    public String getContainerOrFileShare() {
        return containerOrFileShare;
    }

    @Override
    public String toString() {
        return "AzureBlob [blobName=" + blobName + ", blobURL="
                + blobURL + "]";
    }
}
