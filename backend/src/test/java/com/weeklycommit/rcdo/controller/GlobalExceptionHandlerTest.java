package com.weeklycommit.rcdo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.rcdo.exception.RcdoValidationException;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import com.weeklycommit.team.exception.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tests the GlobalExceptionHandler mappings using standalone MockMvc setup with
 * a minimal test controller that throws each exception type.
 */
class GlobalExceptionHandlerTest {

	private MockMvc mockMvc;

	@RestController
	static class TestExceptionController {

		@GetMapping("/test/not-found")
		public void notFound() {
			throw new ResourceNotFoundException("Thing not found");
		}

		@GetMapping("/test/plan-validation")
		public void planValidation() {
			throw new PlanValidationException("Plan is invalid");
		}

		@GetMapping("/test/rcdo-validation")
		public void rcdoValidation() {
			throw new RcdoValidationException("RCDO constraint violated");
		}

		@GetMapping("/test/access-denied")
		public void accessDenied() {
			throw new AccessDeniedException("Not authorized");
		}
	}

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new TestExceptionController())
				.setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	@Test
	void resourceNotFound_returns404() throws Exception {
		mockMvc.perform(get("/test/not-found")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("Thing not found"));
	}

	@Test
	void planValidation_returns400() throws Exception {
		mockMvc.perform(get("/test/plan-validation")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Plan is invalid"));
	}

	@Test
	void rcdoValidation_returns400() throws Exception {
		mockMvc.perform(get("/test/rcdo-validation")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("RCDO constraint violated"));
	}

	@Test
	void accessDenied_returns403() throws Exception {
		mockMvc.perform(get("/test/access-denied")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("Not authorized"));
	}
}
