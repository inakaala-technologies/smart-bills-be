package com.bhive.common.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class S3ImageUploadService {

	private final AwsSecretsManagerService secretsService;

	@Value("${spring.profiles.active}")
	private String profile;

	@Value("${bhive.s3.bucket-name}")
	private String bucketName;

	@Value("${bhive.s3.prefix:}")
	private String keyPrefix;

	private final S3Client s3Client;

	public S3ImageUploadService(S3Client s3Client, AwsSecretsManagerService secretsService) {
		this.secretsService = secretsService;
		this.s3Client = s3Client;
	}

	public String uploadBase64Image(String base64Image, String id) {
		try {
			String secretName = profile + "/ts/s3";
			JSONObject secretJson = secretsService.getSecretAsJson(secretName);
			String region = secretJson.getString("s3Region");
			String bucketName = secretJson.getString("s3BucketName");

			String mimeFromHeader = extractMimeTypeFromDataUrl(base64Image);
			String base64Data = extractBase64Payload(base64Image);

			byte[] decodedBytes = Base64.getDecoder().decode(base64Data);

			String contentType = (mimeFromHeader != null) ? mimeFromHeader : guessMimeFromBytes(decodedBytes);
			if (contentType == null || contentType.isBlank()) {
				contentType = "application/octet-stream";
			}

			String extension = extensionFromMime(contentType);

			String safeId = sanitizeId(id);
			String fileName = withPrefix("images/" + safeId + extension);

			PutObjectRequest putObjectRequest = PutObjectRequest.builder().bucket(bucketName).key(fileName)
					.contentType(contentType).build();

			PutObjectResponse response = s3Client.putObject(putObjectRequest, RequestBody.fromBytes(decodedBytes));

			String url = "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + fileName;
			if (response != null && response.versionId() != null && !response.versionId().isEmpty()) {
				url += "?versionId=" + response.versionId();
			}
			return url;

		} catch (Exception e) {
			throw new RuntimeException("Error uploading image to S3", e);
		}
	}

//	public String uploadBusinessOfferFile(MultipartFile file, String businessId, String offerId) {
//		try {
//			String secretName = profile + "/ts/s3";
//			JSONObject secretJson = secretsService.getSecretAsJson(secretName);
//
//			String bucketName = secretJson.getString("s3BucketName");
//			String region = secretJson.getString("s3Region");
//
//			String contentType = file.getContentType();
//			String extension = extensionFromMime(contentType);
//
//			String fileName = withPrefix(
//					"business_offers/" + sanitizeId(businessId) + "/" + sanitizeId(offerId) + extension);
//
//			PutObjectRequest request = PutObjectRequest.builder().bucket(bucketName).key(fileName)
//					.contentType(contentType).build();
//
//			s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
//
//			return "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + fileName;
//
//		} catch (Exception e) {
//			throw new RuntimeException("Business offer file upload failed", e);
//		}
//	}

	public String uploadBusinessOfferFile(MultipartFile file, String businessId, String offerId) {

		final String bucketName = "bhive-dev-uploads";

		try {
			// Retrieve the S3 region from the existing secret.
			String secretName = profile + "/ts/s3";

			JSONObject secretJson = secretsService.getSecretAsJson(secretName);
			String region = secretJson.getString("s3Region");

			String contentType = file.getContentType();

			if (!StringUtils.hasText(contentType)) {
				throw new IllegalArgumentException("File content type is missing");
			}

			String extension = extensionFromMime(contentType);

			String fileName = "business_offers/" + sanitizeId(businessId) + "/" + sanitizeId(offerId) + extension;

			PutObjectRequest request = PutObjectRequest.builder().bucket(bucketName).key(fileName)
					.contentType(contentType).build();

			s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));

			return "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + fileName;

		} catch (Exception e) {
			log.error("Business offer upload failed. businessId={}, offerId={}, bucket={}", businessId, offerId,
					bucketName, e);

			throw new RuntimeException("Business offer file upload failed", e);
		}
	}

	public void deleteFileByUrl(String url) {
		try {
			URI uri = URI.create(url);
			String bucketName = uri.getHost().split("\\.")[0];
			String key = uri.getPath().substring(1);

			s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
		} catch (Exception e) {
			throw new RuntimeException("File deletion failed", e);
		}
	}

	private void deleteIfExists(String bucketName, String key) {
		try {
			s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
		} catch (Exception ignored) {
			// Ignore if object does not exist
		}
	}

	private static String extractMimeTypeFromDataUrl(String dataUrl) {
		if (dataUrl == null)
			return null;
		if (!dataUrl.startsWith("data:"))
			return null;
		int semi = dataUrl.indexOf(';');
		if (semi <= 5)
			return null; // "data:" is 5 chars
		return dataUrl.substring(5, semi).trim().toLowerCase(Locale.ROOT);
	}

	public byte[] downloadFile(String fileUrl) {
		try {
			URI uri = URI.create(fileUrl);

			String bucketName = uri.getHost().split("\\.")[0];
			String key = uri.getPath().substring(1);

			GetObjectRequest request = GetObjectRequest.builder().bucket(bucketName).key(key).build();

			return s3Client.getObject(request).readAllBytes();

		} catch (Exception e) {
			throw new RuntimeException("Failed to download file from S3", e);
		}
	}

	private static String extractBase64Payload(String input) {
		if (input == null)
			throw new IllegalArgumentException("base64 input is null");
		String s = input.trim();
		if (s.startsWith("data:")) {
			int comma = s.indexOf(',');
			if (comma < 0)
				throw new IllegalArgumentException("Invalid data URL: missing comma");
			s = s.substring(comma + 1);
		}
		return s.replaceAll("\\s+", "");
	}

	private static String guessMimeFromBytes(byte[] bytes) {
		try (InputStream is = new ByteArrayInputStream(bytes)) {
			return URLConnection.guessContentTypeFromStream(is);
		} catch (Exception ignore) {
			return null;
		}
	}

	private static String extensionFromMime(String mime) {
		if (mime == null)
			return ".bin";
		String m = mime.toLowerCase(Locale.ROOT);
		switch (m) {
		case "image/jpeg":
		case "image/jpg":
			return ".jpg";
		case "image/png":
			return ".png";
		case "image/gif":
			return ".gif";
		case "image/webp":
			return ".webp";
		case "image/bmp":
			return ".bmp";
		case "image/tiff":
			return ".tiff";
		case "image/heic":
			return ".heic";
		case "image/heif":
			return ".heif";
		case "image/svg+xml":
			return ".svg";
		case "image/x-icon":
		case "image/vnd.microsoft.icon":
			return ".ico";
		case "application/pdf":
			return ".pdf";
		case "application/msword":
			return ".doc";

		case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
			return ".docx";
		default:
			if (m.contains("jpeg"))
				return ".jpg";
			if (m.contains("svg"))
				return ".svg";
			if (m.contains("png"))
				return ".png";
			if (m.contains("gif"))
				return ".gif";
			if (m.contains("webp"))
				return ".webp";
			if (m.contains("bmp"))
				return ".bmp";
			if (m.contains("tif"))
				return ".tiff";
			return ".bin";
		}
	}

	private static String sanitizeId(String id) {
		String safe = Objects.toString(id, "file");
		safe = safe.replaceAll("[^A-Za-z0-9-]", "");
		if (safe.isBlank())
			safe = "file";
		return safe;
	}

	public void deleteImage(String fileUrl) {
		try {
			if (fileUrl == null || fileUrl.isBlank())
				return;

			String secretName = profile + "/cscs/s3";
			JSONObject secretJson = secretsService.getSecretAsJson(secretName);
			String bucketName = secretJson.getString("s3BucketName");

			// Extract key from URL — everything after .amazonaws.com/
			String key = fileUrl.substring(fileUrl.indexOf(".amazonaws.com/") + ".amazonaws.com/".length());

			// Strip ?versionId=... if present
			if (key.contains("?")) {
				key = key.substring(0, key.indexOf("?"));
			}

			s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());

		} catch (Exception e) {
			throw new RuntimeException("Error deleting image from S3", e);
		}
	}

	private String withPrefix(String key) {
		String prefix = keyPrefix == null ? "" : keyPrefix.trim().replaceAll("^/+|/+$", "");
		return prefix.isEmpty() ? key : prefix + "/" + key;
	}

}
