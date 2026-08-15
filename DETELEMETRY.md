# UFI-TOOLS-DeTelemetry

This fork keeps the original MIT-licensed functionality while removing the
confirmed automatic telemetry paths from UFI-TOOLS.

## Removed automatic traffic

- The background device report containing a stable device identifier, model,
  firmware, app version, and root status.
- The logged-in Web UI request that sent the stable device identifier to the
  developer message service.
- Remote donation images that contacted the telemetry host when the page was
  opened.
- Automatic in-app update polling against the original distribution server.

The update button now checks this fork's latest GitHub Release only when the
user clicks it. CI supplies the fork repository name at build time. Local builds
without `-PdetelemetryRepository=owner/UFI-TOOLS-DeTelemetry` leave update
discovery disabled.

This is a narrowly scoped de-telemetry fork. It does not claim that every
optional network feature, plugin, or local management interface is private or
secure. Features such as SMS forwarding and plugin downloads still contact the
destinations explicitly configured or selected by the user.

## Automated upstream builds

`Upstream Sync` checks the upstream `http-server-version` branch every day. It
merges clean changes, preserves this fork's workflows and privacy guard, runs
the guard, compiles the app, and then publishes a signed GitHub Release. Merge
conflicts, a failed privacy check, or a failed build stop publication and create
an issue for review.

Configure these GitHub Actions secrets before enabling scheduled releases:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_PASSWORD`

The keystore must be backed up permanently. Android updates require every
release to use the same application ID and signing certificate. A build signed
with this fork's certificate cannot replace an upstream installation signed by
someone else; uninstall the upstream app before the first DeTelemetry install.

The original copyright and MIT license remain in `LICENCE`.
