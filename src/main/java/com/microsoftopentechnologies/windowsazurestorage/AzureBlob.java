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

    public AzureBlob(String blobName, String blobURL, String md5, long byteSize, String storageType) {
        this.blobName = blobName;
        this.blobURL = blobURL;
        this.md5 = md5;
        this.byteSize = byteSize;
        this.storageType = storageType;
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
    public String getStorageType(){
        return storageType;
    }

    @Override
    public String toString() {
        return "AzureBlob [blobName=" + blobName + ", blobURL="
                + blobURL + "]";
    }
}
