package org.quality.gates.jenkins.plugin;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import net.sf.json.JSONObject;
import org.quality.gates.jenkins.plugin.enumeration.BuildStatusEnum;

public class JobConfigurationService {

    private static final Pattern ENV_VARIABLE_WITH_BRACES_PATTERN = Pattern.compile("(\\$\\{[a-zA-Z0-9_]+\\})");

    private static final Pattern ENV_VARIABLE_WITHOUT_BRACES_PATTERN = Pattern.compile("(\\$[a-zA-Z0-9_]+)");

    public ListBoxModel getListOfSonarInstanceNames(GlobalSonarQualityGatesConfiguration globalConfig) {
        var listBoxModel = new ListBoxModel();

        for (SonarInstance sonarInstance : globalConfig.fetchSonarInstances()) {
            listBoxModel.add(sonarInstance.getName());
        }

        return listBoxModel;
    }

    public JobConfigData createJobConfigData(JSONObject formData, GlobalSonarQualityGatesConfiguration globalConfig) {
        var firstInstanceJobConfigData = new JobConfigData();
        var projectKey = formData.getString("projectKey");

        if (!projectKey.startsWith("$")) {
            projectKey = URLDecoder.decode(projectKey, StandardCharsets.UTF_8);
        }

        var name = "";

        if (!globalConfig.fetchSonarInstances().isEmpty()) {
            name = hasFormDataKey(formData, globalConfig);
        }

        firstInstanceJobConfigData.setProjectKey(projectKey);
        firstInstanceJobConfigData.setSonarInstanceName(name);
        firstInstanceJobConfigData.setBuildStatus(BuildStatusEnum.valueOf(formData.getString("buildStatus")));

        return firstInstanceJobConfigData;
    }

    protected String hasFormDataKey(JSONObject formData, GlobalSonarQualityGatesConfiguration globalConfig) {
        if (formData.containsKey("sonarInstancesName")) {
            return formData.getString("sonarInstancesName");
        }

        return globalConfig.fetchSonarInstances().get(0).getName();
    }

    public JobConfigData checkProjectKeyIfVariable(
            JobConfigData jobConfigData, AbstractBuild build, BuildListener listener) throws QGException {
        var projectKey = jobConfigData.getProjectKey();

        if (projectKey.isEmpty()) {
            throw new QGException("Empty project key.");
        }

        var envVariableJobConfigData = new JobConfigData();
        envVariableJobConfigData.setSonarInstanceName(jobConfigData.getSonarInstanceName());

        try {
            envVariableJobConfigData.setProjectKey(getProjectKey(projectKey, build.getEnvironment(listener)));
        } catch (IOException | InterruptedException e) {
            throw new QGException(e);
        }

        envVariableJobConfigData.setSonarInstanceName(jobConfigData.getSonarInstanceName());
        envVariableJobConfigData.setBuildStatus(jobConfigData.getBuildStatus());

        return envVariableJobConfigData;
    }

    private String getProjectKey(final String projectKey, EnvVars env) {
        var projectKeyAfterFirstResolving =
                resolveEmbeddedEnvVariables(projectKey, env, ENV_VARIABLE_WITH_BRACES_PATTERN, 1);

        return resolveEmbeddedEnvVariables(projectKeyAfterFirstResolving, env, ENV_VARIABLE_WITHOUT_BRACES_PATTERN, 0);
    }

    private String resolveEmbeddedEnvVariables(
            final String projectKey, final EnvVars env, final Pattern pattern, final int braceOffset) {
        var matcher = pattern.matcher(projectKey);
        var builder = new StringBuilder(projectKey);
        var matchesFound = false;
        var offset = 0;

        while (matcher.find()) {
            var envVariable = projectKey.substring(matcher.start() + braceOffset + 1, matcher.end() - braceOffset);
            var envValue = env.get(envVariable);

            if (envValue == null) {
                throw new QGException("Environment Variable [" + envVariable + "] not found");
            }

            builder.replace(matcher.start() + offset, matcher.end() + offset, envValue);
            offset += envValue.length() - matcher.group(1).length();
            matchesFound = true;
        }

        if (matchesFound) {
            return getProjectKey(builder.toString(), env);
        }

        return builder.toString();
    }
}
