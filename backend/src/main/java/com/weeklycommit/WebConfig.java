package com.weeklycommit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global web configuration for the Weekly Commit backend.
 *
 * <p>
 * CORS policy: in the Module Federation remote pattern the host app's gateway
 * (nginx, ALB, etc.) typically proxies API calls so CORS is not triggered in
 * production. This explicit policy provides defense-in-depth for development
 * and any deployment topology where the frontend origin differs from the
 * backend.
 *
 * <p>
 * The allowed origins are configurable via {@code app.cors.allowed-origins}
 * (comma-separated). Defaults to {@code http://localhost:5173} (Vite dev
 * server) and {@code http://localhost:80} (Docker frontend).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:80,http://localhost}")
	private String[] allowedOrigins;

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOrigins(allowedOrigins)
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
				.allowedHeaders("*")
				.exposedHeaders("X-Request-Id")
				.allowCredentials(true)
				.maxAge(3600);

		// Health/actuator endpoints — allow from anywhere (no credentials)
		registry.addMapping("/health")
				.allowedOrigins("*")
				.allowedMethods("GET");
		registry.addMapping("/actuator/**")
				.allowedOrigins("*")
				.allowedMethods("GET");
	}
}
