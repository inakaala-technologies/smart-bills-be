package com.bhive.common.config;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bhive.common.service.AwsSecretsManagerService;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AwsS3Config {

	@Autowired
	private AwsSecretsManagerService secretsService;

	@Value("${spring.profiles.active}")
	private String profile;

	@Bean
	public S3Client s3Client() {
		String secretName = profile + "/bhive/s3";
		JSONObject secretJson = secretsService.getSecretAsJson(secretName);
		String region = secretJson.getString("s3Region");
		String accessKey = secretJson.getString("s3AccessKey");
		String secreteKey = secretJson.getString("s3SecreteKey");
		return S3Client.builder().region(Region.of(region)).credentialsProvider(
				StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secreteKey))).build();
	}

}
