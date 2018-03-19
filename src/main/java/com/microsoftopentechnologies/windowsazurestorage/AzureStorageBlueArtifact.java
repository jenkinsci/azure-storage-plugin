package com.microsoftopentechnologies.windowsazurestorage;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import hudson.Extension;
import hudson.model.Run;
import io.jenkins.blueocean.rest.Reachable;
import io.jenkins.blueocean.rest.factory.BlueArtifactFactory;
import io.jenkins.blueocean.rest.hal.Link;
import io.jenkins.blueocean.rest.model.BlueArtifact;

import java.util.Collection;

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
        return artifact.getBlobURL();
    }

    @Override
    public String getUrl() {
        return artifact.getBlobURL();
    }

    @Override
    public long getSize() {
        return -1;
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
            return Collections2.transform(action.getIndividualBlobs(), new Function<AzureBlob, BlueArtifact>() {
                @Override
                public BlueArtifact apply(AzureBlob azureBlob) {
                    return new AzureStorageBlueArtifact(action, azureBlob, reachable.getLink());
                }
            });
        }
    }
}
