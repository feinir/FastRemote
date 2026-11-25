# FastRemote Privacy and AI Data Notice

Revision: 28 August 2026

FastRemote is a locally installed SSH/SFTP desktop application. This notice
describes the product's current data flows. A distributor must replace
“FastRemote Project” with its legal identity and add jurisdiction-specific
contact, retention, consumer-right, and cross-border-transfer information
before public sale.

## Local data

FastRemote stores connection profiles, host-key decisions, preferences,
non-secret catalogs, AI chat history, and diagnostic metadata in the user's
local application-data directory. Credentials and provider tokens managed by
FastRemote are stored through the encrypted credential vault. The vault does
not make a compromised, unlocked computer safe.

FastRemote does not intentionally record terminal input, private-key content,
passwords, passphrases, or plaintext API keys in diagnostic logs.

## Network and remote systems

When the user connects to a host, FastRemote sends connection and authentication
data to the host and any configured jump hosts. SFTP and terminal actions send
commands, paths, file contents, and protocol metadata to systems selected by
the user. Administrators of those systems may independently log the activity.

## AI providers and MCP servers

AI features are optional. When enabled, prompts, selected chat images, chat
history, model settings, and the bounded workspace context shown for the
request may be sent to the provider selected by the user. Tool calls may send
additional command, path, file, terminal, or session metadata to configured MCP
servers or model providers. Provider processing is governed by the provider's
terms and privacy notice.

FastRemote must not be used to submit personal data, credentials, confidential
information, regulated records, or third-party content unless the user has
authority and an appropriate agreement to do so. AI output may be inaccurate
or unsafe and must be reviewed before commands or file operations are approved.

ChatGPT account access, when available, is performed by a separately installed
official Codex component using its own isolated data directory. FastRemote does
not read the ChatGPT OAuth token, but prompts and request context are processed
under the terms applicable to the user's OpenAI account.

## Telemetry and updates

The current product does not include a FastRemote-operated telemetry, crash
reporting, cloud synchronization, account, or automatic-update service. If a
commercial distributor adds any such service, this notice and the in-product
consent and settings must be updated before collection begins.

## Deletion

Users can delete saved sessions, credentials, AI conversations, and other
catalog entries through the application. Uninstalling the application may not
delete the application-data directory or operating-system backups. Users must
delete those copies separately when required.
