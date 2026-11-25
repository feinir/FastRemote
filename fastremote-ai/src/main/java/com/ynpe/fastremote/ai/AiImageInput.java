// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

import java.util.Locale;
import java.util.Objects;

/** A bounded, normalized raster image ready to be sent to a model. */
public record AiImageInput(String mediaType, byte[] data, int width, int height, byte[] thumbnailData) {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int MAX_THUMBNAIL_BYTES = 512 * 1024;
    public static final long MAX_PIXELS = 20_000_000L;

    public AiImageInput(String mediaType, byte[] data, int width, int height) {
        this(mediaType, data, width, height, data);
    }

    public AiImageInput {
        mediaType = Objects.requireNonNull(mediaType, "mediaType").strip().toLowerCase(Locale.ROOT);
        if (!mediaType.equals("image/png") && !mediaType.equals("image/jpeg")
                && !mediaType.equals("image/webp")) {
            throw new IllegalArgumentException("Unsupported AI image media type");
        }
        data = Objects.requireNonNull(data, "data").clone();
        if (data.length == 0 || data.length > MAX_BYTES) {
            throw new IllegalArgumentException("AI image size is outside the supported range");
        }
        thumbnailData = Objects.requireNonNull(thumbnailData, "thumbnailData").clone();
        if (thumbnailData.length == 0 || thumbnailData.length > MAX_THUMBNAIL_BYTES) {
            throw new IllegalArgumentException("AI image thumbnail is outside the supported range");
        }
        if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) {
            throw new IllegalArgumentException("AI image dimensions are outside the supported range");
        }
    }

    @Override public byte[] data() { return data.clone(); }
    @Override public byte[] thumbnailData() { return thumbnailData.clone(); }
}
