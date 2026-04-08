package cn.cctstudio.nexacord.controller;

import cn.cctstudio.nexacord.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {
    private final FileStorageService fileStorageService;

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        String fileUrl = fileStorageService.uploadFile(file);
        return new ResponseEntity<>(fileUrl, HttpStatus.CREATED);
    }

    @PostMapping("/upload-multiple")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<String>> uploadMultipleFiles(@RequestParam("files") List<MultipartFile> files) throws IOException {
        List<String> fileUrls = fileStorageService.uploadMultipleFiles(files);
        return new ResponseEntity<>(fileUrls, HttpStatus.CREATED);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteFile(@RequestParam("fileUrl") String fileUrl) {
        fileStorageService.deleteFile(fileUrl);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/presigned-url")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> getPresignedUrl(
            @RequestParam("fileUrl") String fileUrl,
            @RequestParam(required = false, defaultValue = "15") long expirationMinutes) {
        String presignedUrl = fileStorageService.generatePresignedUrl(fileUrl, expirationMinutes);
        return new ResponseEntity<>(presignedUrl, HttpStatus.OK);
    }
}
