package com.campusresale.conversation;

public record MessageAttachmentRecord(
        long id,
        long messageId,
        long fileId,
        String originalName,
        String contentType,
        long byteSize,
        int sortOrder
) {
}
