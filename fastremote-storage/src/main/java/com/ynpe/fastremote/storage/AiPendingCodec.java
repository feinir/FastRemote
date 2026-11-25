// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import com.ynpe.fastremote.domain.ai.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Versioned, bounded queue payload. Never uses Java object deserialization. */
final class AiPendingCodec {
    private AiPendingCodec() { }
    static byte[] encode(List<AiPendingPrompt> prompts) throws IOException {
        if (prompts.size() > 8) throw new IllegalArgumentException("At most eight queued messages");
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(1); out.writeInt(prompts.size());
            for (var prompt : prompts) {
                out.writeUTF(prompt.id().toString()); out.writeBoolean(prompt.steering());
                write(out, prompt.text().getBytes(StandardCharsets.UTF_8));
                out.writeInt(prompt.images().size());
                for (var image : prompt.images()) {
                    out.writeUTF(image.id().toString()); out.writeUTF(image.mediaType());
                    out.writeInt(image.width()); out.writeInt(image.height());
                    write(out, image.data()); write(out, image.thumbnailData());
                }
            }
        }
        return bytes.toByteArray();
    }
    static List<AiPendingPrompt> decode(byte[] bytes) throws IOException {
        if (bytes == null) return List.of();
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != 1) throw new IOException("Unsupported AI queue format");
            int count = count(in, 8);
            var prompts = new ArrayList<AiPendingPrompt>();
            var ids = new HashSet<UUID>();
            for (int n = 0; n < count; n++) {
                UUID id = UUID.fromString(in.readUTF());
                if (!ids.add(id)) throw new IOException("Duplicate queued message");
                boolean steering = in.readBoolean();
                String text = new String(read(in, 512 * 1024 * 4), StandardCharsets.UTF_8);
                int imageCount = count(in, 4);
                var images = new ArrayList<AiChatImage>();
                for (int i = 0; i < imageCount; i++) {
                    UUID imageId = UUID.fromString(in.readUTF()); String media = in.readUTF();
                    int width = in.readInt(), height = in.readInt();
                    images.add(new AiChatImage(imageId, media, read(in, AiChatImage.MAX_BYTES),
                            width, height, read(in, AiChatImage.MAX_THUMBNAIL_BYTES)));
                }
                prompts.add(new AiPendingPrompt(id, text, images, steering));
            }
            if (in.available() != 0) throw new IOException("Trailing AI queue data");
            return List.copyOf(prompts);
        } catch (IllegalArgumentException invalid) { throw new IOException("Invalid AI queue", invalid); }
    }
    private static int count(DataInputStream in, int max) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > max) throw new IOException("Invalid AI queue length");
        return length;
    }
    private static byte[] read(DataInputStream in, int max) throws IOException {
        int length = count(in, max);
        // Check remaining bytes before allocating, including on corrupted local databases.
        if (length > in.available()) throw new EOFException("Truncated AI queue");
        return in.readNBytes(length);
    }
    private static void write(DataOutputStream out, byte[] data) throws IOException {
        out.writeInt(data.length); out.write(data);
    }
}
