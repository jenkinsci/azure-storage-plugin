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
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.casc.model.Mapping;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@WithJenkinsConfiguredWithCode
class ConfigAsCodeTest {

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    void should_support_configuration_as_code(JenkinsConfiguredWithCodeRule j) {
        AzureStorageAccount credentials = (AzureStorageAccount) SystemCredentialsProvider.getInstance().getCredentials()
                .get(0);

        assertThat(credentials.getScope(), is(CredentialsScope.GLOBAL));
        assertThat(credentials.getDescription(), is("Account"));
        assertThat(credentials.getStorageAccountName(), is("a-storage-account"));
        assertThat(credentials.getBlobEndpointURL(), is("https://a-storage-account.blob.core.windows.net/"));
        assertThat(credentials.getId(), is("storage-account"));
        assertNotNull(credentials.getStorageKey());
    }

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    void should_support_configuration_as_code_with_cdn(JenkinsConfiguredWithCodeRule j) {
        AzureStorageAccount credentials = (AzureStorageAccount) SystemCredentialsProvider.getInstance().getCredentials()
                .get(1);

        assertThat(credentials.getScope(), is(CredentialsScope.GLOBAL));
        assertThat(credentials.getDescription(), is("CDN Enabled Account"));
        assertThat(credentials.getStorageAccountName(), is("b-storage-account"));
        assertThat(credentials.getBlobEndpointURL(), is("https://b-storage-account.blob.core.windows.net/"));
        assertThat(credentials.getCdnEndpointURL(), is("https://cdn-resource-name.azureedge.net/"));
        assertThat(credentials.getId(), is("cdn-enabled-storage-account"));
        assertNotNull(credentials.getStorageKey());
    }

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    void export_configuration(JenkinsConfiguredWithCodeRule j) throws Exception {
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

        assertThat(mapping.getScalarValue("scope"), is("GLOBAL"));
        assertThat(mapping.getScalarValue("description"), is("Account"));
        assertThat(mapping.getScalarValue("storageAccountName"), is("a-storage-account"));
        assertThat(mapping.getScalarValue("blobEndpointURL"), is("https://a-storage-account.blob.core.windows.net/"));
        assertThat(mapping.getScalarValue("id"), is("storage-account"));
        assertNotNull(mapping.getScalarValue("storageKey"));
    }

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    void export_configuration_with_cdn(JenkinsConfiguredWithCodeRule j) throws Exception {
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
                .get(1)
                .asMapping()
                .get("azureStorageAccount")
                .asMapping();

        assertThat(mapping.getScalarValue("scope"), is("GLOBAL"));
        assertThat(mapping.getScalarValue("description"), is("CDN Enabled Account"));
        assertThat(mapping.getScalarValue("storageAccountName"), is("b-storage-account"));
        assertThat(mapping.getScalarValue("blobEndpointURL"), is("https://b-storage-account.blob.core.windows.net/"));
        assertThat(mapping.getScalarValue("cdnEndpointURL"), is("https://cdn-resource-name.azureedge.net/"));
        assertThat(mapping.getScalarValue("id"), is("cdn-enabled-storage-account"));
        assertNotNull(mapping.getScalarValue("storageKey"));
    }
}
