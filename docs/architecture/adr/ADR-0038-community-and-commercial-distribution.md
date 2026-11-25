# ADR-0038: FastRemote desktop distribution editions

Status: Accepted on 2026-11-25.

The shared Java desktop modules retain the same feature set across the
community and commercial builds. The community source is released under MPL 2.0.
Vendored third-party code, fonts, and artwork retain their upstream terms.
The public repository starts from an audited desktop source snapshot. The
private repository history, operational files, licenses, and production signing
inputs stay in their existing private environments.

Gradle records the selected edition in the application resources. A checkout
without the private Go Agent sources defaults to the community edition. The
commercial edition requires those sources and continues to use the Go Agent,
the signed integrity manifest, and the original YNPE update service. The
community edition provides local capabilities through the same Java interface
and uses its own YNPE update manifest route. Both editions use the FastRemote
name, installer identity, and standard data location. User consent and operational
permissions for the vault, SSH, AI, and MCP remain governed by the desktop code.

The Mac App Store edition reuses the public desktop source, product identifier,
and local capabilities. It uses sandbox entitlements and receives distribution
updates through the store. Official YNPE and App Store
packages use the signing identities for their respective distribution channels.
