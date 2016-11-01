package com.microsoftopentechnologies.windowsazurestorage;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;

import org.acegisecurity.Authentication;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.model.AbstractBuild;
import hudson.model.RunAction;
import hudson.model.Run;

import com.microsoftopentechnologies.windowsazurestorage.WAStoragePublisher;
import com.microsoftopentechnologies.windowsazurestorage.WAStoragePublisher.WAStorageDescriptor;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureTelemetryHelper;

public class AzureBlobAction implements RunAction {
	private final AbstractBuild build;
	private final String storageAccountName;
	private final String containerName;
	private final boolean allowAnonymousAccess;
	private final AzureBlob zipArchiveBlob;
	private final List<AzureBlob> individualBlobs;

	public AzureBlobAction(AbstractBuild build, String storageAccountName, String containerName,
			List<AzureBlob> individualBlobs, AzureBlob zipArchiveBlob,
			boolean allowAnonymousAccess) {
		this.build = build;
		this.storageAccountName = storageAccountName;
		this.containerName = containerName;
		this.individualBlobs = individualBlobs;
		this.allowAnonymousAccess = allowAnonymousAccess;
		this.zipArchiveBlob = zipArchiveBlob;
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
	
	public AzureBlob getZipArchiveBlob() {
		return zipArchiveBlob;
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
	
	public List<AzureBlob> getIndividualBlobs() {
		return individualBlobs;
	}
	
	public boolean getAllowAnonymousAccess() {
		return allowAnonymousAccess;
	}
	
	private WAStoragePublisher.WAStorageDescriptor getWAStorageDescriptor() {
		WAStoragePublisher.WAStorageDescriptor desc = Jenkins.getInstance().getDescriptorByType(WAStoragePublisher.WAStorageDescriptor.class);
		return desc;
	}
	
	private String getSASURL(StorageAccountInfo accountInfo) throws Exception {
		try {
			return WAStorageClient.generateSASURL(accountInfo.getStorageAccName(), accountInfo.getStorageAccountKey(), 
					containerName, accountInfo.getBlobEndPointURL());
		} catch (Exception e) {
			//TODO: handle this in a better way
			e.printStackTrace();
			return "";
		}
	}
	
	public void doProcessDownloadRequest(final StaplerRequest request, final StaplerResponse response) throws IOException, ServletException {
		WAStorageDescriptor storageDesc = getWAStorageDescriptor();
		StorageAccountInfo accountInfo  = storageDesc.getStorageAccount(storageAccountName);
                AzureTelemetryHelper telemetry = new AzureTelemetryHelper();
                String telemetryEvent = "DownloadingRequest";
		
		if (accountInfo == null) {
			response.sendError(500, "Azure Storage account global configuration is missing");
                        telemetry.createErrorEvent(telemetryEvent, "Azure Storage account global configuration is missing" );
			return;
		}
		
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
		if (zipArchiveBlob != null) {
			if (zipArchiveBlob.getBlobName().equals(blobName)) {
				try {
                                        telemetry.createEvent(telemetryEvent, accountInfo.getStorageAccName()+" downloaded archive "+zipArchiveBlob.getBlobName());
					response.sendRedirect2(zipArchiveBlob.getBlobURL()+"?"+getSASURL(accountInfo));
				} catch(Exception e) {
					response.sendError(500, "Error occurred while downloading artifact "+e.getMessage());
                                        telemetry.createErrorEvent(telemetryEvent, "Error occurred while downloading artifact "+e.getMessage());
				}
				return;
			}
		}
		
		for (AzureBlob blob : individualBlobs) {
			if (blob.getBlobName().equals(blobName)) {
				try {
                                    telemetry.createEvent(telemetryEvent, accountInfo.getStorageAccName()+" downloaded blob "+ blob.getBlobName());
                                    response.sendRedirect2(blob.getBlobURL()+"?"+getSASURL(accountInfo));
				} catch(Exception e) {
                                    telemetry.createErrorEvent(telemetryEvent, "Error occurred while downloading artifact "+e.getMessage());
				    response.sendError(500, "Error occurred while downloading artifact "+e.getMessage());
				}
				return;
			}
		}
		
		
                telemetry.createErrorEvent(telemetryEvent, "Azure artifact is not available");
                response.sendError(404, "Azure artifact is not available");
	}
	
	public boolean isAnonymousAccess(Authentication auth) {
		if (auth != null && auth.getName() != null && "anonymous".equals(auth.getName())) {
			return true;
		}
		return false;
	}
}
