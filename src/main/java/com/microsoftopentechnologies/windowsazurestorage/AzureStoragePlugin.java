/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoftopentechnologies.windowsazurestorage;

import com.microsoft.jenkins.azurecommons.telemetry.AppInsightsClientFactory;
import hudson.Plugin;

import java.util.HashMap;
import java.util.Map;

public class AzureStoragePlugin extends Plugin {
    public static void sendEvent(final String item, final String action, final String... properties) {
        final Map<String, String> props = new HashMap<>();
        for (int i = 1; i < properties.length; i += 2) {
            props.put(properties[i - 1], properties[i]);
        }
        sendEvent(item, action, props);
    }

    public static void sendEvent(final String item, final String action, final Map<String, String> properties) {
        AppInsightsClientFactory.getInstance(AzureStoragePlugin.class)
                .sendEvent(item, action, properties, false);
    }
}
