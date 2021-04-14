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
import hudson.Util;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class Utils {

    public static final String DEF_BLOB_URL = "https://blob.core.windows.net/";
    public static final String BLOB_ENDPOINT_ENDSUFFIX_KEYWORD = "core";
    public static final int BLOB_NAME_LENGTH_LIMIT = 1024;

    /**
     * Checks for validity of container name after converting the input into
     * lowercase. Rules for container name 1.Container names must start with a
     * letter or number, and can contain only letters, numbers, and the dash (-)
     * character. 2.Every dash (-) character must be immediately preceded and
     * followed by a letter or number; consecutive dashes are not permitted in
     * container names. 3.All letters in a container name must be lowercase.
     * 4.Container names must be from 3 through 63 characters long. 5.Root
     * container and web container are specially treated.
     *
     * @param containerName Name of the Azure storage container
     * @return true if container name is valid else returns false
     */
    public static boolean validateContainerName(String containerName) {
        if (containerName != null) {
            if (containerName.equals(Constants.ROOT_CONTAINER) || containerName.equals(Constants.WEB_CONTAINER)) {
                return true;
            }

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
     *
     * @param fileShareName
     * @return
     */
    public static boolean validateFileShareName(String fileShareName) {
        return StringUtils.isNotBlank(fileShareName) && fileShareName.matches(Constants.VAL_SHARE_NAME);
    }

    public static boolean validateBlobName(String blobName) {
        return blobName != null && (blobName.length() > 0 && blobName.length() <= BLOB_NAME_LENGTH_LIMIT);
    }

    /**
     * This method checks if text contains tokens in the form of $TOKEN or
     * ${TOKEN}.
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

        String preparedURL = blobURL;
        // Append forward slash
        if (!preparedURL.endsWith(Constants.FWD_SLASH)) {
            preparedURL = preparedURL.concat(Constants.FWD_SLASH);
        }

        // prepend http protocol if missing
        if (!preparedURL.contains(Constants.PRT_SEP)) {
            preparedURL = Constants.HTTP_PRT + preparedURL;
        }

        return preparedURL;
    }

    /**
     * Returns default blob url.
     *
     * @return
     */
    public static String getDefaultBlobURL() {
        return Constants.DEF_BLOB_URL;
    }

    /**
     * Returns md5 hash in string format for a given string.
     *
     * @param plainText
     * @return
     */
    public static String getMD5(String plainText) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance(Constants.HASH_TYPE);
            byte[] array = md.digest(plainText.getBytes(Charset.forName("UTF-8")));
            StringBuilder sb = new StringBuilder();
            final int byteMask = 0xFF;
            final int byteExtended = 0x100;
            final int length = 3;
            for (int i = 0; i < array.length; ++i) {
                sb.append(Integer.toHexString((array[i] & byteMask) | byteExtended).substring(1, length));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
        }
        return null;
    }

    public static String getWorkDirectory() {
        File jenkinsRoot;
        jenkinsRoot = Jenkins.get().root;
        if (jenkinsRoot == null) {
            throw new IllegalStateException("Root isn't configured. Couldn't find Jenkins work directory.");
        }

        return jenkinsRoot.getAbsolutePath();

    }

    public static String getPluginInstance() {
        String instanceId = null;
        try {
            if (Jenkins.get().getLegacyInstanceId() == null) {
                instanceId = "local";
            } else {
                instanceId = Jenkins.get().getLegacyInstanceId();
            }
        } catch (Exception e) {
        }
        return instanceId;
    }

    public static String getPluginVersion() {
        return Utils.class.getPackage().getImplementationVersion();
    }

    public static OperationContext updateUserAgent() throws IOException {
        return updateUserAgent(null);
    }

    public static OperationContext updateUserAgent(final Long contentLength) throws IOException {
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

        String pluginUserAgent;
        if (contentLength == null) {
            pluginUserAgent = String.format("%s/%s/%s", Constants.PLUGIN_NAME, version, instanceId);
        } else {
            pluginUserAgent = String.format("%s/%s/%s/ContentLength/%s",
                    Constants.PLUGIN_NAME, version, instanceId, contentLength.toString());
        }

        final String baseUserAgent = BaseRequest.getUserAgent();
        if (baseUserAgent != null) {
            pluginUserAgent = pluginUserAgent + "/" + baseUserAgent;
        }

        OperationContext opContext = new OperationContext();
        HashMap<String, String> temp = new HashMap<String, String>();
        temp.put("User-Agent", pluginUserAgent);

        opContext.setUserHeaders(temp);
        return opContext;
    }

    /**
     * wrapper of method {@link hudson.Util#replaceMacro(String, Map)}, make the result into lower case.
     *
     * @param s
     * @param props
     * @param locale
     * @return
     */
    public static String replaceMacro(String s, Map<String, String> props, Locale locale) {
        String result = Util.replaceMacro(s, props);
        if (result == null) {
            return null;
        }
        return result.trim().toLowerCase(locale);
    }

    /**
     * wrapper of method {@link hudson.Util#replaceMacro(String, Map)}, trim the result.
     *
     * @param s
     * @param props
     * @return
     */
    public static String replaceMacro(String s, Map<String, String> props) {
        String result = Util.replaceMacro(s, props);
        if (result == null) {
            return null;
        }
        return result.trim();
    }

    private Utils() {
        // hide constructor
    }
}
