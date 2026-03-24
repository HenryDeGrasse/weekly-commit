package com.weeklycommit.ticket.dto;

import java.util.List;

public record PagedTicketResponse(List<TicketSummaryResponse> items, long total, int page, int pageSize) {
}
