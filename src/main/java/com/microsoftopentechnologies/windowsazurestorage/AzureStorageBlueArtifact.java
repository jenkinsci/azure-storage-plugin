package com.microsoftopentechnologies.windowsazurestorage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import hudson.Extension;
import hudson.model.Run;
import io.jenkins.blueocean.rest.Reachable;
import io.jenkins.blueocean.rest.factory.BlueArtifactFactory;
import io.jenkins.blueocean.rest.hal.Link;
import io.jenkins.blueocean.rest.model.BlueArtifact;

@Extension(optional = true)
public final class AzureStorageBlueArtifact extends BlueArtifact {
    private final AzureBlobAction action;
    private final AzureBlob artifact;

    public AzureStorageBlueArtifact(AzureBlobAction action, AzureBlob artifact, Link parent) {
        super(parent);
        this.action = action;
        this.artifact = artifact;
    }

    @Override
    public String getName() {
        return artifact.getBlobName();
    }

    @Override
    public String getPath() {
        return artifact.getBlobName();
    }

    @Override
    public String getUrl() {
        return String.format("/%s%s/processDownloadRequest/%s", action.getBuild().getUrl(), action.getUrlName(),
                artifact.getBlobName());
    }

    @Override
    public long getSize() {
        return artifact.getSizeInBytes();
    }

    @Override
    public boolean isDownloadable() {
        return true;
    }

    @Extension
    public static final class FactoryImpl extends BlueArtifactFactory {
        @Override
        public Collection<BlueArtifact> getArtifacts(Run<?, ?> run, final Reachable reachable) {
            final AzureBlobAction action = run.getAction(AzureBlobAction.class);
            if (action == null) {
                return null;
            }
            List<BlueArtifact> result = new ArrayList<>();
            AzureBlob zipArtifact = action.getZipArchiveBlob();
            if (zipArtifact != null) {
                result.add(new AzureStorageBlueArtifact(action, zipArtifact, reachable.getLink()));
            }
            for (AzureBlob artifact : action.getIndividualBlobs()) {
                result.add(new AzureStorageBlueArtifact(action, artifact, reachable.getLink()));
            }
            return result;
        }
    }
}
