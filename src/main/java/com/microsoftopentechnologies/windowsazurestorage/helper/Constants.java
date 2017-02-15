/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.microsoftopentechnologies.windowsazurestorage.helper;

/**
 *
 * @author arroyc
 */
public class Constants {

    /* Regular expression for valid container name */
    public static final String VAL_CNT_NAME = "^(([a-z\\d]((-(?=[a-z\\d]))|([a-z\\d])){2,62}))$";

    /* Regular expression to match tokens in the format of $TOKEN or ${TOKEN} */
    public static final String TOKEN_FORMAT = "\\$([A-Za-z0-9_]+|\\{[A-Za-z0-9_]+\\})";

    public static final String DEF_BLOB_URL = "http://blob.core.windows.net/";
    public static final String FWD_SLASH = "/";
    public static final String HTTP_PRT = "http://";
    // http Protocol separator
    public static final String PRT_SEP = "://";
    public static final String LEGACY_STORAGE_CONFIG_FILE = "com.microsoftopentechnologies.windowsazurestorage.WAStoragePublisher.xml";
    public static final String HASH_TYPE = "MD5";
    public static final String CREDENTIALS_AJAX_URI = "/descriptor/com.cloudbees.plugins.credentials.CredentialsSelectHelper/resolver/com.cloudbees.plugins.credentials.CredentialsSelectHelper$SystemContextResolver/provider/com.cloudbees.plugins.credentials.SystemCredentialsProvider$ProviderImpl/context/jenkins/dialog";
    public static final String PLUGIN_NAME = "AzureJenkinsStorage";

}
