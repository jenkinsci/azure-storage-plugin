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

import java.util.Locale;
import jenkins.model.Jenkins;

public class Utils {

    /**
     * Checks for validity of container name after converting the input into
     * lowercase. Rules for container name 1.Container names must start with a
     * letter or number, and can contain only letters, numbers, and the dash (-)
     * character. 2.Every dash (-) character must be immediately preceded and
     * followed by a letter or number; consecutive dashes are not permitted in
     * container names. 3.All letters in a container name must be lowercase.
     * 4.Container names must be from 3 through 63 characters long.
     *
     * @param containerName Name of the Windows Azure storage container
     * @return true if container name is valid else returns false
     */
    public static boolean validateContainerName(final String containerName) {
        if (containerName != null) {
            String lcContainerName = containerName.trim().toLowerCase(
                    Locale.ENGLISH);
            if (lcContainerName.matches(Constants.VAL_CNT_NAME)) {
                return true;
            }
        }
        return false;
    }

    public static boolean validateBlobName(final String blobName) {
        return blobName != null && (blobName.length() > 0 && blobName.length() <= 1024);
    }

    /**
     * Utility method to check for null conditions or empty strings.
     *
     * @param name
     * @return true if null or empty string
     */
    public static boolean isNullOrEmpty(final String name) {
        return name == null || name.matches("\\s*");
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
        return text.matches(Constants.TOKEN_FORMAT);
    }

    /**
     * Returns formatted blob end point in case if a non-default one is
     * specified.
     *
     * @param blobURL
     * @return DEF_BLOB_URL if blobURL is empty or blobURL is default one else
     * returns formatted blob url.
     */
    public static String getBlobEP(String blobURL) {

        if (isNullOrEmpty(blobURL)) {
            return Constants.DEF_BLOB_URL;
        }

        // Append forward slash
        if (!blobURL.endsWith(Constants.FWD_SLASH)) {
            blobURL = blobURL.concat(Constants.FWD_SLASH);
        }

        // prepend http protocol if missing
        if (!blobURL.contains(Constants.PRT_SEP)) {
            blobURL = new StringBuilder()
                    .append(Constants.HTTP_PRT)
                    .append(blobURL)
                    .toString();
        }

        return blobURL;
    }

    /**
     * Returns default blob url
     *
     * @return
     */
    public static String getDefaultBlobURL() {
        return Constants.DEF_BLOB_URL;
    }

    /**
     * Returns md5 hash in string format for a given string
     *
     * @return
     */
    public static String getMD5(String plainText) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance(Constants.HASH_TYPE);
            byte[] array = md.digest(plainText.getBytes());
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < array.length; ++i) {
                sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getWorkDirectory() {
        return Jenkins.getInstance().root.getAbsolutePath();
    }

}
