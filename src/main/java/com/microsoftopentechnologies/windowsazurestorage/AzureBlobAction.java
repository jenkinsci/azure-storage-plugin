package com.microsoftopentechnologies.windowsazurestorage;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.model.AbstractBuild;
import hudson.model.RunAction;
import hudson.model.Run;

import com.microsoftopentechnologies.windowsazurestorage.WAStoragePublisher;
import com.microsoftopentechnologies.windowsazurestorage.WAStoragePublisher.WAStorageDescriptor;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;


public class AzureBlobAction implements RunAction {
	private final AbstractBuild build;
	private final String storageAccountName;
	private final String containerName;
	private final List<AzureBlob> blobs;

	public AzureBlobAction(AbstractBuild build, String storageAccountName, String containerName,  List<AzureBlob> blobs) {
		this.build = build;
		this.storageAccountName = storageAccountName;
		this.containerName = containerName;
		this.blobs = blobs;
	}
	
	public String getDisplayName() {
		return "Azure Artifacts";
	}

	public String getIconFileName() {
		return "/plugin/windows-azure-storage/images/24x24/Azure.png";
	}

	public String getUrlName() {
		return "Azure";
	}

	public void onAttached(Run arg0) {
	}

	public void onBuildComplete() {
	}

	public void onLoad() {
	}
	
	public AbstractBuild<?,?> getBuild() {
	      return build;
	}
	
	public String getStorageAccountName() {
		return storageAccountName;
	}
	
	public String getContainerName() {
		return containerName;
	}
	
	public List<AzureBlob> getBlobs() {
		return blobs;
	}
	
	private WAStoragePublisher.WAStorageDescriptor getWAStorageDescriptor() {
		WAStoragePublisher.WAStorageDescriptor desc = Jenkins.getInstance().getDescriptorByType(WAStoragePublisher.WAStorageDescriptor.class);
		return desc;
	}
	
	private String getSASURL() throws Exception{
		try {
			WAStorageDescriptor storageDesc = getWAStorageDescriptor();
			StorageAccountInfo accountInfo =storageDesc.getStorageAccount(storageAccountName);
			
			if (accountInfo == null) {
				return "";
			}
			
			return WAStorageClient.generateSASURL(accountInfo.getStorageAccName(), accountInfo.getStorageAccountKey(), 
					containerName, accountInfo.getBlobEndPointURL());
		} catch (Exception e) {
			//TODO: handle this in a better way
			e.printStackTrace();
			return "";
		}
	}
	
	public void doProcessDownloadRequest(final StaplerRequest request, final StaplerResponse response) throws IOException, ServletException {
		String queryPath = request.getRestOfPath();
	    
		if (queryPath == null) {
	          return;
	    }
		
		String blobName = queryPath.substring(1);
		
		for (AzureBlob blob : blobs) {
			if (blob.getBlobName().equals(blobName)) {
				try {
					response.sendRedirect2(blob.getBlobURL()+"?"+getSASURL());
				} catch(Exception e) {
					response.sendError(500, "Error occurred while downlaoding artifact "+e.getMessage());
				}
				return;
			}
		}

		response.sendError(404, "Azure artifact is not available");
	}
}
