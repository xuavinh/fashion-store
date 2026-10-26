package com.fashion.store.modules.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface StorageService {

    String uploadFile(MultipartFile file, String folder);

    List<String> uploadMultipleFiles(List<MultipartFile> files, String folder);

    String uploadBase64(String base64Data, String folder);

    void deleteFile(String fileUrl);
}
