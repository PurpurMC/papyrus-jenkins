package org.purpurmc.papyrus.jenkins;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.List;
import java.util.Map;

@SuppressFBWarnings({"URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"})
public class CreateBuildRequest {
    public String project;
    public String version;
    public String build;
    public Result result;
    public long timestamp;
    public long duration;
    public String fileExtension;
    public List<Commit> commits;
    public Map<String, String> metadata;

    public enum Result {
        SUCCESS,
        FAILURE
    }

    public static class Commit {
        public String author;
        public String email;
        public String description;
        public String hash;
        public long timestamp;
    }
}
