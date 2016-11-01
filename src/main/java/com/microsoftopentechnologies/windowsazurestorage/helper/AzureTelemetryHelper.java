/*
 Copyright 2016 Microsoft, Inc.
 
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

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AzureTelemetryHelper {

    private static String PLUGIN_NAME = "AzureJenkinsStorage";
    private static String User_Agent = "AzureJenkinsStoragePlugin";
    private static String AI_KEY = "3aa27b91-92fd-4d2d-be8a-0ca67c842560";
    private static String user_location = System.getProperty("user.country");
    private static String user_language = System.getProperty("user.language");
    private static String system_information = System.getProperty("os.name") + "-" + System.getProperty("os.version") + "-" + System.getProperty("os.arch");
    private static String java_information = System.getProperty("java.version");


    private boolean isTelemetry() {
        boolean is_telemetry_on = TelemetryConfiguration.getActive().isTrackingDisabled();
        //returns false if telemetry is disabled, returns true if telemetry is on
        return !is_telemetry_on;
    }
    private String getPluginVersion() {
        String version = getClass().getPackage().getImplementationVersion();
        if (version == null) {
            return "local";
        }
        return version;
    }

    private double convertPluginVersion(String version) {
        if (version.contains("local")) {
            return 0.0;
        }
        return Double.parseDouble((version.replace(".", "")));
    }
    
    public void createEvent(String eventName, String message){
        TelemetryClient tc = new TelemetryClient();
        tc.getContext().setInstrumentationKey(AI_KEY);
        
        tc.getContext().getUser().setUserAgent(User_Agent);
        Map<String, String> properties = new HashMap<String, String>();
        
        properties.put("ActionDetail", message);
        
        String version = PLUGIN_NAME + "/" + getPluginVersion();
        properties.put("UserAgent", version);
        properties.put("OSInformation", system_information);
        properties.put("UserLocation", user_location);
        properties.put("UserLanguage", user_language);
        properties.put("JavaInformation", java_information);
        
        Map<String, Double> metrics = new HashMap<String, Double>();

        metrics.put("NumericVersion", convertPluginVersion(version));
        
        if(isTelemetry())
        {
            try {
                tc.trackEvent(eventName, properties, metrics);
                Thread.sleep(5000);
                tc.flush();
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
                tc.trackException(ex);
                tc.flush();
                Logger.getLogger(AzureTelemetryHelper.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void createErrorEvent(String eventName, String e){
        TelemetryClient tc = new TelemetryClient();
        tc.getContext().setInstrumentationKey(AI_KEY);
        tc.getContext().getUser().setUserAgent(User_Agent);
        
        Map<String, String> properties = new HashMap<String, String>();
        properties.put("ErrorDetail", e);
        
        String version = PLUGIN_NAME + "/" + getPluginVersion();
        properties.put("UserAgent", version);
        properties.put("OSInformation", system_information);
        properties.put("UserLocation", user_location);
        properties.put("UserLanguage", user_language);
        properties.put("JavaInformation", java_information);

        Map<String, Double> metrics = new HashMap<String, Double>();

        metrics.put("NumericVersion", convertPluginVersion(getPluginVersion()));
        
        if(isTelemetry())
        {
            try {
                tc.trackEvent(eventName, properties, metrics);
                Thread.sleep(5000);
                tc.flush();
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
                tc.trackException(ex);
                tc.flush();
                Logger.getLogger(AzureTelemetryHelper.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
    }

}
