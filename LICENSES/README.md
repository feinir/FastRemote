# FastRemote license bundle

This directory contains license texts that are not already embedded in a
dependency archive. The release build expands this directory into a complete
`legal/LICENSES` bundle and also collects:

- the selected JDK runtime's complete `legal` directory;
- the Apache License selected for the bundled JediTermFX fork;
- the complete font and icon notices shipped in project resources; and
- every license or notice file from the installed JavaScript dependency tree.

`THIRD_PARTY_NOTICES.txt` is the authoritative component-to-license map for the
release baseline. Files generated under `build/` must not be edited manually.

The FastRemote proprietary license is at `../LICENSE`; it does not replace or
restrict the licenses in this directory.
