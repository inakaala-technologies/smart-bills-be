package com.bhive.common.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import software.amazon.awssdk.services.s3.S3Client;

@ExtendWith(MockitoExtension.class)
class S3ImageUploadServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private AwsSecretsManagerService secretsService;

    private S3ImageUploadService uploadService;

    @BeforeEach
    void setUp() {
        uploadService = new S3ImageUploadService(s3Client, secretsService);
    }

    @Test
    void rejectsFilesOverTwoMegabytes() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.png", "image/png", new byte[2 * 1024 * 1024 + 1]);

        assertThrows(IllegalArgumentException.class, () -> uploadService.uploadUserProfileImage(file, 42L));
    }

    @Test
    void rejectsNonImageContentTypes() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.txt", "text/plain", new byte[] { 1, 2, 3 });

        assertThrows(IllegalArgumentException.class, () -> uploadService.uploadUserProfileImage(file, 42L));
    }

    @Test
    void rejectsImageContentTypesWithInvalidFileSignatures() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "profile.png", "image/png", new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 });

        assertThrows(IllegalArgumentException.class, () -> uploadService.uploadUserProfileImage(file, 42L));
    }
}
