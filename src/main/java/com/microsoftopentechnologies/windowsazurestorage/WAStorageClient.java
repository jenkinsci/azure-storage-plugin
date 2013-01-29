/*
 Copyright 2013 Microsoft Open Technologies, Inc.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package com.microsoftopentechnologies.windowsazurestorage;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.StringTokenizer;

import hudson.FilePath;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;

import com.microsoft.windowsazure.services.blob.client.BlobContainerPermissions;
import com.microsoft.windowsazure.services.blob.client.BlobContainerPublicAccessType;
import com.microsoft.windowsazure.services.blob.client.CloudBlobClient;
import com.microsoft.windowsazure.services.blob.client.CloudBlobContainer;
import com.microsoft.windowsazure.services.blob.client.CloudBlockBlob;
import com.microsoft.windowsazure.services.core.storage.CloudStorageAccount;
import com.microsoft.windowsazure.services.core.storage.RetryNoRetry;
import com.microsoft.windowsazure.services.core.storage.StorageCredentialsAccountAndKey;
import com.microsoft.windowsazure.services.core.storage.StorageException;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.exceptions.WAStorageException;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;

public class WAStorageClient {

	/* A random name for container name to test validity of storage account details*/
	private static final String TEST_CNT_NAME = "testcheckfromjenkins";
	
	private static String fpSeparator = ";" ; 
	
	/**
	 * This method validates Storage Account credentials by checking for a dummy conatiner existence.
	 * @param storageAccountName
	 * @param storageAccountKey
	 * @param blobEndPointURL
	 * @return true if valid
	 * @throws WAStorageException
	 */
	public static boolean validateStorageAccount(final String storageAccountName, final String storageAccountKey, 
			                                     final String blobEndPointURL) throws WAStorageException {
		try {
			// Get container reference
			CloudBlobContainer  container = getBlobContainerReference(storageAccountName,storageAccountKey, 
											blobEndPointURL,TEST_CNT_NAME,false,false,null);   			
            container.exists(); // No need to capture result , all we need is to check validity of account details. 
		}  catch (Exception e) {
			e.printStackTrace();
			throw new WAStorageException(Messages.Client_SA_val_fail());
		}
		return true;
	}


	/**
	 * Returns reference of Windows Azure cloud blob container.
	 * @param accName 		storage account name
	 * @param key     		storage account primary access key
	 * @param blobURL 		blob service endpoint url
	 * @param containerName name of the container
	 * @param createCnt 	Indicates if container needs to be created
	 * @param allowRetry 	sets retry policy
	 * @param cntPubAccess 	Permissions for container
	 * @return              reference of CloudBlobContainer   
	 * @throws URISyntaxException
	 * @throws StorageException
	 */
	public static CloudBlobContainer getBlobContainerReference(String accName, String key, String blobURL, String containerName, 
															   boolean createCnt, boolean allowRetry, Boolean cntPubAccess) 
	throws URISyntaxException, StorageException {

		CloudStorageAccount cloudStorageAccount;
        CloudBlobClient 	serviceClient;
        CloudBlobContainer  container;
        StorageCredentialsAccountAndKey credentials;

        credentials = new StorageCredentialsAccountAndKey(accName,key);

        if (Utils.isNullOrEmpty(blobURL)|| blobURL.equals(Utils.DEF_BLOB_URL) ) { //no need to check for null, just taking extra care
        	cloudStorageAccount = new CloudStorageAccount(credentials);
        } else {
        	cloudStorageAccount = new CloudStorageAccount(credentials,new URI(blobURL),null,null);
        }

        serviceClient = cloudStorageAccount.createCloudBlobClient();
        if (!allowRetry) {
            // Setting no retry policy
            RetryNoRetry  rnr = new RetryNoRetry();
            serviceClient.setRetryPolicyFactory(rnr);
        }

        container 	  = serviceClient.getContainerReference(containerName);
        
        if (createCnt) {
        	container.createIfNotExist();
        }
        
        if (cntPubAccess != null) {
        	// Set access permissions on container.
            BlobContainerPermissions cntPerm;
            cntPerm = new BlobContainerPermissions();
            if (cntPubAccess) {
            	cntPerm.setPublicAccess(BlobContainerPublicAccessType.CONTAINER);
            } else {
            	cntPerm.setPublicAccess(BlobContainerPublicAccessType.OFF);
            }
            container.uploadPermissions(cntPerm);
        }
        
        return container;
	}	
	
	/**
	 * Uploads files to Windows Azure Storage.
	 * @param listener 
	 * @param build 
	 * @param StorageAccountInfo storage account information.
	 * @param expContainerName   container name. 
	 * @param cntPubAccess       denotes if container is publicly accessible.
	 * @param expFP       		 File Path in ant glob syntax relative to CI tool workspace. 
	 * @param expVP       		 Virtual Path of blob container.
	 * @return filesUploaded	 number of files that are uploaded.
	 * @throws WAStorageException 
	 * @throws Exception
	 */
	public static int upload(AbstractBuild<?, ?> build, BuildListener listener, StorageAccountInfo strAcc, 
							 String expContainerName, boolean cntPubAccess, String expFP, String expVP) 
	throws WAStorageException {

		CloudBlockBlob	blob			= null ;
		int 			filesUploaded	= 0; //Counter to track no. of files that are uploaded

		try {
			FilePath  		wsPath		= build.getWorkspace();
    		StringTokenizer strTokens	= new StringTokenizer(expFP, fpSeparator);
    		FilePath[]      paths 		= null;
    		
    		listener.getLogger().println(Messages.WAStoragePublisher_uploading());
    		
    		CloudBlobContainer 	container = WAStorageClient.getBlobContainerReference(strAcc.getStorageAccName(),strAcc.getStorageAccountKey(),
    				                        										  strAcc.getBlobEndPointURL(),expContainerName,true, 
    				                        										  true,cntPubAccess);
    		
    		while (strTokens.hasMoreElements()) {
    			paths 		  = wsPath.list(strTokens.nextToken());
    			if	(paths.length != 0) {
    				for (FilePath src : paths) {
    	            	 if(Utils.isNullOrEmpty(expVP)) {
    	            		 blob = container.getBlockBlobReference(src.getName());
    	            	 } else {
    	            		 blob = container.getBlockBlobReference(expVP+src.getName());
    	            	 }
    	                 blob.upload(src.read(), src.length());
    	                 filesUploaded++;
    	            } 
    			}
    		}
	    } catch (Exception e) {
        	e.printStackTrace();
        	throw new WAStorageException(e.getMessage(),e.getCause());
        }
		return filesUploaded;
	}
		
}
