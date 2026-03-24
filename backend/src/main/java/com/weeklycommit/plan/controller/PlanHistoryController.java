package com.weeklycommit.plan.controller;

import com.weeklycommit.plan.dto.WeeklyPlanHistoryEntry;
import com.weeklycommit.plan.service.PlanHistoryService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlanHistoryController {

	private final PlanHistoryService planHistoryService;

	public PlanHistoryController(PlanHistoryService planHistoryService) {
		this.planHistoryService = planHistoryService;
	}

	@GetMapping("/api/users/{userId}/plan-history")
	public ResponseEntity<List<WeeklyPlanHistoryEntry>> getPlanHistory(@PathVariable UUID userId) {
		return ResponseEntity.ok(planHistoryService.getPlanHistory(userId));
	}
}
