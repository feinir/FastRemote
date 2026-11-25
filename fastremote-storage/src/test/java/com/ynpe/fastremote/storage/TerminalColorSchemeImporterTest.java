// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TerminalColorSchemeImporterTest {
    @TempDir Path directory;

    @Test
    void importsWindowsTerminalJsonScheme() throws Exception {
        Path source = directory.resolve("ocean.json");
        Files.writeString(source, """
                {
                  // Windows Terminal settings files may use JSONC comments.
                  "name": "Ocean",
                  "background": "#010203", "foreground": "#F0F1F2",
                  "cursorColor": "#AABBCC", "selectionBackground": "#112233",
                  "black": "#000000", "red": "#110000", "green": "#001100", "yellow": "#111100",
                  "blue": "#000011", "purple": "#110011", "cyan": "#001111", "white": "#AAAAAA",
                  "brightBlack": "#222222", "brightRed": "#FF0000", "brightGreen": "#00FF00",
                  "brightYellow": "#FFFF00", "brightBlue": "#0000FF", "brightPurple": "#FF00FF",
                  "brightCyan": "#00FFFF", "brightWhite": "#FFFFFF"
                }
                """);

        var palette = new TerminalColorSchemeImporter().importFile(source).getFirst();

        assertEquals("Ocean", palette.name());
        assertEquals(0xF0F1F2, palette.foreground());
        assertEquals(0x010203, palette.background());
        assertEquals(0xAABBCC, palette.cursor());
        assertEquals(16, palette.ansiColors().size());
        assertEquals(0xFF00FF, palette.ansiColors().get(13));
    }

    @Test
    void importsIterm2ColorPlist() throws Exception {
        Path source = directory.resolve("forest.itermcolors");
        StringBuilder colors = new StringBuilder();
        for (int index = 0; index < 16; index++) {
            colors.append("<key>Ansi ").append(index).append(" Color</key>")
                    .append(color(index / 255.0, 0.0, 0.0));
        }
        Files.writeString(source, """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0"><dict>
                <key>Name</key><string>Forest</string>
                <key>Foreground Color</key>%s
                <key>Background Color</key>%s
                <key>Cursor Color</key>%s
                <key>Selection Color</key>%s
                %s
                </dict></plist>
                """.formatted(
                color(1.0, 1.0, 1.0), color(0.0, 0.0, 0.0),
                color(0.5, 0.5, 0.5), color(0.0, 0.25, 0.0), colors));

        var palette = new TerminalColorSchemeImporter().importFile(source).getFirst();

        assertEquals("Forest", palette.name());
        assertEquals(0xFFFFFF, palette.foreground());
        assertEquals(0x808080, palette.cursor());
        assertEquals(0x004000, palette.selectionBackground());
        assertEquals(0x0F0000, palette.ansiColors().get(15));
    }

    private static String color(double red, double green, double blue) {
        return "<dict><key>Red Component</key><real>" + red
                + "</real><key>Green Component</key><real>" + green
                + "</real><key>Blue Component</key><real>" + blue + "</real></dict>";
    }
}
