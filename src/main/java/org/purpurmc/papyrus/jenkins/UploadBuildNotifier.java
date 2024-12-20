package org.purpurmc.papyrus.jenkins;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.plugins.git.GitChangeSet;
import hudson.scm.ChangeLogSet;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Mailer;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressFBWarnings({"NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE"})
public class UploadBuildNotifier extends Notifier {
    public final String url;
    public final String accessToken;
    public final String project;
    public final String version;
    public final String fileName;
    public final String postScript;

    private List<Metadata> metadata = new ArrayList<>();

    @DataBoundConstructor
    public UploadBuildNotifier(String url, String accessToken, String project, String version, String fileName, String postScript) {
        this.accessToken = accessToken;
        this.project = project;
        this.version = version;
        this.fileName = fileName;
        this.postScript = postScript;

        url = url.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        this.url = url;
    }

    public List<Metadata> getMetadata() {
        return this.metadata;
    }

    @DataBoundSetter
    public void setMetadata(List<Metadata> metadata) {
        this.metadata = new ArrayList<>();
        if (metadata != null) {
            this.metadata.addAll(metadata);
        }
    }

    Map<String, String> getMetadataMap() {
        return Metadata.toMap(getMetadata());
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        CreateBuildRequest createBuild = new CreateBuildRequest();
        createBuild.project = this.project;
        createBuild.version = this.version;
        createBuild.build = String.valueOf(build.getNumber());
        createBuild.result = build.getResult() == Result.SUCCESS ? CreateBuildRequest.Result.SUCCESS : CreateBuildRequest.Result.FAILURE;
        createBuild.timestamp = build.getStartTimeInMillis();
        createBuild.duration = build.getDuration();

        int index = fileName.indexOf(".");
        createBuild.fileExtension = index >= 0 ? fileName.substring(index + 1) : null;

        List<CreateBuildRequest.Commit> commits = new ArrayList<>();
        for (ChangeLogSet.Entry entry : build.getChangeSet()) {
            CreateBuildRequest.Commit commit = new CreateBuildRequest.Commit();
            commit.author = entry.getAuthor().getFullName();
            commit.email = entry.getAuthor().getProperty(Mailer.UserProperty.class).getAddress();
            commit.description = entry instanceof GitChangeSet ? ((GitChangeSet) entry).getComment() : entry.getMsg();
            commit.hash = entry.getCommitId();
            commit.timestamp = entry.getTimestamp();
            commits.add(commit);
        }
        createBuild.commits = commits;

        if (!getMetadata().isEmpty()) {
            createBuild.metadata = getMetadataMap();
        }

        OkHttpClient client = new OkHttpClient();
        ObjectMapper mapper = new ObjectMapper();

        RequestBody createBuildBody = RequestBody.create(mapper.writeValueAsBytes(createBuild));
        Request request = new Request.Builder()
                .url(this.url + "/v2/create")
                .header("Authorization", "Basic " + this.accessToken)
                .header("Content-Type", "application/json")
                .post(createBuildBody)
                .build();

        CreateBuildResponse createBuildResponse;
        try (Response response = client.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful()) {
                ErrorResponse errorResponse = mapper.readValue(body.bytes(), ErrorResponse.class);
                listener.getLogger().println("[" + response.code() + "] Failed to create build with body: " + errorResponse.error());
                return false;
            } else if (body == null) {
                listener.getLogger().println("[" + response.code() + "] Failed to create build with empty response body.");
                return false;
            }
            createBuildResponse = mapper.readValue(body.bytes(), CreateBuildResponse.class);
        }

        FilePath path = build.getWorkspace().child(this.fileName);
        if (build.getResult() != Result.SUCCESS) {
            listener.getLogger().println("Not uploading build file since build failed.");
            return true;
        }

        if (!path.exists()) {
            listener.getLogger().printf("(%s) File does not exist at '%s'. Did something break?%n", path.isRemote() ? "Remote" : "Local", path);
            return false;
        }

        byte[] file;
        try (InputStream in = path.read()) {
            file = in.readAllBytes();
        }

        RequestBody fileBody = RequestBody.create(file);
        MultipartBody multipartBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("stateKey", createBuildResponse.stateKey())
                .addFormDataPart("file", this.fileName, fileBody)
                .build();

        Request uploadFileRequest = new Request.Builder()
                .url(this.url + "/v2/create/upload")
                .header("Authorization", "Basic " + this.accessToken)
                .post(multipartBody)
                .build();

        try (Response response = client.newCall(uploadFileRequest).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful()) {
                listener.getLogger().println("[" + response.code() + "] Failed to upload file with error: " + body.string());
                return false;
            } else if (body == null) {
                listener.getLogger().println("[" + response.code() + "] Failed to upload file with empty response body.");
                return false;
            }
        }

        if (this.postScript != null) {
            Runtime.getRuntime().exec(String.format("/bin/sh -c %s", this.postScript));
        }

        listener.getLogger().println("Successfully uploaded this build to papyrus");
        return true;
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public String getDisplayName() {
            return "papyrus uploader";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
