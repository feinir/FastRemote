// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.settings.TextAppearance;
import java.util.List;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleablePropertyFactory;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/** A resizable sample using the same CSS palette as the settings panel. */
final class TextSpacingPreview extends Region {
    private static final StyleablePropertyFactory<TextSpacingPreview> STYLES =
            new StyleablePropertyFactory<>(Region.getClassCssMetaData());
    private static final CssMetaData<TextSpacingPreview, Paint> TEXT_FILL =
            STYLES.createPaintCssMetaData("-fx-text-fill", preview -> preview.textFill, Color.BLACK);
    private final StyleableObjectProperty<Paint> textFill =
            new SimpleStyleableObjectProperty<>(TEXT_FILL, this, "textFill", Color.BLACK);
    private final Canvas canvas = new Canvas();
    private Font font = Font.getDefault();
    private TextAppearance appearance = TextAppearance.terminalDefaults();
    private boolean terminal;

    TextSpacingPreview() {
        setId("text-spacing-preview");
        getStyleClass().add("text-spacing-preview");
        setMinWidth(0);
        setMaxWidth(Double.MAX_VALUE);
        canvas.setManaged(false);
        canvas.setMouseTransparent(true);
        getChildren().add(canvas);
        textFill.addListener(ignored -> draw());
    }

    @Override public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return STYLES.getCssMetaData();
    }

    void configure(Font font, TextAppearance appearance, boolean terminal) {
        this.font = font;
        this.appearance = appearance;
        this.terminal = terminal;
        requestLayout();
    }

    private Text measure() {
        Text measure = new Text("W");
        measure.setFont(font);
        return measure;
    }

    private double lineHeight(Text measure) {
        return Math.max(1, Math.ceil(measure.getLayoutBounds().getHeight()) + appearance.lineSpacing());
    }

    @Override protected double computePrefHeight(double width) {
        Text measure = measure();
        return snappedTopInset() + Math.ceil(measure.getLayoutBounds().getHeight())
                + 2 * lineHeight(measure) + snappedBottomInset();
    }

    @Override protected double computeMinHeight(double width) { return computePrefHeight(width); }

    @Override protected void layoutChildren() {
        canvas.relocate(snappedLeftInset(), snappedTopInset());
        canvas.setWidth(Math.max(0, getWidth() - snappedLeftInset() - snappedRightInset()));
        canvas.setHeight(Math.max(0, getHeight() - snappedTopInset() - snappedBottomInset()));
        draw();
    }

    private void draw() {
        var graphics = canvas.getGraphicsContext2D();
        graphics.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        graphics.setFont(font);
        graphics.setFill(textFill.get());
        Text measure = measure();
        double cell = Math.round(measure.getLayoutBounds().getWidth());
        double lineHeight = lineHeight(measure);
        double y = -measure.getLayoutBounds().getMinY();
        for (String line : new String[]{"FastRemote 0123456789", "Aa Bb Cc [] {} ()", "user@host ~ % ls"}) {
            double x = 0;
            for (int i = 0; i < line.length(); i++) {
                String glyph = line.substring(i, i + 1);
                graphics.fillText(glyph, x, y);
                measure.setText(glyph);
                x += (terminal ? cell : measure.getLayoutBounds().getWidth()) + appearance.letterSpacing();
            }
            y += lineHeight;
        }
    }
}
