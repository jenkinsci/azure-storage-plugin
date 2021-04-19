package com.microsoftopentechnologies.windowsazurestorage;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.Serializable;

@ExportedBean
public class AzureBlob implements Serializable {

    private static final long serialVersionUID = -1873484056669542679L;

    private final String blobName;
    private final String blobURL;
    private final String md5;
    private final long byteSize;
    private final String storageType;
    private final String credentialsId;

    @Deprecated
    public AzureBlob(
            String blobName,
            String blobURL,
            String md5,
            long byteSize,
            String storageType) {
        this(blobName, blobURL, md5, byteSize, storageType, null);
    }

    public AzureBlob(
            String blobName,
            String blobURL,
            String md5,
            long byteSize,
            String storageType,
            String credentialsId) {
        this.blobName = blobName;
        this.blobURL = blobURL;
        this.md5 = md5;
        this.byteSize = byteSize;
        this.storageType = storageType;
        this.credentialsId = credentialsId;
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
    public String getMd5() {
        return md5;
    }

    @Exported
    public long getSizeInBytes() {
        return byteSize;
    }

    @Exported
    public String getStorageType() {
        return storageType;
    }

    @Override
    public String toString() {
        return "AzureBlob [blobName=" + blobName + ", blobURL="
                + blobURL + "]";
    }
}
