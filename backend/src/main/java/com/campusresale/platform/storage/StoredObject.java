package com.campusresale.platform.storage;

public record StoredObject(
        byte[] bytes,
        String contentType
) {
}
