package com.weeklycommit.plan.service;

import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.CommitOutcome;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.plan.dto.WeeklyPlanHistoryEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlanHistoryService {

	private final WeeklyPlanRepository planRepo;
	private final WeeklyCommitRepository commitRepo;

	public PlanHistoryService(WeeklyPlanRepository planRepo, WeeklyCommitRepository commitRepo) {
		this.planRepo = planRepo;
		this.commitRepo = commitRepo;
	}

	public List<WeeklyPlanHistoryEntry> getPlanHistory(UUID userId) {
		return planRepo.findByOwnerUserIdOrderByWeekStartDateDesc(userId).stream().map(this::toEntry).toList();
	}

	private WeeklyPlanHistoryEntry toEntry(WeeklyPlan plan) {
		List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId());
		int plannedPoints = commits.stream().mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0)
				.sum();
		int achievedPoints = commits.stream().filter(c -> c.getOutcome() == CommitOutcome.ACHIEVED)
				.mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0).sum();
		int carryForwardCount = (int) commits.stream().filter(c -> c.getCarryForwardStreak() > 0).count();
		return new WeeklyPlanHistoryEntry(plan.getId(), plan.getWeekStartDate(), plan.getState(), plan.isCompliant(),
				commits.size(), plannedPoints, achievedPoints, carryForwardCount);
	}
}
