CREATE INDEX IF NOT EXISTS idx_stored_files_owner_created
    ON stored_files (owner_user_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_stored_files_kind_visibility_created
    ON stored_files (file_kind, visibility_scope, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_stored_files_business
    ON stored_files (business_type, business_id)
    WHERE business_type IS NOT NULL AND business_id IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_stored_files_checksum
    ON stored_files (checksum);

ALTER TABLE stored_files
    ADD CONSTRAINT ck_stored_files_file_kind
    CHECK (file_kind IN ('AVATAR', 'GOODS_IMAGE', 'CAMPUS_AUTH_MATERIAL'));

ALTER TABLE stored_files
    ADD CONSTRAINT ck_stored_files_auth_material_admin_only
    CHECK (file_kind <> 'CAMPUS_AUTH_MATERIAL' OR visibility_scope = 'ADMIN_ONLY');

CREATE INDEX IF NOT EXISTS idx_sensitive_access_logs_admin_target_created
    ON sensitive_access_logs (admin_id, target_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_sensitive_access_logs_target_result_created
    ON sensitive_access_logs (target_type, target_id, result, created_at DESC);
