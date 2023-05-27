package org.purpurmc.papyrus.jenkins;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.scm.ChangeLogSet;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Mailer;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@SuppressFBWarnings({"NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE"})
public class UploadBuildNotifier extends Notifier {
    public final String url;
    public final String accessToken;
    public final String project;
    public final String version;
    public final String fileName;

    @DataBoundConstructor
    public UploadBuildNotifier(String url, String accessToken, String project, String version, String fileName) {
        this.accessToken = accessToken;
        this.project = project;
        this.version = version;
        this.fileName = fileName;

        url = url.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        this.url = url;
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
            commit.description = entry.getMsg();
            commit.hash = entry.getCommitId();
            commit.timestamp = entry.getTimestamp();
            commits.add(commit);
        }
        createBuild.commits = commits;

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
            if (!response.isSuccessful() || response.body() == null) {
                return false;
            }
            createBuildResponse = mapper.readValue(response.body().bytes(), CreateBuildResponse.class);
        }

        Path path = Paths.get(build.getWorkspace().toURI()).resolve(this.fileName);
        byte[] file = Files.readAllBytes(path);

        RequestBody fileBody = RequestBody.create(file);
        MultipartBody multipartBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("stateKey", createBuildResponse.stateKey)
                .addFormDataPart("file", this.fileName, fileBody)
                .build();

        Request uploadFileRequest = new Request.Builder()
                .url(this.url + "/v2/create/upload")
                .header("Authorization", "Basic " + this.accessToken)
                .post(multipartBody)
                .build();

        try (Response response = client.newCall(uploadFileRequest).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return false;
            }
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
