package cn.cctstudio.nexacord.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface FileStorageService {
    String uploadFile(MultipartFile file) throws IOException;
    List<String> uploadMultipleFiles(List<MultipartFile> files) throws IOException;
    void deleteFile(String fileUrl);
    String generatePresignedUrl(String fileUrl, long expirationMinutes);
}