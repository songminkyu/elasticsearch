/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */
package org.elasticsearch.cluster;

import org.elasticsearch.TransportVersion;
import org.elasticsearch.TransportVersions;
import org.elasticsearch.cluster.metadata.ProjectId;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.collect.Iterators;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.repositories.RepositoryOperation;
import org.elasticsearch.xcontent.ToXContent;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static org.elasticsearch.TransportVersions.PROJECT_ID_IN_SNAPSHOTS_DELETIONS_AND_REPO_CLEANUP;

/**
 * A repository cleanup request entry. Part of the cluster state.
 */
public final class RepositoryCleanupInProgress extends AbstractNamedDiffable<ClusterState.Custom> implements ClusterState.Custom {

    public static final RepositoryCleanupInProgress EMPTY = new RepositoryCleanupInProgress(List.of());

    public static final String TYPE = "repository_cleanup";

    private final List<Entry> entries;

    public static RepositoryCleanupInProgress get(ClusterState state) {
        return state.custom(TYPE, EMPTY);
    }

    public RepositoryCleanupInProgress(List<Entry> entries) {
        this.entries = entries;
    }

    RepositoryCleanupInProgress(StreamInput in) throws IOException {
        this.entries = in.readCollectionAsList(Entry::readFrom);
    }

    public static NamedDiff<ClusterState.Custom> readDiffFrom(StreamInput in) throws IOException {
        return readDiffFrom(ClusterState.Custom.class, TYPE, in);
    }

    public static Entry startedEntry(ProjectId projectId, String repository, long repositoryStateId) {
        return new Entry(projectId, repository, repositoryStateId);
    }

    public boolean hasCleanupInProgress() {
        // TODO: Should we allow parallelism across repositories here maybe?
        return entries.isEmpty() == false;
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    @Override
    public String getWriteableName() {
        return TYPE;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeCollection(entries);
    }

    @Override
    public Iterator<? extends ToXContent> toXContentChunked(ToXContent.Params ignored) {
        return Iterators.concat(
            Iterators.single((builder, params) -> builder.startArray(TYPE)),
            Iterators.map(entries.iterator(), entry -> (builder, params) -> {
                builder.startObject();
                builder.field("repository", entry.repository);
                builder.endObject();
                return builder;
            }),
            Iterators.single((builder, params) -> builder.endArray())
        );
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        RepositoryCleanupInProgress that = (RepositoryCleanupInProgress) o;
        return Objects.equals(entries, that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(entries);
    }

    @Override
    public String toString() {
        return Strings.toString(this);
    }

    @Override
    public TransportVersion getMinimalSupportedVersion() {
        return TransportVersions.ZERO;
    }

    public record Entry(ProjectId projectId, String repository, long repositoryStateId) implements Writeable, RepositoryOperation {

        public static Entry readFrom(StreamInput in) throws IOException {
            final ProjectId projectId = in.getTransportVersion().onOrAfter(PROJECT_ID_IN_SNAPSHOTS_DELETIONS_AND_REPO_CLEANUP)
                ? ProjectId.readFrom(in)
                : ProjectId.DEFAULT;
            return new Entry(projectId, in.readString(), in.readLong());
        }

        @Override
        public long repositoryStateId() {
            return repositoryStateId;
        }

        @Override
        public ProjectId projectId() {
            return projectId;
        }

        @Override
        public String repository() {
            return repository;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            if (out.getTransportVersion().onOrAfter(PROJECT_ID_IN_SNAPSHOTS_DELETIONS_AND_REPO_CLEANUP)) {
                projectId.writeTo(out);
            } else {
                if (ProjectId.DEFAULT.equals(projectId) == false) {
                    final var message = "Cannot write repository cleanup entry with non-default project id "
                        + projectId
                        + " to version before "
                        + PROJECT_ID_IN_SNAPSHOTS_DELETIONS_AND_REPO_CLEANUP;
                    assert false : message;
                    throw new IllegalStateException(message);
                }
            }
            out.writeString(repository);
            out.writeLong(repositoryStateId);
        }
    }
}
