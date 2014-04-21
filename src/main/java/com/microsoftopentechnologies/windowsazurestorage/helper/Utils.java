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
package com.microsoftopentechnologies.windowsazurestorage.helper;

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

public class Utils {
	/* Regular expression for valid container name */
	public static final String VAL_CNT_NAME = "^(([a-z\\d]((-(?=[a-z\\d]))|([a-z\\d])){2,62}))$";

	/* Regular expression to match tokens in the format of $TOKEN or ${TOKEN} */
	public static final String TOKEN_FORMAT = "\\$([A-Za-z0-9_]+|\\{[A-Za-z0-9_]+\\})";

	public static final String DEF_BLOB_URL = "http://blob.core.windows.net/";
	public static final String FWD_SLASH = "/";
	public static final String HTTP_PRT = "http://";
	// http Protocol separator
	public static final String PRT_SEP = "://";

	/**
	 * Checks for validity of container name after converting the input into
	 * lowercase. Rules for container name 1.Container names must start with a
	 * letter or number, and can contain only letters, numbers, and the dash (-)
	 * character. 2.Every dash (-) character must be immediately preceded and
	 * followed by a letter or number; consecutive dashes are not permitted in
	 * container names. 3.All letters in a container name must be lowercase.
	 * 4.Container names must be from 3 through 63 characters long.
	 * 
	 * @param containerName
	 *            Name of the Windows Azure storage container
	 * @return true if conatiner name is valid else returns false
	 */
	public static boolean validateContainerName(final String containerName) {
		boolean isValid = false;

		if (containerName != null) {
			String lcContainerName = containerName.trim().toLowerCase(
					Locale.ENGLISH);
			if (lcContainerName.matches(VAL_CNT_NAME)) {
				isValid = true;
			}
		}
		return isValid;
	}

	public static boolean validateBlobName(final String blobName) {
		boolean isValid = false;

		if (blobName != null
				&& (blobName.length() > 0 && blobName.length() <= 1024)) {
			isValid = true;
		}
		return isValid;
	}

	/**
	 * Utility method to check for null conditions or empty strings.
	 * 
	 * @param name
	 * @return true if null or empty string
	 */
	public static boolean isNullOrEmpty(final String name) {
		boolean isValid = false;
		if (name == null || name.matches("\\s*")) {
			isValid = true;
		}
		return isValid;
	}

	/**
	 * Utility method to replace system environment variables in text
	 * 
	 * @param build
	 * @param listener
	 * @param text
	 * @return
	 * @throws InterruptedException
	 * @throws IOException
	 */

	public static String replaceTokens(AbstractBuild<?, ?> build,
			BuildListener listener, String text) throws IOException,
			InterruptedException {
		String newText = null;
		if (!isNullOrEmpty(text)) {
			Map<String, String> envVars = build.getEnvironment(listener);
			newText = Util.replaceMacro(text, envVars);
		}
		return newText;
	}

	/**
	 * This method find tokens in the form of $TOKEN or ${TOKEN} and removes
	 * them
	 * 
	 * @param text
	 *            may contains tokens that are to be replaced
	 * @return String replaced text
	 */
	public static String removeTokens(String text) {
		if (isNullOrEmpty(text)) {
			return null;
		}
		return text.replaceAll(TOKEN_FORMAT, "");
	}

	/**
	 * This method checks if text contains tokens in the form of $TOKEN or
	 * ${TOKEN}
	 * 
	 * @param text
	 * @return true if tokens exist in input string
	 */
	public static boolean containTokens(String text) {
		if (isNullOrEmpty(text)) {
			return false;
		}
		return text.matches(TOKEN_FORMAT);
	}

	/**
	 * Returns formatted blob end point in case if a non-default one is
	 * specified.
	 * 
	 * @param storageAccName
	 * @param blobURL
	 * @return DEF_BLOB_URL if blobURL is empty or blobURL is default one else
	 *         returns formatted blob url.
	 */
	public static String getBlobEP(String storageAccName, String blobURL) {

		if (isNullOrEmpty(blobURL)) {
			return DEF_BLOB_URL;
		}

		// Append forward slash
		if (!blobURL.endsWith(FWD_SLASH)) {
			blobURL = blobURL.concat(FWD_SLASH);
		}

		if (blobURL.equals(DEF_BLOB_URL)) {
			return DEF_BLOB_URL;
		} else {
			if (blobURL.indexOf(PRT_SEP) != -1) { // insert storage account name
				blobURL = blobURL.replace(PRT_SEP, PRT_SEP + storageAccName
						+ ".");
			} else { // insert http and storage account name
				blobURL = new StringBuilder().append(HTTP_PRT)
						.append(storageAccName).append(".").append(blobURL)
						.toString();
			}
		}
		return blobURL;
	}

	/**
	 * Returns default blob url
	 * 
	 * @return
	 */
	public static String getDefaultBlobURL() {
		return DEF_BLOB_URL;
	}

}
