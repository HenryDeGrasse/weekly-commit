package com.weeklycommit;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI spec metadata. The springdoc library auto-generates endpoint
 * definitions from {@code @RestController} annotations; this configuration adds
 * global metadata (title, version, contact, servers).
 */
@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI weeklyCommitOpenAPI() {
		return new OpenAPI().info(new Info().title("Weekly Commit API").version("1.0.0")
				.description("API for the Weekly Commit module — weekly planning, "
						+ "lock/reconcile lifecycle, RCDO hierarchy, team views, " + "tickets, and AI assistance.")
				.contact(new Contact().name("Engineering").email("eng@weeklycommit.dev")))
				.servers(List.of(new Server().url("http://localhost:8080").description("Local development"),
						new Server().url("https://api.weeklycommit.dev").description("Production")));
	}
}
