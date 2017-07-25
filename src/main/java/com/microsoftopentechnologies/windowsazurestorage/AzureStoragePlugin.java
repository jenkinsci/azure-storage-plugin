/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoftopentechnologies.windowsazurestorage;

import com.microsoft.jenkins.azurecommons.telemetry.AppInsightsClientFactory;
import hudson.Plugin;

import java.util.Map;

public class AzureStoragePlugin extends Plugin {
    public static void sendEvent(final String item, final String action, final Map<String, String> properties) {
        AppInsightsClientFactory.getInstance(AzureStoragePlugin.class)
                .sendEvent(item, action, properties, false);
    }
}
