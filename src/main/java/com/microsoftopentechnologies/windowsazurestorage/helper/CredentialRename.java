/*
 Copyright 2019 Microsoft, Inc.

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

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CredentialRename {
    private static final String BACKUP_SUFFIX = ".backup";
    private static final String SOURCE_CONTENT =
            "com.microsoftopentechnologies.windowsazurestorage.helper.AzureCredentials";
    private static final String TARGET_CONTENT =
            "com.microsoftopentechnologies.windowsazurestorage.helper.AzureStorageAccount";

    private CredentialRename() {
    }

    private static final Logger LOGGER = Logger.getLogger(CredentialMigration.class.getName());

    private static File backupFile(String sourceFile) throws IOException {
        String backupFile = sourceFile + BACKUP_SUFFIX;
        LOGGER.log(Level.INFO, sourceFile + ".backup has been created for backup.");
        File backUp = new File(backupFile);
        FileUtils.copyFile(new File(sourceFile), backUp);
        return backUp;
    }

    private static void recoverFile(String backupFile) throws IOException {
        String sourceFile = backupFile.substring(0, backupFile.length() - BACKUP_SUFFIX.length());
        LOGGER.info("Start to recover credentials.xml with " + backupFile);
        File source = new File(sourceFile);
        try {
            FileUtils.copyFile(new File(backupFile), source);
        } catch (IOException e) {
            LOGGER.warning("Fail to recover credentials with backup file, please manually recover it.");
            throw e;
        }
        removeFile(backupFile);
    }

    public static void renameStorageConfig() throws IOException {
        File sourceFile = new File(Utils.getWorkDirectory(), "credentials.xml");
        if (!sourceFile.exists()) {
            return;
        }
        Path path = Paths.get(sourceFile.getCanonicalPath());

        try (Stream<String> stream = Files.lines(path)) {
            boolean needRename = stream.anyMatch(s -> s.contains(SOURCE_CONTENT));
            if (!needRename) {
                return;
            }
        }

        LOGGER.log(Level.INFO, sourceFile + " exists, rename azure storage credential will start now...");
        File backUp = backupFile(sourceFile.getCanonicalPath());

        try (Stream<String> stream = Files.lines(path)) {
            List<String> collect = stream
                    .map(s -> s.replace(SOURCE_CONTENT, TARGET_CONTENT))
                    .collect(Collectors.toList());
            Files.write(path, collect);
        } catch (IOException e) {
            LOGGER.warning("Failed to rename azure storage credential in credentials.xml file");
            recoverFile(backUp.getCanonicalPath());
        }

        removeFile(backUp.getCanonicalPath());
    }

    private static void removeFile(String sourceFile) {
        File file = new File(sourceFile);

        if (file.delete()) {
            LOGGER.log(Level.INFO, file.getName() + " is deleted!");
        } else {
            LOGGER.log(Level.WARNING, file.getName() + "deletion is failed.");
        }
    }
}
