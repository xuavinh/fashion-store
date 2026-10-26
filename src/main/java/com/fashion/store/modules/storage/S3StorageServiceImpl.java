package com.fashion.store.modules.storage;

import com.fashion.store.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageServiceImpl implements StorageService {

    private final S3Client s3Client;

    @Value("${app.storage.s3.bucket-name:fashion-store}")
    private String bucketName;

    @Value("${app.storage.s3.public-url:}")
    private String publicUrl;

    @Value("${app.storage.s3.endpoint:}")
    private String endpoint;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    @Override
    public String uploadFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Tập tin tải lên không được để trống");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("Dung lượng ảnh vượt quá giới hạn tối đa cho phép (10MB)");
        }

        try {
            byte[] bytes = file.getBytes();
            ImageFormat format = detectAndValidateImageFormat(bytes);

            return uploadBytesToS3(bytes, format, folder);
        } catch (IOException e) {
            log.error("Lỗi khi đọc file upload: {}", e.getMessage(), e);
            throw new BadRequestException("Không thể đọc tập tin tải lên: " + e.getMessage());
        }
    }

    @Override
    public List<String> uploadMultipleFiles(List<MultipartFile> files, String folder) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                urls.add(uploadFile(file, folder));
            }
        }
        return urls;
    }

    @Override
    public String uploadBase64(String base64Data, String folder) {
        if (!StringUtils.hasText(base64Data)) {
            throw new BadRequestException("Dữ liệu ảnh Base64 không được để trống");
        }

        String rawBase64 = base64Data.trim();

        // Tách tiền tố Data URL nếu có (ví dụ: data:image/png;base64,iVBORw0KGgo...)
        if (rawBase64.contains(",")) {
            rawBase64 = rawBase64.substring(rawBase64.indexOf(",") + 1).trim();
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(rawBase64);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Chuỗi Base64 không hợp lệ: " + e.getMessage());
        }

        if (bytes.length == 0) {
            throw new BadRequestException("Dữ liệu ảnh Base64 sau khi giải mã rỗng");
        }

        if (bytes.length > MAX_FILE_SIZE) {
            throw new BadRequestException("Dung lượng ảnh Base64 vượt quá giới hạn tối đa (10MB)");
        }

        // Xác thực nội dung nhị phân thực tế bằng Magic Bytes
        ImageFormat format = detectAndValidateImageFormat(bytes);

        return uploadBytesToS3(bytes, format, folder);
    }

    @Override
    public void deleteFile(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return;
        }

        try {
            String key = extractKeyFromUrl(fileUrl);
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("Đã xóa file khỏi S3/R2: key={}", key);
        } catch (Exception e) {
            log.warn("Không thể xóa file '{}' từ S3/R2: {}", fileUrl, e.getMessage());
        }
    }

    private String uploadBytesToS3(byte[] bytes, ImageFormat format, String folder) {
        String cleanFolder = StringUtils.hasText(folder) ? folder.trim().replaceAll("^/+|/+$", "") + "/" : "";
        // Tên file luôn được sinh bằng UUID và đuôi file lấy CHÍNH XÁC từ Magic Bytes thực tế
        String uniqueKey = cleanFolder + UUID.randomUUID() + format.extension;

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(uniqueKey)
                    .contentType(format.mimeType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(bytes));

            return resolveFileUrl(uniqueKey);
        } catch (Exception e) {
            log.error("Lỗi khi tải file lên S3 / R2: {}", e.getMessage(), e);
            throw new RuntimeException("Tải ảnh lên S3/R2 thất bại: " + e.getMessage(), e);
        }
    }

    // Xác thực Magic Bytes
    private ImageFormat detectAndValidateImageFormat(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            throw new BadRequestException("Tập tin quá nhỏ hoặc không phải là định dạng ảnh hợp lệ");
        }

        // JPEG / JPG: FF D8 FF
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return new ImageFormat("image/jpeg", ".jpg");
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((bytes[0] & 0xFF) == 0x89 && (bytes[1] & 0xFF) == 0x50 &&
                (bytes[2] & 0xFF) == 0x4E && (bytes[3] & 0xFF) == 0x47 &&
                (bytes[4] & 0xFF) == 0x0D && (bytes[5] & 0xFF) == 0x0A &&
                (bytes[6] & 0xFF) == 0x1A && (bytes[7] & 0xFF) == 0x0A) {
            return new ImageFormat("image/png", ".png");
        }

        // GIF: 'G' 'I' 'F' '8' ('7' hoặc '9') 'a'
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8' &&
                (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a') {
            return new ImageFormat("image/gif", ".gif");
        }

        // WEBP: bytes 0-3 là 'RIFF' và bytes 8-11 là 'WEBP'
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' &&
                bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return new ImageFormat("image/webp", ".webp");
        }
        throw new BadRequestException("Nội dung tập tin không phải ảnh hợp lệ (hoặc đã bị đổi đuôi tên file giả mạo). Hệ thống chỉ chấp nhận JPEG, PNG, GIF, WEBP.");
    }

    private String resolveFileUrl(String key) {
        if (StringUtils.hasText(publicUrl)) {
            String baseUrl = publicUrl.replaceAll("/+$", "");
            return baseUrl + "/" + key;
        }
        if (StringUtils.hasText(endpoint)) {
            String baseEndpoint = endpoint.replaceAll("/+$", "");
            return baseEndpoint + "/" + bucketName + "/" + key;
        }
        return "https://" + bucketName + ".s3.amazonaws.com/" + key;
    }

    private String extractKeyFromUrl(String fileUrl) {
        if (StringUtils.hasText(publicUrl) && fileUrl.startsWith(publicUrl)) {
            return fileUrl.substring(publicUrl.length()).replaceAll("^/+", "");
        }
        if (fileUrl.contains(bucketName + "/")) {
            return fileUrl.substring(fileUrl.indexOf(bucketName + "/") + bucketName.length() + 1);
        }
        int lastSlash = fileUrl.lastIndexOf("/");
        return lastSlash >= 0 ? fileUrl.substring(lastSlash + 1) : fileUrl;
    }

    private record ImageFormat(String mimeType, String extension) {}
}
