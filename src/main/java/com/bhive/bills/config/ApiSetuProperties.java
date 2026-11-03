
package com.bhive.bills.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "app.apisetu")
public class ApiSetuProperties {

    private String baseUrl;
    private String apiKey;
    private String clientId;

    private Map<String, ProviderConfig> providers = new HashMap<>();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Map<String, ProviderConfig> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, ProviderConfig> providers) {
        this.providers = providers;
    }

    @PostConstruct
    void registerDocumentedServices() {
        ApiSetuServiceCatalog.registerProviderConfigurations(this);
    }

    public static class ProviderConfig {

        private String endpoint;
        private String httpMethod = "POST";

        // Supported formats: JSON, XML
        private String requestFormat = "JSON";

        // Supported formats: JSON, XML, PDF
        private String responseFormat = "JSON";

        private String contentType;
        private String accept;

        private String apiKey;
        private String clientId;

        private Map<String, String> headers = new HashMap<>();
        private Map<String, Object> requestTemplate = new HashMap<>();

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getHttpMethod() {
            return httpMethod;
        }

        public void setHttpMethod(String httpMethod) {
            this.httpMethod = httpMethod;
        }

        public String getRequestFormat() {
            return requestFormat;
        }

        public void setRequestFormat(String requestFormat) {
            this.requestFormat = requestFormat;
        }

        public String getResponseFormat() {
            return responseFormat;
        }

        public void setResponseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getAccept() {
            return accept;
        }

        public void setAccept(String accept) {
            this.accept = accept;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers;
        }

        public Map<String, Object> getRequestTemplate() {
            return requestTemplate;
        }

        public void setRequestTemplate(Map<String, Object> requestTemplate) {
            this.requestTemplate = requestTemplate;
        }
    }
}
