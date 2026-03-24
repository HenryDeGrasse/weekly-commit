package com.weeklycommit.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiProviderRegistryTest {

	@Mock
	private AiProvider mockProvider;

	private AiProviderRegistry registry;

	@BeforeEach
	void setUp() {
		registry = new AiProviderRegistry(List.of(mockProvider));
	}

	// -------------------------------------------------------------------------
	// isAiEnabled
	// -------------------------------------------------------------------------

	@Test
	void isAiEnabled_whenFlagTrueAndProviderRegistered_returnsTrue() {
		registry.setAiEnabled(true);
		// isAiEnabled() only checks the flag + list non-empty; does not call
		// isAvailable()
		assertThat(registry.isAiEnabled()).isTrue();
	}

	@Test
	void isAiEnabled_whenFlagDisabled_returnsFalse() {
		registry.setAiEnabled(false);

		assertThat(registry.isAiEnabled()).isFalse();
	}

	@Test
	void isAiEnabled_whenNoProviders_returnsFalse() {
		AiProviderRegistry emptyRegistry = new AiProviderRegistry(List.of());

		assertThat(emptyRegistry.isAiEnabled()).isFalse();
	}

	// -------------------------------------------------------------------------
	// getActiveProvider
	// -------------------------------------------------------------------------

	@Test
	void getActiveProvider_whenProviderAvailable_returnsProvider() {
		registry.setAiEnabled(true);
		when(mockProvider.isAvailable()).thenReturn(true);

		assertThat(registry.getActiveProvider()).isPresent().contains(mockProvider);
	}

	@Test
	void getActiveProvider_whenProviderUnavailable_returnsEmpty() {
		registry.setAiEnabled(true);
		when(mockProvider.isAvailable()).thenReturn(false);

		assertThat(registry.getActiveProvider()).isEmpty();
	}

	@Test
	void getActiveProvider_whenAiDisabled_returnsEmptyWithoutCheckingProvider() {
		registry.setAiEnabled(false);

		assertThat(registry.getActiveProvider()).isEmpty();
		// Provider.isAvailable() should not be called when flag is off
		verify(mockProvider, never()).isAvailable();
	}

	// -------------------------------------------------------------------------
	// generateSuggestion — graceful degradation
	// -------------------------------------------------------------------------

	@Test
	void generateSuggestion_whenAiDisabled_returnsUnavailableWithoutCallingProvider() {
		registry.setAiEnabled(false);
		AiContext context = new AiContext(AiContext.TYPE_COMMIT_DRAFT, null, null, null, null, null, null, null, null);

		AiSuggestionResult result = registry.generateSuggestion(context);

		assertThat(result.available()).isFalse();
		verify(mockProvider, never()).generateSuggestion(any());
	}

	@Test
	void generateSuggestion_whenProviderUnavailable_returnsUnavailable() {
		registry.setAiEnabled(true);
		when(mockProvider.isAvailable()).thenReturn(false);
		AiContext context = new AiContext(AiContext.TYPE_COMMIT_DRAFT, null, null, null, null, null, null, null, null);

		AiSuggestionResult result = registry.generateSuggestion(context);

		assertThat(result.available()).isFalse();
	}

	@Test
	void generateSuggestion_whenProviderThrows_returnsUnavailable() {
		registry.setAiEnabled(true);
		when(mockProvider.isAvailable()).thenReturn(true);
		when(mockProvider.generateSuggestion(any())).thenThrow(new RuntimeException("provider error"));
		AiContext context = new AiContext(AiContext.TYPE_COMMIT_DRAFT, null, null, null, null, null, null, null, null);

		AiSuggestionResult result = registry.generateSuggestion(context);

		assertThat(result.available()).isFalse();
	}

	@Test
	void generateSuggestion_whenProviderAvailable_returnsProviderResult() {
		registry.setAiEnabled(true);
		when(mockProvider.isAvailable()).thenReturn(true);
		AiSuggestionResult expected = new AiSuggestionResult(true, "{}", "rationale", 0.9, "model-v1");
		when(mockProvider.generateSuggestion(any())).thenReturn(expected);
		AiContext context = new AiContext(AiContext.TYPE_COMMIT_DRAFT, null, null, null, null, null, null, null, null);

		AiSuggestionResult result = registry.generateSuggestion(context);

		assertThat(result).isEqualTo(expected);
	}

	// -------------------------------------------------------------------------
	// Stub provider
	// -------------------------------------------------------------------------

	@Test
	void stubProvider_isAlwaysAvailable() {
		StubAiProvider stub = new StubAiProvider();
		assertThat(stub.isAvailable()).isTrue();
	}

	@Test
	void stubProvider_returnsAvailableResultForAllKnownTypes() {
		StubAiProvider stub = new StubAiProvider();
		for (String type : List.of(AiContext.TYPE_COMMIT_DRAFT, AiContext.TYPE_COMMIT_LINT, AiContext.TYPE_RCDO_SUGGEST,
				AiContext.TYPE_RISK_SIGNAL, AiContext.TYPE_RECONCILE_ASSIST, AiContext.TYPE_TEAM_SUMMARY)) {
			AiContext ctx = new AiContext(type, null, null, null, null, null, null, null, null);
			AiSuggestionResult res = stub.generateSuggestion(ctx);
			assertThat(res.available()).as("Expected available=true for type %s", type).isTrue();
			assertThat(res.payload()).as("Expected non-null payload for type %s", type).isNotNull();
		}
	}
}
