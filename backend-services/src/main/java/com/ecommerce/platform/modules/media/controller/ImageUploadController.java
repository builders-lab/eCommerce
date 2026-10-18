package com.ecommerce.platform.modules.media.controller;

import com.ecommerce.platform.modules.media.dto.ImageUploadResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@RestController
@RequestMapping("/api/v1/images")
public class ImageUploadController {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final Path uploadDir;
    private final String publicBaseUrl;
    private final long maxUploadBytes;

    public ImageUploadController(
            @Value("${app.media.upload-dir}") String uploadDir,
            @Value("${app.media.public-base-url}") String publicBaseUrl,
            @Value("${app.media.max-upload-bytes:5242880}") long maxUploadBytes
    ) {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl : publicBaseUrl + "/";
        this.maxUploadBytes = maxUploadBytes;
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ImageUploadResponse> uploadImage(@RequestParam("file") MultipartFile file) {
        validateBasicFileChecks(file);

        try {
            byte[] bytes = file.getBytes();
            String contentType = normalizeContentType(file.getContentType());
            validateImage(contentType, bytes);

            String extension = extensionFor(contentType);
            String newFilename = UUID.randomUUID() + extension;

            Files.createDirectories(uploadDir);
            Path target = uploadDir.resolve(newFilename).normalize();
            if (!target.startsWith(uploadDir)) {
                throw new ResponseStatusException(BAD_REQUEST, "Invalid upload path");
            }

            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
            return ResponseEntity.ok(new ImageUploadResponse(publicBaseUrl + newFilename));
        } catch (IOException e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to upload image");
        }
    }

    private void validateBasicFileChecks(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "No file selected");
        }
        if (file.getSize() > maxUploadBytes) {
            throw new ResponseStatusException(BAD_REQUEST, "File size exceeds allowed limit");
        }
    }

    private String normalizeContentType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Content type is required");
        }
        String normalized = rawType.toLowerCase();
        if ("image/jpg".equals(normalized)) {
            return "image/jpeg";
        }
        return normalized;
    }

    private void validateImage(String contentType, byte[] bytes) {
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported content type");
        }
        if (!matchesSignature(contentType, bytes)) {
            throw new ResponseStatusException(BAD_REQUEST, "File content does not match type");
        }
    }

    private boolean matchesSignature(String contentType, byte[] bytes) {
        return switch (contentType) {
            case "image/png" -> isPng(bytes);
            case "image/jpeg" -> isJpeg(bytes);
            case "image/webp" -> isWebp(bytes);
            default -> false;
        };
    }

    private boolean isPng(byte[] bytes) {
        return bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A;
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private String extensionFor(String contentType) {
        return Map.of(
                "image/png", ".png",
                "image/jpeg", ".jpg",
                "image/webp", ".webp"
        ).get(contentType);
    }
}
