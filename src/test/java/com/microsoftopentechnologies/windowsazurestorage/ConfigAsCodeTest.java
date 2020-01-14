package com.microsoftopentechnologies.windowsazurestorage;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.casc.CredentialsRootConfigurator;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureStorageAccount;
import hudson.ExtensionList;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.casc.model.Mapping;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ConfigAsCodeTest {

    @Rule
    public JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    public void should_support_configuration_as_code() {
        AzureStorageAccount credentials = (AzureStorageAccount) SystemCredentialsProvider.getInstance().getCredentials()
                .get(0);

        assertEquals(credentials.getScope(), CredentialsScope.GLOBAL);
        assertEquals(credentials.getDescription(), "Account");
        assertEquals(credentials.getStorageAccountName(), "a-storage-account");
        assertEquals(credentials.getBlobEndpointURL(), "https://blob.core.windows.net/");
        assertEquals(credentials.getId(), "storage-account");
        assertNotNull(credentials.getStorageKey());
    }

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    public void export_configuration() throws Exception {
        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);

        CredentialsRootConfigurator root = ExtensionList
                .lookupSingleton(CredentialsRootConfigurator.class);
        final CNode node = root.describe(root.getTargetComponent(context), context);

        assertNotNull(node);
        Mapping mapping = node.asMapping().get("system")
                .asMapping()
                .get("domainCredentials")
                .asSequence()
                .get(0)
                .asMapping()
                .get("credentials")
                .asSequence()
                .get(0)
                .asMapping()
                .get("azureStorageAccount")
                .asMapping();

        assertEquals(mapping.getScalarValue("scope"), "GLOBAL");
        assertEquals(mapping.getScalarValue("description"), "Account");
        assertEquals(mapping.getScalarValue("storageAccountName"), "a-storage-account");
        assertEquals(mapping.getScalarValue("blobEndpointURL"), "https://blob.core.windows.net/");
        assertEquals(mapping.getScalarValue("id"), "storage-account");
        assertNotNull(mapping.getScalarValue("storageKey"));
    }
}
