package org.quality.gates.jenkins.plugin;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.util.Arrays;
import javax.inject.Inject;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.quality.gates.jenkins.plugin.enumeration.BuildStatusEnum;

@Extension
public final class QGPublisherDescriptor extends BuildStepDescriptor<Publisher> {

    @Inject
    private JobConfigurationService jobConfigurationService;

    @Inject
    private JobExecutionService jobExecutionService;

    public QGPublisherDescriptor() {
        super(QGPublisher.class);
        load();
    }

    public QGPublisherDescriptor(
            JobExecutionService jobExecutionService, JobConfigurationService jobConfigurationService) {
        super(QGPublisher.class);
        this.jobExecutionService = jobExecutionService;
        this.jobConfigurationService = jobConfigurationService;
    }

    public JobExecutionService getJobExecutionService() {
        return jobExecutionService;
    }

    public ListBoxModel doFillSonarInstancesItems() {
        return jobConfigurationService.getListOfSonarInstanceNames(jobExecutionService.getGlobalConfigData());
    }

    public FormValidation doCheckProjectKey(@QueryParameter String projectKey) {
        if (projectKey.isEmpty()) {
            return FormValidation.error("Please insert project key.");
        }

        return FormValidation.ok();
    }

    public ListBoxModel doFillBuildStatusItems() {
        var items = new ListBoxModel();
        Arrays.asList(BuildStatusEnum.values()).forEach(e -> items.add(e.toString()));
        return items;
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
        return true;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Quality Gates Sonarqube Plugin";
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        save();
        return true;
    }

    @Override
    public QGPublisher newInstance(StaplerRequest2 req, JSONObject formData) throws QGException {
        var firstInstanceJobConfigData =
                jobConfigurationService.createJobConfigData(formData, jobExecutionService.getGlobalConfigData());
        var sonarInstance = jobExecutionService
                .getGlobalConfigData()
                .getSonarInstanceByName(firstInstanceJobConfigData.getSonarInstanceName());

        return new QGPublisher(firstInstanceJobConfigData, sonarInstance);
    }
}
