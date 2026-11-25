// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.ynpe.fastremote.domain.transfer.RemoteFileAttributes;
import java.util.Optional;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import org.junit.jupiter.api.BeforeAll;

import org.junit.jupiter.api.Test;

class RemoteFilePropertiesDialogTest {
    @BeforeAll
    static void toolkit() throws Exception {
        var started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); }
        catch (IllegalStateException running) { started.countDown(); }
        assertTrue(started.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test
    void showsFullLinkTargetImmediatelyBelowTypeAndOmitsItForRegularFiles() throws Exception {
        var check = new FutureTask<Void>(() -> {
            String path = "/etc/nginx/sites-available/web.conf";
            var link = new RemoteFileAttributes("web.conf", "/etc/nginx/sites-enabled/web.conf",
                    false, true, 34, Optional.empty(), "0", "0", 0777, Optional.of(path));
            GridPane grid = RemoteFilePropertiesDialog.information(link);
            Label target = (Label) grid.getChildren().stream()
                    .filter(node -> "remote-properties-link-target".equals(node.getId())).findFirst().orElseThrow();
            assertEquals(1, GridPane.getRowIndex(target));
            assertEquals(path, target.getText());
            assertEquals(path, target.getTooltip().getText());
            assertTrue(target.isWrapText());
            assertEquals(10, grid.getChildren().size());
            var regular = new RemoteFileAttributes("web.conf", path, false, false,
                    34, Optional.empty(), "0", "0", 0644);
            assertEquals(8, RemoteFilePropertiesDialog.information(regular).getChildren().size());
            return null;
        });
        Platform.runLater(check);
        check.get(10, TimeUnit.SECONDS);
    }

    @Test
    void reservesEnoughWidthForLocalizedPermissionLabels() {
        assertTrue(RemoteFilePropertiesDialog.PREFERRED_CONTENT_WIDTH >= 530.0);
        assertTrue(RemoteFilePropertiesDialog.PERMISSION_LABEL_WIDTH >= 100.0);
        assertTrue(RemoteFilePropertiesDialog.PERMISSION_ROW_HEIGHT >= 44.0);
    }
}
