package com.campusresale.platform.storage;

public interface ObjectStorageClient {

    void putObject(String storageKey, byte[] bytes, String contentType);

    StoredObject getObject(String storageKey);

    void deleteObject(String storageKey);
}
