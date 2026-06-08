-- 文件功能：补齐会话按参与者维度的删除可见性，避免一方删除影响另一方记录。

ALTER TABLE conversations
    ADD COLUMN buyer_deleted_at TIMESTAMPTZ,
    ADD COLUMN seller_deleted_at TIMESTAMPTZ;

CREATE INDEX idx_conversations_buyer_visible_archived
    ON conversations (buyer_id, buyer_deleted_at, buyer_archived_at, updated_at DESC);

CREATE INDEX idx_conversations_seller_visible_archived
    ON conversations (seller_id, seller_deleted_at, seller_archived_at, updated_at DESC);
