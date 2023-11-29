/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.microsoftopentechnologies.windowsazurestorage.helper;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author arroyc
 */
public final class CredentialMigration {
    private static final Logger LOGGER = Logger.getLogger(CredentialMigration.class.getName());

    protected static List<StorageAccountInfo> getOldStorageConfig(
            File inputFile) throws SAXException, IOException, ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        // Load the legacy storage config XML document, parse it and return a list of storage accounts
        Document document = builder.parse(inputFile);

        List<StorageAccountInfo> storages = new ArrayList<StorageAccountInfo>();

        NodeList nodeList = document.getElementsByTagName(
                "com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);

            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) node;

                // Get the value of all sub-elements.
                String accName = elem.getElementsByTagName("storageAccName")
                        .item(0).getChildNodes().item(0).getNodeValue();

                String accKey = elem.getElementsByTagName("storageAccountKey").item(0)
                        .getChildNodes().item(0).getNodeValue();

                String blobURL = elem.getElementsByTagName("blobEndPointURL").item(0)
                        .getChildNodes().item(0).getNodeValue();

                storages.add(new StorageAccountInfo(accName, accKey, blobURL, ""));
            }
        }

        return storages;

    }

    private static File backupFile(String sourceFile) throws IOException {
        String backupFile = sourceFile + ".backup";
        LOGGER.log(Level.INFO, sourceFile + ".backup has been created for backup.");
        File backUp = new File(backupFile);
        FileUtils.copyFile(new File(sourceFile), backUp);
        return backUp;
    }

    /**
     * Take the legacy local storage credential configuration and create an
     * equivalent global credential in Jenkins Credential Store.
     */
    private static void removeFile(String sourceFile) throws IOException {
        File file = new File(sourceFile);

        if (file.delete()) {
            LOGGER.log(Level.INFO, file.getName() + " is deleted!");
        } else {
            LOGGER.log(Level.WARNING, file.getName() + "deletion is failed.");
        }
    }

    public static void upgradeStorageConfig() throws Exception {

        File sourceFile = new File(Utils.getWorkDirectory(), Constants.LEGACY_STORAGE_CONFIG_FILE);
        try {
            //check if we need to upgrade (i.e. if we have prior version of 0.3.2 storage plugin)
            if (!sourceFile.exists()) {
                return;
            }

            LOGGER.log(Level.INFO, sourceFile + " exists, upgrade will start now...");

            File backUp = backupFile(sourceFile.getCanonicalPath());
            List<StorageAccountInfo> oldStorages = getOldStorageConfig(sourceFile);

            for (StorageAccountInfo sa : oldStorages) {
                String storageAccount = sa.getStorageAccName();
                String storageAccountKey = sa.getStorageAccountKey();
                String storageBlobURL = sa.getBlobEndPointURL();

                AzureStorageAccount.StorageAccountCredential u =
                        new AzureStorageAccount.StorageAccountCredential(
                                storageAccount, storageAccountKey, storageBlobURL, "");
                AzureStorageAccount cred = CredentialsMatchers.firstOrNull(
                        CredentialsProvider.lookupCredentials(
                                AzureStorageAccount.class,
                                Jenkins.getInstance(),
                                ACL.SYSTEM,
                                Collections.<DomainRequirement>emptyList()),
                        CredentialsMatchers.withId(u.getId()));
                if (cred != null) {
                    return;
                }

                LOGGER.log(Level.INFO,
                        "Moving Storage Account names and their keys to credential store, "
                                + "a creddential Id will be created for each pair of account name and key.");

                // no matching, so make our own.
                AzureStorageAccount tempCred = new AzureStorageAccount(
                        CredentialsScope.GLOBAL,
                        Utils.getMD5(storageAccount.concat(storageAccountKey)),
                        "credential for " + storageAccount,
                        storageAccount,
                        storageAccountKey,
                        storageBlobURL, "");
                final SecurityContext securityContext = ACL.impersonate(ACL.SYSTEM);

                try {
                    CredentialsStore s = CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
                    try {
                        s.addCredentials(Domain.global(), tempCred);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                } finally {

                    SecurityContextHolder.setContext(securityContext);

                } // end finally

            } //end for

            LOGGER.log(Level.INFO, "Migrated successfully, deleting legacy config files...");
            removeFile(sourceFile.getCanonicalPath());
            removeFile(backUp.getCanonicalPath());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private CredentialMigration() {
        // hide constructor
    }
}
