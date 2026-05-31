package com.campusresale.files;

public record FileContentResponse(
        byte[] bytes,
        String contentType,
        String filename
) {
}
