package com.fashion.store.modules.storage;

import com.fashion.store.common.response.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<String>> uploadSingle(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "products") String folder
    ) {
        String url = storageService.uploadFile(file, folder);
        return ResponseEntity.ok(ApiResponse.success(url, "Tải ảnh lên thành công"));
    }

    @PostMapping(value = "/upload-multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<String>>> uploadMultiple(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "folder", defaultValue = "products") String folder
    ) {
        List<String> urls = storageService.uploadMultipleFiles(files, folder);
        return ResponseEntity.ok(ApiResponse.success("Tải danh sách ảnh lên thành công", urls));
    }

    @PostMapping("/upload-base64")
    public ResponseEntity<ApiResponse<String>> uploadBase64(@RequestBody Base64UploadRequest request) {
        String url = storageService.uploadBase64(request.getBase64Data(), request.getFolder());
        return ResponseEntity.ok(ApiResponse.success(url, "Tải ảnh Base64 lên thành công"));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<ApiResponse<String>> deleteFile(@RequestParam("fileUrl") String fileUrl) {
        storageService.deleteFile(fileUrl);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa ảnh thành công"));
    }

    @Getter
    @Setter
    public static class Base64UploadRequest {
        @NotBlank(message = "Dữ liệu ảnh Base64 không được để trống")
        private String base64Data;
        private String folder = "products";
    }
}
