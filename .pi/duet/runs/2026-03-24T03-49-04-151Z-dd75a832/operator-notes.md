## Operator note — 2026-03-24T14:22:02.774Z

This step is BACKEND ONLY. Do NOT touch any frontend files. Work exclusively in backend/src/. You must: (1) Create Flyway migration V3__add_ticket_priority.sql, (2) Add TicketPriority
 │ enum, PagedTicketResponse, TicketDetailResponse, TicketSummaryResponse, LinkedCommitEntry, CreateTicketFromCommitRequest records, (3) Add paginated/filterable ticket list endpoint, (4)
 │ Add /api/tickets/summaries endpoint, (5) Add /api/tickets/from-commit endpoint, (6) Add /api/users/{userId}/plan-history endpoint, (7) Add /api/teams/{teamId}/history endpoint, (8) Add
 │ /api/commits/{commitId}/carry-forward-lineage alias. All work in Java backend only. The frontend files already exist and should NOT be modified in this step
