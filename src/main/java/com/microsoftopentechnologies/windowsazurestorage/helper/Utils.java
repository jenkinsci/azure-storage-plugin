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

import com.microsoft.azure.storage.OperationContext;
import com.microsoft.azure.storage.core.BaseRequest;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;

public class Utils {

    /* Regular expression for valid container name */
    public static final String VAL_CNT_NAME = "^(([a-z\\d]((-(?=[a-z\\d]))|([a-z\\d])){2,62}))$";

    /* Regular expression to match tokens in the format of $TOKEN or ${TOKEN} */
    public static final String TOKEN_FORMAT = "\\$([A-Za-z0-9_]+|\\{[A-Za-z0-9_]+\\})";

    public static final String DEF_BLOB_URL = "http://blob.core.windows.net/";
    public static final String BLOB_ENDPOINT_ENDSUFFIX_KEYWORD = "core";
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

    /**
     * Check for the validity of file share name. Rules:
     * 1. must be from 3 through 63 characters long
     * 2. can contain only lowercase letters, numbers, and hyphens
     * 3. must begin and end with a letter or a number.
     * 4. cannot contain two consecutive hyphens.
     * @param fileShareName
     * @return
     */
    public static boolean validateFileShareName(final String fileShareName) {
        return StringUtils.isNotBlank(fileShareName) && fileShareName.matches(Constants.VAL_SHARE_NAME);
    }

    public static boolean validateBlobName(final String blobName) {
        return blobName != null && (blobName.length() > 0 && blobName.length() <= 1024);
    }

    /**
     * This method checks if text contains tokens in the form of $TOKEN or
     * ${TOKEN}
     *
     * @param text
     * @return true if tokens exist in input string
     */
    public static boolean containTokens(String text) {
        if (StringUtils.isBlank(text)) {
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

        if (StringUtils.isBlank(blobURL)) {
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

    @Nonnull
    public static Jenkins getJenkinsInstance() {
        return Jenkins.getInstance();
    }

    /**
     * Returns md5 hash in string format for a given string
     *
     * @param plainText
     * @return
     */
    public static String getMD5(String plainText) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance(Constants.HASH_TYPE);
            byte[] array = md.digest(plainText.getBytes(Charset.forName("UTF-8")));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < array.length; ++i) {
                sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
        }
        return null;
    }

    public static String getWorkDirectory() {
        File jenkinsRoot;
        jenkinsRoot = Utils.getJenkinsInstance().root;
        if (jenkinsRoot == null) {
            throw new IllegalStateException("Root isn't configured. Couldn't find Jenkins work directory.");
        }

        return jenkinsRoot.getAbsolutePath();

    }

    public static String getPluginInstance() {
        String instanceId = null;
        try {
            if (Utils.getJenkinsInstance().getLegacyInstanceId() == null) {
                instanceId = "local";
            } else {
                instanceId = Utils.getJenkinsInstance().getLegacyInstanceId();
            }
        } catch (Exception e) {
        }
        return instanceId;
    }

    public static String getPluginVersion() {
        String version = Utils.class.getPackage().getImplementationVersion();
        return version;
    }

    public static OperationContext updateUserAgent() throws IOException {

        String userInfo = BaseRequest.getUserAgent();
        String version = null;
        String instanceId = null;

        try {
            version = Utils.getPluginVersion();
            if (version == null) {
                version = "local";
            }
            instanceId = Utils.getPluginInstance();
        } catch (Exception e) {
        }

        String pluginInfo = Constants.PLUGIN_NAME + "/" + version + "/" + instanceId;

        if (userInfo == null) {
            userInfo = pluginInfo;
        } else {
            userInfo = pluginInfo + "/" + userInfo;
        }

        OperationContext opContext = new OperationContext();
        HashMap<String, String> temp = new HashMap<String, String>();
        temp.put("User-Agent", userInfo);

        opContext.setUserHeaders(temp);
        return opContext;
    }

}
