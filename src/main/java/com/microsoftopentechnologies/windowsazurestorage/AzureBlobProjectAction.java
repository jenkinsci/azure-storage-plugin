package com.microsoftopentechnologies.windowsazurestorage;

import java.util.List;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Run;

public class AzureBlobProjectAction implements Action {
	private final AbstractProject<?, ?> project;
	
	public AzureBlobProjectAction(AbstractProject<?, ?> project) {
		this.project = project;
	}

	@Override
	public String getDisplayName() {
		return "Azure Last Successful Artifacts";
	}

	@Override
	public String getIconFileName() {
		return null;
	}

	@Override
	public String getUrlName() {
		return null;
	}
	
	public int getMaxResultsDisplay() {
		return 100;
	}
	
	public int getLastSuccessfulBuildNumber() {
	    Run build = project.getLastSuccessfulBuild();
	    if (build == null) {
	        return 0;
	    }
	    return build.getNumber();
	}
	
	public AzureBlobAction getLastSuccessfulArtifactsAction() {
		Run build = project.getLastSuccessfulBuild();
		if (build == null) {
			return null;
		}
		
		List<AzureBlobAction> actions = build.getActions(AzureBlobAction.class);
		if (actions == null || actions.isEmpty()) {
			return null;
		}
		return actions.get(actions.size() - 1);
	}
}
