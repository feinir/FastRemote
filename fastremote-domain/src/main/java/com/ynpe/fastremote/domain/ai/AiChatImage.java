// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.ai;

import java.util.Objects;
import java.util.UUID;

/** A bounded image attached to a persisted user chat message. */
public record AiChatImage(UUID id, String mediaType, byte[] data, int width, int height, byte[] thumbnailData) {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int MAX_THUMBNAIL_BYTES = 512 * 1024;
    public static final long MAX_PIXELS = 20_000_000L;

    public AiChatImage(UUID id, String mediaType, byte[] data, int width, int height) {
        this(id, mediaType, data, width, height, data);
    }

    public AiChatImage {
        Objects.requireNonNull(id, "id");
        mediaType = Objects.requireNonNull(mediaType, "mediaType").strip().toLowerCase(java.util.Locale.ROOT);
        if (!mediaType.equals("image/png") && !mediaType.equals("image/jpeg") && !mediaType.equals("image/webp")) {
            throw new IllegalArgumentException("Unsupported AI chat image type");
        }
        data = Objects.requireNonNull(data, "data").clone();
        if (data.length == 0 || data.length > MAX_BYTES) throw new IllegalArgumentException("Invalid AI chat image size");
        thumbnailData = Objects.requireNonNull(thumbnailData, "thumbnailData").clone();
        if (thumbnailData.length == 0 || thumbnailData.length > MAX_THUMBNAIL_BYTES) {
            throw new IllegalArgumentException("Invalid AI chat thumbnail size");
        }
        if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) {
            throw new IllegalArgumentException("Invalid AI chat image dimensions");
        }
    }

    @Override public byte[] data() { return data.clone(); }
    @Override public byte[] thumbnailData() { return thumbnailData.clone(); }
}
