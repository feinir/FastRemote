# FastRemote RichTextFX fork

- Upstream: https://github.com/FXMisc/RichTextFX/tree/v0.11.7
- Source archive: https://codeload.github.com/FXMisc/RichTextFX/tar.gz/refs/tags/v0.11.7
- Archive SHA-256: `f32dd18395bf652c8181469b7680aed6b86c0e31fe9eea3ffb50dc9ca8c6b045`
- Baseline: 0.11.7; local artifact: 0.11.7-fastremote.1.
- License: BSD 2-Clause (see LICENSE). This source tree contains upstream main Java/resources,
  its original README/license, and FastRemote's regression tests. It is versioned in the
  FastRemote repository, without a second Git repository or a separately published artifact.
- Gradle recipe: `gradle/richtextfx.gradle.kts`; settings project `:richtextfx`.

Local changes add `GenericStyledArea.setTextSpacing(lineSpacing, letterSpacing)`.
Nonzero tracking splits rendered text on Unicode grapheme boundaries and adds presentation-only
spacing spans: non-breaking spaces for positive advance and empty native inline spans with
signed preferred width for negative advance. `TrackingMap` maps caret/range/hit/wrapped-line
offsets back to the unchanged document. Native TextFlow handles font fallback and wrapping.
Zero tracking retains the original styled-segment rendering. Paragraph virtualization remains;
a very long single paragraph creates more rendered nodes when tracking is enabled.

Spacing supports tenths of a logical pixel: -15.0–15.0 vertically and -5.0–5.0 horizontally.
The paragraph's line-spacing property is bound to its configured value so RichTextFX's author
stylesheet cannot overwrite it. Paragraph geometry disables pixel snapping; wrapped-line metrics
include signed gap advance and preserve each node’s UTF-16 endpoint when hit testing overlaps a gap or Unicode cluster. Listeners detach on cell disposal and font changes invalidate line
geometry. Custom bold selection and synthetic outlines were removed after performance feedback;
existing syntax/ANSI styles are unaffected. Spacing never edits text, document styles or undo history.

Run `./gradlew :richtextfx:test :fastremote-ui:test --tests '*EmbeddedTextEditorTest'` from
repository root. Upgrade by comparing a pinned upstream release against this baseline;
retain the offset-mapping, geometry, Unicode, virtualization and editing regression tests.
