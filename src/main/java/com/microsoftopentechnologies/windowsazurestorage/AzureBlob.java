package com.microsoftopentechnologies.windowsazurestorage;

import hudson.model.Api;
import java.io.Serializable;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class AzureBlob implements Serializable {

	private static final long serialVersionUID = -1873484056669542679L;
	
	private final String blobName;
	private final String blobURL;
	private final String md5;
	private final long byteSize;
	
	public AzureBlob(String blobName, String blobURL, String md5, long byteSize) {
		this.blobName = blobName;
		this.blobURL = blobURL;
		this.md5 = md5;
		this.byteSize = byteSize;
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

	@Override
	public String toString() {
		return "AzureBlob [blobName=" + blobName + ", blobURL="
				+ blobURL + "]";
	}
}
