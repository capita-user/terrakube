package io.terrakube.executor.plugin.tfstate.api;

import io.terrakube.client.model.organization.job.TfOutputRequest;
import io.terrakube.client.model.response.Response;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.text.TextStringBuilder;
import org.springframework.beans.factory.annotation.Value;
import io.terrakube.client.TerrakubeClient;
import io.terrakube.client.model.generic.Resource;
import io.terrakube.client.model.state.CreateStateVersion;
import io.terrakube.client.model.state.CreateStateVersionAttributes;
import io.terrakube.client.model.state.CreateStateVersionRelationships;
import io.terrakube.client.model.state.CreateStateVersionRequest;
import io.terrakube.client.model.state.RunData;
import io.terrakube.executor.plugin.tfstate.TerraformState;
import io.terrakube.executor.plugin.tfstate.TerraformStatePathService;
import io.terrakube.executor.service.mode.TerraformJob;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.Base64;
import java.util.stream.Stream;

@Slf4j
@Builder
@Getter
@Setter
public class ApiTerraformStateImpl implements TerraformState {
    private static final String TERRAFORM_PLAN_FILE = "terraformLibrary.tfPlan";
    private static final String BACKEND_FILE_NAME = "api_backend_override.tf";
    private static final String STATE_FILE_NAME = "api.tfstate";

    @NonNull
    TerrakubeClient terrakubeClient;

    @NonNull
    TerraformStatePathService terraformStatePathService;


    @Value("${io.terrakube.api.url}")
    private String apiUrl;

    private final static String URL_PLAN = "%s/organization/%s/workspace/%s/jobId/%s/step/%s/terraform.tfstate";

    @Override
    public String getBackendStateFile(String organizationId, String workspaceId, File workingDirectory, String terraformVersion) {

        File stateFile = new File(
                FilenameUtils.separatorsToSystem(
                        String.join(File.separator, Stream.of(workingDirectory.getAbsolutePath(), STATE_FILE_NAME)
                                .toArray(String[]::new))));
        if (stateFile.exists()) {
            log.info("State file already exists");
            return BACKEND_FILE_NAME;
        }
        log.info("Downloading state file");

        byte[] res = terrakubeClient.getCurrentState(organizationId, workspaceId);

        if (res.length == 0) {
            log.warn("No state file found. This is probably the first run.");
        } else {

            try {
                FileUtils.writeByteArrayToFile(stateFile, res);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        TextStringBuilder localBackendHcl = new TextStringBuilder();
        localBackendHcl.appendln("terraform {");
        localBackendHcl.appendln("  backend \"local\" {");
        localBackendHcl.appendln("    path                  = \"" + stateFile.getAbsolutePath() + "\"");
        localBackendHcl.appendln("  }");
        localBackendHcl.appendln("}");


        File localBackendFile = new File(
                FilenameUtils.separatorsToSystem(
                        String.join(File.separator, Stream.of(workingDirectory.getAbsolutePath(), BACKEND_FILE_NAME)
                                .toArray(String[]::new))));

        log.info("Creating Local Backend File: {}", localBackendFile.getAbsolutePath());
        try {
            FileUtils.writeStringToFile(localBackendFile, localBackendHcl.toString(), Charset.defaultCharset());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return BACKEND_FILE_NAME;
    }

    @Override
    public String saveTerraformPlan(String organizationId, String workspaceId, String jobId, String stepId,
                                    File workingDirectory) {
        log.info("Saving plan file to terrakube API");

        Path localPlanPath = Paths.get(workingDirectory.getAbsolutePath() + "/" + TERRAFORM_PLAN_FILE);
        File localPlanFile = localPlanPath.toFile();

        if (localPlanFile.exists()) {
            try {
                byte[] planBytes = Files.readAllBytes(localPlanFile.toPath());
                log.info(String.format("bytes: 0x%02X 0x%02X 0x%02X 0x%02X 0x%02X 0x%02X 0x%02X 0x%02X", planBytes[0], planBytes[1], planBytes[2], planBytes[3], planBytes[4], planBytes[5], planBytes[6], planBytes[7]));

                terrakubeClient.uploadPlanState(planBytes, organizationId, workspaceId, jobId, stepId);

                return String.format("%s/tfstate/v1/organization/%s/workspace/%s/jobId/%s/step/%s/terraform.tfstate",
                        apiUrl, organizationId, workspaceId, jobId, stepId);
            } catch (Exception e) {
                log.error("Failed to upload plan file to terrakube API: {}", e.getMessage());
                return null;
            }

        } else {
            log.warn("Terraform plan file not found");
            return null;
        }
    }

    @Override
    public boolean downloadTerraformPlan(String organizationId, String workspaceId, String jobId, String stepId,
                                         File workingDirectory) {
        log.info("Downloading plan file from terrakube API");
        log.info("Working directory: {}", workingDirectory.getAbsolutePath());
        log.info("Workspace Id: {}", workspaceId);
        log.info("Job Id: {}", jobId);
        log.info("Step Id: {}", stepId);

        String localPlanPath = workingDirectory.getAbsolutePath() + "/" + TERRAFORM_PLAN_FILE;
        String stateUrl = terrakubeClient.getJobById(organizationId, jobId).getData().getAttributes().getTerraformPlan();
        log.info("stateUrl: {}", stateUrl);
        String fullPath = null;
        try {
            fullPath = new URL(stateUrl).getPath();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        log.info("fullPath: {}", fullPath);

        byte[] res = terrakubeClient.getPlanState(fullPath);

        if (res.length == 0) {
            log.info("No plan file found");
            return false;
        }
        try {
            FileUtils.writeByteArrayToFile(new File(localPlanPath), res);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;

    }

    @Override
    public void saveStateJson(TerraformJob terraformJob, String applyJSON, String rawState) {
        log.info("Saving state json to terrakube API");
        // Attributes
        CreateStateVersionAttributes stateAttributes = new CreateStateVersionAttributes();
        stateAttributes.setJsonState(Base64.getEncoder().encodeToString(applyJSON.getBytes()));
        stateAttributes.setState(Base64.getEncoder().encodeToString(rawState.getBytes()));

        // Relationship
        Resource runResource = new Resource();
        runResource.setType("runs");
        runResource.setId(terraformJob.getJobId());
        RunData runData = new RunData();
        runData.setData(runResource);
        CreateStateVersionRelationships stateRelationships = new CreateStateVersionRelationships();
        stateRelationships.setRun(runData);

        CreateStateVersion createStateVersion = new CreateStateVersion();
        createStateVersion.setType("state-versions");
        createStateVersion.setAttributes(stateAttributes);
        createStateVersion.setRelationships(stateRelationships);

        CreateStateVersionRequest createStateVersionRequest = new CreateStateVersionRequest();
        createStateVersionRequest.setData(createStateVersion);

        terrakubeClient.createWorkspaceStateVersion(
                createStateVersionRequest,
                terraformJob.getWorkspaceId()
        );
    }


    @Override
    public String saveOutput(String organizationId, String jobId, String stepId, String output, String outputError) {
        log.info("Uploading output for org: {}, job: {}, step: {}", organizationId, jobId, stepId);

        TfOutputRequest tfOutputRequest = new TfOutputRequest();
        tfOutputRequest.setData(output + outputError);
        Response<String> listOrganization = terrakubeClient.uploadOutput(tfOutputRequest, organizationId, jobId, stepId);
        return listOrganization.getData();
    }

}
