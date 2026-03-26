package com.weeklycommit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Feature flags read from {@code app.features.*} in application.yml. Can be
 * overridden per-environment via env vars: {@code FEATURE_AI_ASSISTANCE=false},
 * {@code FEATURE_NOTIFICATIONS=false}, etc.
 */
@Configuration
@ConfigurationProperties(prefix = "app.features")
public class FeatureFlagProperties {

	private boolean aiAssistance = true;
	private boolean notifications = true;

	public boolean isAiAssistance() {
		return aiAssistance;
	}

	public void setAiAssistance(boolean aiAssistance) {
		this.aiAssistance = aiAssistance;
	}

	public boolean isNotifications() {
		return notifications;
	}

	public void setNotifications(boolean notifications) {
		this.notifications = notifications;
	}
}
