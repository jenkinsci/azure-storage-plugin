package com.microsoftopentechnologies.windowsazurestorage;

import java.io.Serializable;

public class AzureBlob implements Serializable {

    private static final long serialVersionUID = -1873484056669542679L;
    
    private final String blobName;
    private final String blobURL;
    
    public AzureBlob(String blobName, String blobURL) {
        this.blobName = blobName;
        this.blobURL = blobURL;
    }

    public String getBlobName() {
        return blobName;
    }
    
    public String getBlobURL() {
        return blobURL;
    }

    @Override
    public String toString() {
        return "AzureBlob [blobName=" + blobName + ", blobURL="
                + blobURL + "]";
    }
    
    
}
