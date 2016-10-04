package com.microsoftopentechnologies.windowsazurestorage;

import com.microsoftopentechnologies.windowsazurestorage.WAStoragePublisher.WAStorageDescriptor;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import hudson.model.Api;
import hudson.model.Run;
import hudson.model.RunAction;
import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class AzureBlobAction implements RunAction {
	private final Run build;
	private final String storageAccountName;
	private final String containerName;
	private final boolean allowAnonymousAccess;
	private final AzureBlob zipArchiveBlob;
	private final List<AzureBlob> individualBlobs;

	public AzureBlobAction(Run build, String storageAccountName, String containerName,
			List<AzureBlob> individualBlobs, AzureBlob zipArchiveBlob,
			boolean allowAnonymousAccess) {
		this.storageAccountName = storageAccountName;
		this.containerName = containerName;
		this.individualBlobs = individualBlobs;
		this.allowAnonymousAccess = allowAnonymousAccess;
		this.zipArchiveBlob = zipArchiveBlob;
		this.build = build;
	}

	public Run<?,?> getBuild() {
		return build;
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
	
	@Exported
	public AzureBlob getZipArchiveBlob() {
		return zipArchiveBlob;
	}

	public void onAttached(Run arg0) {
	}

	public void onBuildComplete() {
	}

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
	
	private WAStoragePublisher.WAStorageDescriptor getWAStorageDescriptor() {
		WAStoragePublisher.WAStorageDescriptor desc = Jenkins.getInstance().getDescriptorByType(WAStoragePublisher.WAStorageDescriptor.class);
		return desc;
	}
	
	private String getSASURL(StorageAccountInfo accountInfo, String blobName) throws Exception {
		try {
			return WAStorageClient.generateSASURL(accountInfo.getStorageAccName(), accountInfo.getStorageAccountKey(), 
					containerName, blobName, accountInfo.getBlobEndPointURL());
		} catch (Exception e) {
			//TODO: handle this in a better way
			e.printStackTrace();
			return "";
		}
	}
	
	public void doProcessDownloadRequest(final StaplerRequest request, final StaplerResponse response) throws IOException, ServletException {
		WAStorageDescriptor storageDesc = getWAStorageDescriptor();
		StorageAccountInfo accountInfo  = storageDesc.getStorageAccount(storageAccountName);
		
		if (accountInfo == null) {
			response.sendError(500, "Azure Storage account global configuration is missing");
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
					response.sendRedirect2(zipArchiveBlob.getBlobURL()+"?"+getSASURL(accountInfo, blobName));
				} catch(Exception e) {
					response.sendError(500, "Error occurred while downloading artifact "+e.getMessage());
				}
				return;
			}
		}
		
		for (AzureBlob blob : individualBlobs) {
			if (blob.getBlobName().equals(blobName)) {
				try {
					response.sendRedirect2(blob.getBlobURL()+"?"+getSASURL(accountInfo, blobName));
				} catch(Exception e) {
					response.sendError(500, "Error occurred while downloading artifact "+e.getMessage());
				}
				return;
			}
		}
		
		response.sendError(404, "Azure artifact is not available");
	}
	
	public boolean isAnonymousAccess(Authentication auth) {
		if (auth != null && auth.getName() != null && "anonymous".equals(auth.getName())) {
			return true;
		}
		return false;
	}

	public Api getApi() {
		return new Api(this);
	}
}
