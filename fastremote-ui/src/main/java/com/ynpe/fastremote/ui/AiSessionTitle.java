// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/** Two bounded lines with a smaller timestamp following the title. */
final class AiSessionTitle extends Label {
    private final TextFlow flow = new TextFlow();
    private final Text caption = new Text();
    private final Text age = new Text();
    private double measuredWidth = -1;
    private String measuredTitle;
    private javafx.scene.text.Font measuredFont;

    AiSessionTitle(String title, String time) {
        super(title);
        age.setText("  " + time);
        caption.getStyleClass().add("ai-session-caption");
        age.getStyleClass().add("ai-session-age");
        flow.getChildren().setAll(caption, age);
        setGraphic(flow);
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        textProperty().addListener(ignored -> requestLayout());
        fontProperty().addListener(ignored -> requestLayout());
    }

    @Override protected void layoutChildren() {
        double width = Math.max(1, getWidth() - snappedLeftInset() - snappedRightInset());
        if (Double.compare(width, measuredWidth) == 0 && getText().equals(measuredTitle)
                && getFont().equals(measuredFont)) {
            super.layoutChildren();
            return;
        }
        measuredWidth = width;
        measuredTitle = getText();
        measuredFont = getFont();
        caption.setFont(getFont());
        String full = getText().replaceAll("\\s+", " ");
        caption.setText(full);
        Text line = new Text("Ag");
        line.setFont(getFont());
        double twoLines = Math.ceil(line.getLayoutBounds().getHeight() * 2 + 2);
        if (flow.prefHeight(width) > twoLines) {
            int count = full.codePointCount(0, full.length());
            int low = 0, high = count;
            while (low < high) {
                int middle = (low + high + 1) / 2;
                caption.setText(full.substring(0, full.offsetByCodePoints(0, middle)) + "...");
                if (flow.prefHeight(width) <= twoLines) low = middle;
                else high = middle - 1;
            }
            caption.setText(full.substring(0, full.offsetByCodePoints(0, low)) + "...");
        }
        flow.setPrefWidth(width);
        flow.setMaxWidth(width);
        super.layoutChildren();
    }
}
