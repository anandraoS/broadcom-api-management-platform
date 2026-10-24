package io.apiplatform.controlplane.service;

/** Published inside a transaction; consumers act only after commit (see SnapshotService). */
public record ConfigChanged(String reason) {}
