// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Objects;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogEvent;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/** Defines the compact handoff from dialog content to the native button bar. */
public final class DialogSpacing {
    static final String CONTENT_TO_BUTTON_BAR = "content-to-button-bar-dialog";
    static final double MARGIN = 10.0;
    static final double GENERATED_TEXT_MAX_WIDTH = 520.0;
    static final double MINIMUM_AUTOMATIC_HEADER_WIDTH = 240.0;
    private static final Object INITIAL_INPUT_FOCUS_KEY = new Object();
    private static final Object PREPARED_DIALOG_KEY = new Object();
    private static final Object HEADER_TEXT_MAX_WIDTH_KEY = new Object();
    private static final Object AUTOMATIC_HEADER_TEXT_MAX_WIDTH_KEY = new Object();
    private static final Object HEADER_NATURAL_WIDTH_KEY = new Object();
    private static final Object HEADER_DYNAMIC_WIDTH_KEY = new Object();
    private static final Object RESIZABLE_WINDOW_WIDTH_KEY = new Object();
    private static final Object RESIZABLE_WINDOW_HEIGHT_KEY = new Object();
    private static final Object RESIZABLE_MINIMUM_WIDTH_KEY = new Object();
    private static final Object RESIZABLE_MINIMUM_HEIGHT_KEY = new Object();
    private static final Object RESIZABLE_STAGE_MINIMUM_APPLIED_KEY = new Object();
    private static final Object FIXED_BUTTON_WIDTH_KEY = new Object();
    private static final Object ESCAPE_MENU_DISPATCH_INSTALLED_KEY = new Object();
    private static final String COMPACT_BUTTONS = "compact-dialog-buttons";

    private DialogSpacing() {}

    static void contentToButtonBar(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        prepare(dialog);
        if (!pane.getStyleClass().contains(CONTENT_TO_BUTTON_BAR)) {
            pane.getStyleClass().add(CONTENT_TO_BUTTON_BAR);
        }
    }

    static void contentToButtonBar(Dialog<?> dialog, double headerTextMaxWidth) {
        prepare(dialog, headerTextMaxWidth);
        DialogPane pane = dialog.getDialogPane();
        if (!pane.getStyleClass().contains(CONTENT_TO_BUTTON_BAR)) {
            pane.getStyleClass().add(CONTENT_TO_BUTTON_BAR);
        }
    }

    public static void prepare(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        style(pane);
        if (pane.getProperties().putIfAbsent(PREPARED_DIALOG_KEY, Boolean.TRUE) == null) {
            LinuxDialogModality.prepare(dialog);
            if (dialog instanceof javafx.scene.control.TextInputDialog) focusFirstInputOnShown(dialog);
            pane.contentProperty().addListener((ignored, previous, content) ->
                    rememberContentPreferredWidth(pane, content));
            rememberContentPreferredWidth(pane, pane.getContent());
            dialog.addEventHandler(DialogEvent.DIALOG_SHOWING, ignored -> prepareBeforeReveal(pane));
            dialog.addEventHandler(DialogEvent.DIALOG_SHOWN, ignored -> revealAfterFinalLayout(dialog, pane));
        }
    }

    /** Opt-in for editing forms; library/search dialogs retain their own focus policy. */
    static void focusFirstInputOnShown(Dialog<?> dialog) {
        if (dialog.getDialogPane().getProperties().putIfAbsent(INITIAL_INPUT_FOCUS_KEY, Boolean.TRUE) != null) return;
        dialog.addEventHandler(DialogEvent.DIALOG_SHOWN, ignored -> Platform.runLater(() -> {
            if (!dialog.isShowing()) return;
            Node content = dialog instanceof javafx.scene.control.TextInputDialog prompt
                    ? prompt.getEditor() : dialog.getDialogPane().getContent();
            javafx.scene.control.TextInputControl input = firstEditableInput(content);
            if (input != null) focusAtEnd(input);
        }));
    }

    static void focusAtEnd(javafx.scene.control.TextInputControl input) {
        input.requestFocus();
        input.positionCaret(input.getLength());
    }

    private static javafx.scene.control.TextInputControl firstEditableInput(Node node) {
        if (node == null || !node.isVisible() || node.isDisabled()) return null;
        if (node instanceof javafx.scene.control.TextInputControl input
                && input.isEditable() && input.isFocusTraversable()) return input;
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                var input = firstEditableInput(child);
                if (input != null) return input;
            }
        }
        return null;
    }

    static void prepare(Dialog<?> dialog, double headerTextMaxWidth) {
        if (!Double.isFinite(headerTextMaxWidth) || headerTextMaxWidth <= 0.0) {
            throw new IllegalArgumentException("headerTextMaxWidth must be finite and positive");
        }
        dialog.getDialogPane().getProperties().put(HEADER_TEXT_MAX_WIDTH_KEY, headerTextMaxWidth);
        prepare(dialog);
    }

    private static void prepareBeforeReveal(DialogPane pane) {
        localizeStandardButtons(pane);
        Window window = pane.getScene() == null ? null : pane.getScene().getWindow();
        if (window != null) {
            window.setOpacity(0.0);
        }
        ThemeManager.prepare(pane);
        wrapGeneratedContentText(pane);
        wrapHeaderText(pane);
        fitButtonsToContent(pane);
    }

    private static void revealAfterFinalLayout(Dialog<?> dialog, DialogPane pane) {
        ThemeManager.prepare(pane);
        Window window = pane.getScene() == null ? null : pane.getScene().getWindow();
        if (window == null) return;
        Platform.runLater(() -> {
            if (window.isShowing()) {
                fitButtonsToContent(pane);
                if (dialog.isResizable() && window instanceof Stage stage) {
                    applyResizableWindowGeometry(stage, pane, false);
                    // GTK can report zero decorations during DIALOG_SHOWN and install the real
                    // server-side frame at the next pulse. Re-measure once so configured content
                    // geometry remains content geometry rather than accidentally becoming the
                    // outer-window size.
                    // GTK applies server-side decorations asynchronously. Reveal after its first
                    // stable frame. The DialogPane retains the initial content minimum until then,
                    // while the Stage advertises its final user-resizable minimum from the start;
                    // changing the Stage minimum after reveal makes GTK resize the window itself.
                    PauseTransition afterNativeFrame = new PauseTransition(Duration.millis(50));
                    afterNativeFrame.setOnFinished(ignored -> {
                        if (!window.isShowing()) return;
                        applyResizableWindowGeometry(stage, pane, false);
                        centerOnOwner(window, dialog.getOwner());
                        window.setOpacity(1.0);
                        PauseTransition relaxAfterPlacement =
                                new PauseTransition(Duration.millis(500));
                        relaxAfterPlacement.setOnFinished(relaxIgnored -> {
                            if (window.isShowing()) {
                                applyResizableWindowGeometry(stage, pane, true);
                            }
                        });
                        relaxAfterPlacement.play();
                    });
                    afterNativeFrame.play();
                } else {
                    centerOnOwner(window, dialog.getOwner());
                    window.setOpacity(1.0);
                }
                // A fixed dialog has already been sized from the bounded header width installed
                // before DIALOG_SHOWING. On GTK the header graphic can briefly retain its
                // pre-layout x-coordinate during DIALOG_SHOWN; binding the label width to that
                // transient coordinate creates a feedback loop that collapses the label to a
                // few glyphs per line without giving the native window another height pass.
                // Only resizable dialogs need their generated header to track later width changes.
                if (dialog.isResizable()) enableDynamicHeaderWidth(pane);
            }
        });
    }

    /** Applies the same owner-relative placement to standalone dialog stages. */
    public static void centerOnOwnerWhenShown(Stage stage) {
        stage.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWING, ignored -> stage.setOpacity(0.0));
        stage.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWN, ignored -> Platform.runLater(() -> {
            if (!stage.isShowing()) return;
            centerOnOwner(stage, stage.getOwner());
            stage.setOpacity(1.0);
        }));
    }

    private static void centerOnOwner(Window window, Window owner) {
        Window reference = owner != null && owner.isShowing() ? owner : window;
        var bounds = new javafx.geometry.Rectangle2D(reference.getX(), reference.getY(),
                reference.getWidth(), reference.getHeight());
        var screens = javafx.stage.Screen.getScreensForRectangle(bounds);
        var screen = screens.stream().max(java.util.Comparator.comparingDouble(candidate -> {
            var b = candidate.getVisualBounds();
            return Math.max(0, Math.min(b.getMaxX(), bounds.getMaxX()) - Math.max(b.getMinX(), bounds.getMinX()))
                    * Math.max(0, Math.min(b.getMaxY(), bounds.getMaxY()) - Math.max(b.getMinY(), bounds.getMinY()));
        })).orElse(javafx.stage.Screen.getPrimary()).getVisualBounds();
        var position = centeredPosition(owner != null && owner.isShowing() ? bounds : screen,
                screen, window.getWidth(), window.getHeight());
        window.setX(position.getX());
        window.setY(position.getY());
    }

    static javafx.geometry.Point2D centeredPosition(javafx.geometry.Rectangle2D owner,
            javafx.geometry.Rectangle2D screen, double width, double height) {
        double x = owner.getMinX() + (owner.getWidth() - width) / 2.0;
        double y = owner.getMinY() + (owner.getHeight() - height) / 2.0;
        return new javafx.geometry.Point2D(
                Math.max(screen.getMinX(), Math.min(x, screen.getMaxX() - width)),
                Math.max(screen.getMinY(), Math.min(y, screen.getMaxY() - height)));
    }

    private static void applyResizableWindowGeometry(
            Stage stage, DialogPane pane, boolean relaxContentMinimum) {
        Double initialContentWidth = configuredResizableWindowWidth(pane);
        Double initialContentHeight = configuredResizableWindowHeight(pane);
        Double minimumContentWidth = configuredResizableMinimumWidth(pane);
        Double minimumContentHeight = configuredResizableMinimumHeight(pane);
        double sceneWidth = stage.getScene() == null ? 0.0 : stage.getScene().getWidth();
        double sceneHeight = stage.getScene() == null ? 0.0 : stage.getScene().getHeight();
        double decorationWidth = windowDecorationExtent(stage.getWidth(), sceneWidth, pane.getWidth());
        double decorationHeight = windowDecorationExtent(stage.getHeight(), sceneHeight, pane.getHeight());
        if (initialContentWidth != null) {
            stage.setWidth(Math.max(stage.getWidth(), initialContentWidth + decorationWidth));
        }
        if (initialContentHeight != null) {
            stage.setHeight(Math.max(stage.getHeight(), initialContentHeight + decorationHeight));
        }
        if (pane.getProperties().get(RESIZABLE_STAGE_MINIMUM_APPLIED_KEY) != stage) {
            stage.setMinWidth(minimumContentWidth == null
                    ? resizableMinimumWidth(stage.getMinWidth(), stage.getWidth())
                    : minimumContentWidth + decorationWidth);
            if (minimumContentHeight != null) {
                stage.setMinHeight(minimumContentHeight + decorationHeight);
            }
            pane.getProperties().put(RESIZABLE_STAGE_MINIMUM_APPLIED_KEY, stage);
        }
        boolean minimumRelaxed = false;
        if (relaxContentMinimum && initialContentWidth != null && minimumContentWidth != null
                && minimumContentWidth < initialContentWidth) {
            pane.setMinWidth(minimumContentWidth);
            minimumRelaxed = true;
        }
        if (relaxContentMinimum && initialContentHeight != null && minimumContentHeight != null
                && minimumContentHeight < initialContentHeight) {
            pane.setMinHeight(minimumContentHeight);
            minimumRelaxed = true;
        }
        if (minimumRelaxed) {
            // GTK may cap the initial outer window to a small HiDPI work area. Relaxing the
            // DialogPane minimum then changes the available layout contract, but it does not
            // reliably schedule another layout pulse by itself. Without this request, the old
            // taller content layout can keep the button bar below the Scene until any later user
            // action happens to invalidate layout.
            pane.requestLayout();
        }
    }

    /**
     * Returns the native frame extent without trusting an oversized or clipped DialogPane.
     * GTK reports the Stage as an outer-window size while Scene is the client area; during its
     * first HiDPI layout the pane can temporarily retain the requested size even though the
     * Scene is already shorter by the title bar. Using the pane in that state loses the native
     * frame height and makes the bottom of the dialog appear clipped until another layout pass.
     */
    static double windowDecorationExtent(double windowExtent, double sceneExtent, double paneExtent) {
        double clientExtent = Double.isFinite(sceneExtent) && sceneExtent > 0.0
                ? sceneExtent
                : paneExtent;
        if (!Double.isFinite(windowExtent) || !Double.isFinite(clientExtent)) return 0.0;
        return Math.max(0.0, windowExtent - clientExtent);
    }

    /** Fixes a resizable dialog's initial and minimum width before the native window is positioned. */
    static void configureResizableWindowWidth(Dialog<?> dialog, double width) {
        if (!Double.isFinite(width) || width <= 0.0) {
            throw new IllegalArgumentException("width must be finite and positive");
        }
        DialogPane pane = dialog.getDialogPane();
        pane.setPrefWidth(width);
        pane.setMinWidth(width);
        pane.getProperties().put(RESIZABLE_WINDOW_WIDTH_KEY, width);
        pane.getProperties().put(RESIZABLE_MINIMUM_WIDTH_KEY, width);
    }

    /**
     * Defines a dialog's first visible content width before Linux/Windows window managers measure
     * generated wrapping labels. Height continues to follow the dialog's natural content height.
     */
    public static void configureInitialWindowWidth(
            Dialog<?> dialog, double initialWidth, double minimumWidth) {
        requirePositiveFinite("initialWidth", initialWidth);
        requirePositiveFinite("minimumWidth", minimumWidth);
        if (minimumWidth > initialWidth) {
            throw new IllegalArgumentException("minimum dialog width cannot exceed initial width");
        }
        DialogPane pane = dialog.getDialogPane();
        pane.setPrefWidth(initialWidth);
        pane.setMinWidth(minimumWidth);
        pane.getProperties().put(RESIZABLE_WINDOW_WIDTH_KEY, initialWidth);
        pane.getProperties().put(RESIZABLE_MINIMUM_WIDTH_KEY, minimumWidth);
    }

    /**
     * Expands a visible resizable dialog when newly revealed wrapping content needs more height.
     * Existing user width and any larger user-selected height are preserved.
     */
    static void growHeightToContent(Dialog<?> dialog) {
        Objects.requireNonNull(dialog, "dialog");
        Platform.runLater(() -> {
            DialogPane pane = dialog.getDialogPane();
            pane.applyCss();
            pane.layout();
            if (pane.getScene() == null || !(pane.getScene().getWindow() instanceof Stage stage)
                    || !stage.isShowing()) {
                pane.requestLayout();
                return;
            }
            // A Scene root can grow to its minimum layout height without resizing the native
            // window. Compare with the visible client area, not that potentially clipped root.
            double clientWidth = pane.getScene().getWidth();
            double clientHeight = pane.getScene().getHeight();
            double preferredContentHeight = Math.ceil(pane.prefHeight(clientWidth));
            if (!Double.isFinite(preferredContentHeight) || preferredContentHeight <= clientHeight) {
                pane.requestLayout();
                return;
            }
            double decorationHeight = windowDecorationExtent(stage.getHeight(), clientHeight, pane.getHeight());
            stage.setHeight(Math.max(stage.getHeight(), preferredContentHeight + decorationHeight));
        });
    }

    /** Keeps a wrapping label from being compressed back to a one-line ellipsis. */
    static void configureWrappingLabel(Label label) {
        Objects.requireNonNull(label, "label");
        label.setWrapText(true);
        label.setMinWidth(0.0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(Region.USE_PREF_SIZE);
    }

    /** Fixes both dimensions before Linux/Windows window managers perform their first placement. */
    static void configureResizableWindowSize(Dialog<?> dialog, double width, double height) {
        configureResizableWindowWidth(dialog, width);
        if (!Double.isFinite(height) || height <= 0.0) {
            throw new IllegalArgumentException("height must be finite and positive");
        }
        DialogPane pane = dialog.getDialogPane();
        pane.setPrefHeight(height);
        pane.setMinHeight(height);
        pane.getProperties().put(RESIZABLE_WINDOW_HEIGHT_KEY, height);
        pane.getProperties().put(RESIZABLE_MINIMUM_HEIGHT_KEY, height);
    }

    /** Fixes a non-resizable dialog's content geometry before its first native layout. */
    static void configureFixedWindowSize(Dialog<?> dialog, double width, double height) {
        Objects.requireNonNull(dialog, "dialog");
        requirePositiveFinite("width", width);
        requirePositiveFinite("height", height);
        DialogPane pane = dialog.getDialogPane();
        pane.setMinSize(width, height);
        pane.setPrefSize(width, height);
        pane.setMaxSize(width, height);
        pane.getProperties().put(RESIZABLE_WINDOW_WIDTH_KEY, width);
        pane.getProperties().put(RESIZABLE_WINDOW_HEIGHT_KEY, height);
        pane.getProperties().put(RESIZABLE_MINIMUM_WIDTH_KEY, width);
        pane.getProperties().put(RESIZABLE_MINIMUM_HEIGHT_KEY, height);
    }

    /**
     * Defines a resizable dialog's first visible content size independently from the smallest
     * content size accepted after the user starts resizing it.
     */
    public static void configureResizableWindowGeometry(
            Dialog<?> dialog,
            double initialWidth,
            double initialHeight,
            double minimumWidth,
            double minimumHeight) {
        requirePositiveFinite("initialWidth", initialWidth);
        requirePositiveFinite("initialHeight", initialHeight);
        requirePositiveFinite("minimumWidth", minimumWidth);
        requirePositiveFinite("minimumHeight", minimumHeight);
        if (minimumWidth > initialWidth || minimumHeight > initialHeight) {
            throw new IllegalArgumentException("minimum dialog size cannot exceed initial size");
        }
        DialogPane pane = dialog.getDialogPane();
        pane.setPrefSize(initialWidth, initialHeight);
        // Keep the first native layout at the requested content size. Once the Stage has
        // incorporated its platform decorations, revealAfterFinalLayout relaxes these values
        // to the user-resizable minimums recorded below.
        pane.setMinSize(initialWidth, initialHeight);
        pane.getProperties().put(RESIZABLE_WINDOW_WIDTH_KEY, initialWidth);
        pane.getProperties().put(RESIZABLE_WINDOW_HEIGHT_KEY, initialHeight);
        pane.getProperties().put(RESIZABLE_MINIMUM_WIDTH_KEY, minimumWidth);
        pane.getProperties().put(RESIZABLE_MINIMUM_HEIGHT_KEY, minimumHeight);
    }

    private static void requirePositiveFinite(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    static Double configuredResizableWindowWidth(DialogPane pane) {
        Object configured = pane.getProperties().get(RESIZABLE_WINDOW_WIDTH_KEY);
        return configured instanceof Number number ? number.doubleValue() : null;
    }

    static Double configuredResizableWindowHeight(DialogPane pane) {
        Object configured = pane.getProperties().get(RESIZABLE_WINDOW_HEIGHT_KEY);
        return configured instanceof Number number ? number.doubleValue() : null;
    }

    static Double configuredResizableMinimumWidth(DialogPane pane) {
        Object configured = pane.getProperties().get(RESIZABLE_MINIMUM_WIDTH_KEY);
        return configured instanceof Number number ? number.doubleValue() : null;
    }

    static Double configuredResizableMinimumHeight(DialogPane pane) {
        Object configured = pane.getProperties().get(RESIZABLE_MINIMUM_HEIGHT_KEY);
        return configured instanceof Number number ? number.doubleValue() : null;
    }

    /** Keeps an explicit dialog minimum instead of replacing it with the first laid-out width. */
    static double resizableMinimumWidth(double configuredMinimumWidth, double laidOutWidth) {
        if (Double.isFinite(configuredMinimumWidth) && configuredMinimumWidth > 0.0) {
            return configuredMinimumWidth;
        }
        return Math.ceil(laidOutWidth);
    }

    static void style(DialogPane pane) {
        if (!pane.getStyleClass().contains("iconless-dialog")) {
            pane.getStyleClass().add("iconless-dialog");
        }
        pane.setGraphic(null);
        ThemeManager.style(pane);
    }

    /** JavaFX caches its standard ButtonType labels using the JVM startup locale; replace only those labels. */
    static void localizeStandardButtons(DialogPane pane) {
        for (ButtonType type : pane.getButtonTypes()) {
            String key = standardButtonKey(type);
            Node node = key == null ? null : pane.lookupButton(type);
            if (node instanceof ButtonBase button) button.setText(com.ynpe.fastremote.i18n.Texts.text(key));
        }
    }

    /** Keeps translated action labels intact and grows the dialog only after compact spacing is exhausted. */
    static void fitButtonsToContent(DialogPane pane) {
        pane.applyCss();
        for (ButtonType type : pane.getButtonTypes()) {
            Node node = pane.lookupButton(type);
            if (node instanceof ButtonBase button) {
                Double fixedWidth = fixedButtonWidth(button);
                if (fixedWidth == null) {
                    button.setMinWidth(Region.USE_PREF_SIZE);
                    button.setPrefWidth(Region.USE_COMPUTED_SIZE);
                } else {
                    button.setMinWidth(fixedWidth);
                    button.setPrefWidth(fixedWidth);
                    button.setMaxWidth(fixedWidth);
                }
            }
        }
        Node node = pane.lookup(".button-bar");
        if (!(node instanceof ButtonBar buttonBar)) return;
        buttonBar.setButtonMinWidth(Region.USE_COMPUTED_SIZE);
        pane.applyCss();

        double available = effectiveDialogWidth(pane);
        double required = buttonBar.prefWidth(-1);
        if (required > available && !pane.getStyleClass().contains(COMPACT_BUTTONS)) {
            pane.getStyleClass().add(COMPACT_BUTTONS);
            pane.applyCss();
            required = buttonBar.prefWidth(-1);
        }
        if (required > available) pane.setPrefWidth(Math.ceil(required));
    }

    static void configureFixedButtonWidth(Node button, double width) {
        Objects.requireNonNull(button, "button");
        requirePositiveFinite("button width", width);
        button.getProperties().put(FIXED_BUTTON_WIDTH_KEY, width);
        if (button instanceof Region region) {
            region.setMinWidth(width);
            region.setPrefWidth(width);
            region.setMaxWidth(width);
        }
    }

    private static Double fixedButtonWidth(Node button) {
        Object value = button.getProperties().get(FIXED_BUTTON_WIDTH_KEY);
        return value instanceof Double width ? width : null;
    }

    /** Constrains JavaFX's generated header label so translated explanations wrap instead of widening the window. */
    static void wrapHeaderText(DialogPane pane) {
        if (pane.getHeader() != null || pane.getHeaderText() == null || pane.getHeaderText().isBlank()) return;
        pane.applyCss();
        Node headerPanel = pane.lookup(".header-panel");
        Node node = headerPanel == null ? null : headerPanel.lookup(".label");
        if (node instanceof Label label) configureHeaderLabel(label, headerTextMaxWidth(pane));
    }

    private static void wrapGeneratedContentText(DialogPane pane) {
        if (pane.getContent() != null || pane.getContentText() == null || pane.getContentText().isBlank()) return;
        pane.applyCss();
        Node node = pane.lookup(".content.label");
        if (node instanceof Label label) configureHeaderLabel(label, GENERATED_TEXT_MAX_WIDTH);
    }

    static void configureHeaderLabel(Label label, double maximumWidth) {
        double naturalWidth = label.prefWidth(-1.0);
        label.getProperties().put(HEADER_NATURAL_WIDTH_KEY, naturalWidth);
        label.setWrapText(true);
        label.setMinWidth(0.0);
        label.setPrefWidth(Math.min(naturalWidth, maximumWidth));
        label.setMaxWidth(maximumWidth);
        label.setMaxHeight(Double.MAX_VALUE);
    }

    /**
     * Lets a generated header reflow with a resizable dialog after the content-controlled minimum size is known.
     * The fixed width used before the first reveal prevents the header from determining that minimum width.
     */
    static void enableDynamicHeaderWidth(DialogPane pane) {
        if (pane.getHeader() != null || pane.getHeaderText() == null || pane.getHeaderText().isBlank()) return;
        pane.applyCss();
        Node headerPanel = pane.lookup(".header-panel");
        Node node = headerPanel == null ? null : headerPanel.lookup(".label");
        if (!(node instanceof Label label) || label.getProperties().containsKey(HEADER_DYNAMIC_WIDTH_KEY)) return;
        Parent parent = label.getParent();
        if (!(parent instanceof Region region)) return;
        double fallbackWidth = headerTextMaxWidth(pane);
        double naturalWidth = label.getProperties().get(HEADER_NATURAL_WIDTH_KEY) instanceof Number number
                ? number.doubleValue() : label.prefWidth(-1.0);
        DoubleBinding availableWidth = Bindings.createDoubleBinding(
                () -> availableHeaderTextWidth(region, label, fallbackWidth),
                pane.widthProperty(), headerPanel.layoutBoundsProperty(), region.layoutBoundsProperty());
        DoubleBinding preferredWidth = Bindings.createDoubleBinding(
                () -> Math.min(naturalWidth, availableWidth.get()), availableWidth);
        label.prefWidthProperty().unbind();
        label.maxWidthProperty().unbind();
        label.prefWidthProperty().bind(preferredWidth);
        label.maxWidthProperty().bind(availableWidth);
        label.getProperties().put(HEADER_DYNAMIC_WIDTH_KEY, availableWidth);
    }

    static double availableHeaderTextWidth(Region parent, Label label, double fallbackWidth) {
        double parentWidth = parent.getWidth();
        if (!Double.isFinite(parentWidth) || parentWidth <= 0.0) return fallbackWidth;
        Bounds labelBounds = label.getBoundsInParent();
        double left = Math.max(parent.getInsets().getLeft(), labelBounds.getMinX());
        double right = parentWidth - parent.getInsets().getRight();
        if (parent instanceof Pane pane) {
            for (Node sibling : pane.getChildren()) {
                if (sibling == label || !sibling.isManaged() || !sibling.isVisible()) continue;
                Bounds bounds = sibling.getBoundsInParent();
                if (bounds.getMinX() > left) right = Math.min(right, bounds.getMinX());
            }
        }
        double available = right - left;
        return Double.isFinite(available) && available > 0.0 ? available : fallbackWidth;
    }

    /** Builds explanatory dialog content whose width is controlled by the form below it. */
    static Label wrappingContentLabel(String text, double width) {
        if (!Double.isFinite(width) || width <= 0.0) {
            throw new IllegalArgumentException("width must be finite and positive");
        }
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinWidth(0.0);
        label.setPrefWidth(width);
        label.setMaxWidth(width);
        label.setMaxHeight(Double.MAX_VALUE);
        return label;
    }

    static double headerTextMaxWidth(DialogPane pane) {
        Object configured = pane.getProperties().get(HEADER_TEXT_MAX_WIDTH_KEY);
        if (configured instanceof Number number) return number.doubleValue();
        Object automatic = pane.getProperties().get(AUTOMATIC_HEADER_TEXT_MAX_WIDTH_KEY);
        if (automatic instanceof Number number) return number.doubleValue();
        Node content = pane.getContent();
        if (content != null) {
            double preferred = content.prefWidth(-1.0);
            if (Double.isFinite(preferred) && preferred > 0.0) {
                return Math.max(MINIMUM_AUTOMATIC_HEADER_WIDTH, preferred);
            }
        }
        Node generatedContent = pane.lookup(".content.label");
        if (generatedContent instanceof Label label) {
            double preferred = label.prefWidth(-1.0);
            if (Double.isFinite(preferred) && preferred > 0.0) {
                return Math.max(MINIMUM_AUTOMATIC_HEADER_WIDTH,
                        Math.min(preferred, GENERATED_TEXT_MAX_WIDTH));
            }
        }
        return GENERATED_TEXT_MAX_WIDTH;
    }

    /** Captures the content's intrinsic width before a long generated header can stretch the whole dialog. */
    private static void rememberContentPreferredWidth(DialogPane pane, Node content) {
        if (pane.getProperties().containsKey(HEADER_TEXT_MAX_WIDTH_KEY) || content == null) return;
        double preferred = content.prefWidth(-1.0);
        if (Double.isFinite(preferred) && preferred > 0.0) {
            pane.getProperties().put(AUTOMATIC_HEADER_TEXT_MAX_WIDTH_KEY,
                    Math.max(MINIMUM_AUTOMATIC_HEADER_WIDTH, preferred));
        }
    }

    static double effectiveDialogWidth(DialogPane pane) {
        if (pane.getWidth() > 0.0) return pane.getWidth();
        if (pane.getPrefWidth() > 0.0 && pane.getPrefWidth() != Region.USE_COMPUTED_SIZE) {
            return pane.getPrefWidth();
        }
        return pane.prefWidth(-1);
    }

    static String standardButtonKey(ButtonType type) {
        if (type == ButtonType.OK) return "dialog.ok";
        if (type == ButtonType.CANCEL) return "dialog.cancel";
        if (type == ButtonType.CLOSE) return "dialog.close";
        if (type == ButtonType.YES) return "dialog.yes";
        if (type == ButtonType.NO) return "dialog.no";
        if (type == ButtonType.APPLY) return "dialog.apply";
        if (type == ButtonType.FINISH) return "dialog.finish";
        if (type == ButtonType.NEXT) return "dialog.next";
        if (type == ButtonType.PREVIOUS) return "dialog.previous";
        return null;
    }

    static AnchorPane anchoredContent(Node content) {
        AnchorPane root = new AnchorPane(content);
        AnchorPane.setTopAnchor(content, MARGIN);
        AnchorPane.setLeftAnchor(content, MARGIN);
        AnchorPane.setRightAnchor(content, MARGIN);
        AnchorPane.setBottomAnchor(content, 0.0);
        return root;
    }

    /** List/tree skins consume Escape even when they have no edit to cancel. */
    static void closeOnEscape(Dialog<?> dialog, Node content) {
        DialogPane pane = dialog.getDialogPane();
        Window window = pane.getScene().getWindow();
        if (pane.getProperties().putIfAbsent(ESCAPE_MENU_DISPATCH_INSTALLED_KEY, Boolean.TRUE) == null) {
            var original = window.getEventDispatcher();
            // PopupWindow redirects Escape before Scene filters. Handle an open menu first,
            // so its hide does not leave the same key available to close the owner dialog.
            window.setEventDispatcher((event, tail) -> {
                var menu = ApplicationContextMenus.activeMenu();
                if (event instanceof KeyEvent key && plainEscape(key) && menu != null
                        && menu.isShowing() && menu.getOwnerWindow() == window) {
                    menu.hide();
                    key.consume();
                    return null;
                }
                return original.dispatchEvent(event, tail);
            });
        }
        dialog.getDialogPane().getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (plainEscape(event)) {
                Node target = event.getTarget() instanceof Node node ? node : null;
                while (target != null && target != content) target = target.getParent();
                if (target == null) return;
                var menu = ApplicationContextMenus.activeMenu();
                if (menu != null && menu.isShowing()
                        && menu.getOwnerWindow() == dialog.getDialogPane().getScene().getWindow()) menu.hide();
                else dialog.close();
                event.consume();
            }
        });
    }

    private static boolean plainEscape(KeyEvent event) {
        return event.getEventType() == KeyEvent.KEY_PRESSED && event.getCode() == KeyCode.ESCAPE
                && !event.isConsumed() && !event.isAltDown() && !event.isControlDown()
                && !event.isMetaDown() && !event.isShiftDown();
    }
}
