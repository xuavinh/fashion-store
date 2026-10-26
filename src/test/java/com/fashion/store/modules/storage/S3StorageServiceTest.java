package com.fashion.store.modules.storage;

import com.fashion.store.common.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private S3StorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storageService, "bucketName", "fashion-vinh");
        ReflectionTestUtils.setField(storageService, "publicUrl", "https://fashion-vinh.s3.us-east-1.amazonaws.com");
    }

    @Test
    @DisplayName("Upload ảnh JPEG hợp lệ qua chữ ký Magic Bytes (FF D8 FF) -> Thành công")
    void uploadFile_ValidJpeg_Success() {
        // Tạo byte array có Magic Bytes của JPEG (FF D8 FF ...)
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", jpegBytes);

        String resultUrl = storageService.uploadFile(file, "products");

        assertThat(resultUrl).isNotNull();
        assertThat(resultUrl).startsWith("https://fashion-vinh.s3.us-east-1.amazonaws.com/products/");
        assertThat(resultUrl).endsWith(".jpg");
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("Upload ảnh PNG hợp lệ qua chữ ký Magic Bytes (89 50 4E 47...) -> Thành công")
    void uploadFile_ValidPng_Success() {
        // Tạo byte array có Magic Bytes của PNG (89 50 4E 47 0D 0A 1A 0A ...)
        byte[] pngBytes = new byte[]{
                (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47,
                (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A,
                0x00, 0x00, 0x00, 0x00
        };
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", pngBytes);

        String resultUrl = storageService.uploadFile(file, "products");

        assertThat(resultUrl).isNotNull();
        assertThat(resultUrl).endsWith(".png");
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("Chặn file giả mạo đuôi ảnh (file script hoặc text đổi tên thành .jpg) -> Ném BadRequestException")
    void uploadFile_SpoofedExtension_ThrowsBadRequestException() {
        // File văn bản thuần "Hello world" đổi tên thành malicious.jpg
        byte[] fakeBytes = "Hello world malicious script".getBytes();
        MockMultipartFile fakeFile = new MockMultipartFile("file", "malicious.jpg", "image/jpeg", fakeBytes);

        assertThatThrownBy(() -> storageService.uploadFile(fakeFile, "products"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Nội dung tập tin không phải ảnh hợp lệ");

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("Upload nhiều ảnh cùng lúc -> Trả về danh sách URL")
    void uploadMultipleFiles_Success() {
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file1 = new MockMultipartFile("files", "img1.jpg", "image/jpeg", jpegBytes);
        MockMultipartFile file2 = new MockMultipartFile("files", "img2.jpg", "image/jpeg", jpegBytes);

        List<String> urls = storageService.uploadMultipleFiles(List.of(file1, file2), "products");

        assertThat(urls).hasSize(2);
        verify(s3Client, times(2)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("Upload ảnh Base64 có tiền tố Data URI (data:image/jpeg;base64,...) -> Thành công")
    void uploadBase64_ValidDataUri_Success() {
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        String base64Content = Base64.getEncoder().encodeToString(jpegBytes);
        String dataUri = "data:image/jpeg;base64," + base64Content;

        String resultUrl = storageService.uploadBase64(dataUri, "products");

        assertThat(resultUrl).isNotNull();
        assertThat(resultUrl).endsWith(".jpg");
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("Upload ảnh Base64 với chuỗi rỗng -> Ném BadRequestException")
    void uploadBase64_EmptyString_ThrowsBadRequestException() {
        assertThatThrownBy(() -> storageService.uploadBase64("", "products"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không được để trống");
    }

    @Test
    @DisplayName("Xóa file khỏi S3/R2 bằng URL đầy đủ -> Trích xuất đúng key và gọi s3Client.deleteObject")
    void deleteFile_ValidUrl_Success() {
        String fileUrl = "https://fashion-vinh.s3.us-east-1.amazonaws.com/products/test-image.jpg";

        storageService.deleteFile(fileUrl);

        verify(s3Client).deleteObject(argThat((DeleteObjectRequest req) ->
                req.bucket().equals("fashion-vinh") && req.key().equals("products/test-image.jpg")
        ));
    }
}
