package org.purpurmc.papyrus.jenkins;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

// inspired by PodLabel from the Kubernetes Jenkins plugin
public class Metadata extends AbstractDescribableImpl<Metadata> implements Serializable {

    private static final long serialVersionUID = -7008393686530561739L;

    private String key;
    private String value;

    @DataBoundConstructor
    public Metadata(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    static Map<String, String> toMap(@NonNull Iterable<Metadata> metadatas) {
        Map<String, String> map = new HashMap<>();
        for (Metadata metadata : metadatas) {
            map.put(metadata.getKey(), metadata.getValue());
        }
        return Collections.unmodifiableMap(map);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Metadata that = (Metadata) o;
        return Objects.equals(this.key, that.getKey());
    }

    @Override
    public int hashCode() {
        return this.key != null ? this.key.hashCode() : 0;
    }

    @Extension
    @Symbol("buildMetadata")
    public static class DescriptorImpl extends Descriptor<Metadata> {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Initial Build Metadata";
        }
    }
}
