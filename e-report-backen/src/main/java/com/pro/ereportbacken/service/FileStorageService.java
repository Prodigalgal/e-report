package com.pro.ereportbacken.service;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Getter
@Service
@Log4j2
public class FileStorageService {

    private final Path fileStorageLocation;

    public FileStorageService(@Value("${file.upload-dir:./uploads}") String uploadDir) {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    public StoredFile storeFile(String originalFileName, InputStream inputStream) {
        String cleanedFileName = StringUtils.cleanPath(originalFileName != null ? originalFileName : "uploaded-file");
        try {
            if (cleanedFileName.contains("..")) {
                throw new RuntimeException("Sorry! Filename contains invalid path sequence " + cleanedFileName);
            }
            String fileExtension = "";
            int lastDot = cleanedFileName.lastIndexOf(".");
            if (lastDot >= 0) {
                fileExtension = cleanedFileName.substring(lastDot);
            }
            String storedFileName = UUID.randomUUID() + fileExtension;
            Path targetLocation = this.fileStorageLocation.resolve(storedFileName);
            long size;
            try (InputStream stream = inputStream) {
                size = Files.copy(stream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(cleanedFileName, storedFileName, size);
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + cleanedFileName + ". Please try again!", ex);
        }
    }

    public StoredFile storeCompletedFile(String originalFileName, Path sourceFile) {
        return storeCompletedFile(originalFileName, sourceFile, null);
    }

    public StoredFile storeCompletedQuarantineFile(String originalFileName, Path sourceFile) {
        return storeCompletedFile(originalFileName, sourceFile, "_quarantine");
    }

    private StoredFile storeCompletedFile(String originalFileName, Path sourceFile, String directoryName) {
        String cleanedFileName = StringUtils.cleanPath(originalFileName != null ? originalFileName : "uploaded-file");
        try {
            if (cleanedFileName.contains("..")) {
                throw new RuntimeException("Sorry! Filename contains invalid path sequence " + cleanedFileName);
            }
            String leafFileName = UUID.randomUUID() + extensionOf(cleanedFileName);
            String storedFileName = directoryName == null ? leafFileName : directoryName + "/" + leafFileName;
            Path targetLocation = safeResolve(storedFileName);
            Files.createDirectories(targetLocation.getParent());
            if (!targetLocation.startsWith(this.fileStorageLocation)) {
                throw new RuntimeException("Unsafe target file path.");
            }
            Files.move(sourceFile, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return new StoredFile(cleanedFileName, storedFileName, Files.size(targetLocation));
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + cleanedFileName + ". Please try again!", ex);
        }
    }

    public Resource loadFileAsResource(String fileName) throws FileNotFoundException {
        try {
            Path filePath = safeResolve(fileName);
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new FileNotFoundException("File not found " + fileName);
            }
        } catch (MalformedURLException ex) {
            throw new FileNotFoundException("File not found " + fileName + " (Malformed URL)");
        }
    }

    public void deleteFile(String fileName) {
        try {
            if (!StringUtils.hasText(fileName)) return;
            Path filePath = safeResolve(fileName);
            Files.deleteIfExists(filePath);
        } catch (IOException ex) {
            log.warn("Could not delete file: {}", fileName, ex);
        }
    }

    private String extensionOf(String fileName) {
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot < 0) {
            return "";
        }
        String extension = fileName.substring(lastDot);
        return extension.length() <= 20 ? extension.toLowerCase(Locale.ROOT) : "";
    }

    public Path safeResolve(String storedFileName) {
        if (!StringUtils.hasText(storedFileName)) {
            throw new IllegalArgumentException("文件存储标识不能为空。");
        }
        Path relativePath = Paths.get(storedFileName);
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException("文件存储路径不安全。");
        }
        for (Path segment : relativePath) {
            if ("..".equals(segment.toString())) {
                throw new IllegalArgumentException("文件存储路径不安全。");
            }
        }
        Path path = this.fileStorageLocation.resolve(relativePath).normalize();
        if (!path.startsWith(this.fileStorageLocation)) {
            throw new IllegalArgumentException("文件存储路径不安全。");
        }
        return path;
    }

    public record StoredFile(String originalFileName, String storedFileName, long size) {
    }
}
