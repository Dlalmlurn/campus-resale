package com.campusresale.conversation;

public record MessageAttachmentResponse(
        long id,
        long fileId,
        String originalName,
        String contentType,
        long byteSize,
        String url
) {

    public static MessageAttachmentResponse from(MessageAttachmentRecord record) {
        return new MessageAttachmentResponse(
                record.id(),
                record.fileId(),
                record.originalName(),
                record.contentType(),
                record.byteSize(),
                "/api/files/" + record.fileId() + "/content"
        );
    }
}
