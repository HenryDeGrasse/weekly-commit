# UI/UX Decision Guide — Research-Backed Recommendations

Generated from ChatGPT Pro/Deep Research + Claude Research analysis of the Weekly Commit Module frontend, cross-referenced against enterprise tool patterns (Linear, Notion, GitHub, Jira, Monday.com).

> **Implementation status:** All six decisions have been acted on and are **fully implemented**. See the Implementation Status table at the end for file-level evidence. The body of each decision below preserves the original research and reasoning; implementation notes are added where the final code diverged from the initial recommendation.

---

## Decision 1: Semantic Colors in the Monochrome System

### Problem
The original light-mode palette used gray shades for all status indicators — success, warning, and danger were visually indistinguishable. This made it nearly impossible to quickly scan compliance status, risk signals, and plan states.

### Research Findings
- **Linear** (known for minimal design) uses semantic colored dots for issue status — **not monochrome**. The chrome recedes, but status colors are always present.
- **GitHub** uses subtle colored badges + strong red only for errors.
- **Best practice**: "Low-chroma by default, high-chroma on change" — neutral gray for steady state, muted semantic tones for status.
- **WCAG implication**: Gray-on-gray status indicators fail contrast requirements and cannot be distinguished by colorblind users.

### Recommendation: Add Muted Semantic Colors (Light Mode)
Keep the monochrome base but introduce **desaturated, restrained semantic colors** for status components only.

**Always pair color with icon + text label** for redundancy (e.g., ✓ Compliant + green, ⚠ At Risk + amber).

**Scope of change**: Only status components — `Badge`, `ComplianceBadge`, `PlanStateBadge`, risk banners, exception severity indicators. Keep all chrome, navigation, and card backgrounds monochrome.

### Implementation

Implemented in `index.css` with WCAG AA-validated tokens. Base text colors were tuned from the initial research values to achieve stronger contrast ratios (≥4.5:1 against their respective bg tokens):

| Token | Light Mode | Dark Mode | Usage |
|-------|-----------|-----------|-------|
| **Success base** | `#3D7A4A` | `#8FBF9A` | Text/icons for compliant, achieved |
| **Success bg** | `#EEF3EF` | `#1A221C` | Badges, row highlights |
| **Success border** | `#C7D6CB` | `#2F3E33` | Card borders, left indicators |
| **Warning base** | `#7A6520` | `#C4AC6A` | At-risk, over-budget |
| **Warning bg** | `#F5F2EA` | `#211F18` | Alert backgrounds |
| **Warning border** | `#DDD2B8` | `#3A3526` | Border accents |
| **Danger base** | `#9B3B3B` | `#D4A0A0` | Missed deadline, critical |
| **Danger bg** | `#F6EEEE` | `#221A1A` | Error backgrounds |
| **Danger border** | `#E2CACA` | `#3B2A2A` | Border accents |
| **Info/AI base** | `#4A5578` | `#9DABC8` | AI suggestions, info |
| **Info/AI bg** | `#EEF1F6` | `#1A1D24` | AI panel backgrounds |
| **Info/AI border** | `#CCD3E0` | `#2F3542` | AI component borders |

Consumed by `Badge`, `Button`, `Toast`, and `Input` components via variant props. Used across 20+ component files — error states, compliance badges, reconcile status, exception severity, form validation.

---

## Decision 2: Progressive Disclosure on My Week Page

### Problem
My Week renders 12+ sections simultaneously: plan header, risk banners, AI insights, lint panel, capacity meter, soft warnings, commit list, what-if planner, scope change timeline, AI composer, plan history, carry-forward lineage.

### Research Findings
- **Best practice**: Use a **hierarchical combination** — tabs for top-level, collapsible sections within, separate pages for deep workflows.
- **Linear** uses "role × recency × anomaly" to set smart defaults — expand what needs action, collapse the rest.
- **Key heuristic**: If users need to compare → tabs. If users need to scan → collapsibles. If users need to focus → new page.

### Recommendation: Smart Defaults + Collapsible Sections

**Always visible** (core workflow):
- Plan header with state badge + action buttons
- Capacity meter
- Commit list (the primary interaction surface)

**Contextually visible** (appear only when relevant):
- Risk banners → only when plan is LOCKED and risks exist
- Auto-lock system banner → only when system-locked
- Scope change timeline → only when post-lock changes exist
- Pre-lock validation → only when user clicks Lock

**Default collapsed** (available on demand):
- AI Lint Panel → collapsed with summary badge "3 suggestions" — expand to see details
- AI Insights → collapsed with one-line preview
- What-If Planner → already collapsed (good ✓)
- Plan History → already collapsed (good ✓)
- Carry-forward Lineage → already collapsed (good ✓)
- Soft Warnings → collapsed unless critical (>8 commits or >40% pawns)

**Persist expansion state** to localStorage so returning users see their preferred layout.

**Add "Expand all / Collapse all"** toggle for power users.

### Implementation

`CollapsibleSection` component with `usePersistedState` hook persists open/close state to localStorage per section (`wc-section-{id}` keys). Supports `overrideExpanded` prop for "Expand all / Collapse all" controls. Animated height via CSS grid `0fr↔1fr` trick. Accessibility: `aria-expanded`, `aria-controls`, `role="region"`. Used in MyWeek, TeamWeek, and all AI sections.

---

## Decision 3: AI Suggestion UX Patterns

### Problem
The app has 10+ AI touchpoints. Risk of "AI fatigue" — users learn to ignore suggestions.

### Research Findings
- **Gold standard (2024-2025)**: Ghost text / inline suggestions that require **affirmative action to accept** (opt-in), not opt-out to reject.
- **Graduated engagement model**:
  - Level 1 (Passive): Subtle inline hints, ghost text
  - Level 2 (Contextual): Appear only after user action (on save, on empty state)
  - Level 3 (Explicit): User clicks "Generate" or "Suggest"
- **Fatigue prevention**: Respect dismissal memory, confidence gating, rate limiting per session, per-component "turn off" option.
- **AI pre-fill best practice**: Visual distinction (different background, "AI Suggested" badge), but content is editable inline — NOT a separate accept/reject modal for each field.

### Recommendation: Tiered AI Surface

| AI Capability | Current Pattern | Recommended Pattern |
|---|---|---|
| **Commit Lint** | Auto-runs, shows panel | ✅ Good — make it a **collapsed summary badge** ("3 hints") that expands on click |
| **AI Commit Composer** | Modal dialog | ✅ Good — explicit invocation via button |
| **RCDO Suggestion** | Inline during editing | ✅ Good — keep as inline ghost suggestion |
| **Reconcile pre-fill** | Auto-fills outcomes | ⚠️ Change to **ghost/preview state** — show suggested outcomes as faded text that solidifies on click, not pre-selected |
| **Risk Banners** | Always visible when locked | ⚠️ Show **count badge** first, expand to details on click |
| **AI Insights** | Auto-expanded panel | ⚠️ Collapse by default with one-line summary |
| **Manager Summary** | Auto-expanded card | ⚠️ Show summary header, expand details on click |
| **Semantic Search** | Input always visible | ✅ Good — explicit invocation |
| **What-If** | Collapsed by default | ✅ Good |
| **Draft Assist** | On-demand button | ✅ Good |

**Add per-component dismiss memory**: If a user dismisses lint suggestions 3x in a row, collapse by default for that user.

**Add global AI preference**: Settings toggle to reduce AI suggestions to "on-demand only" mode.

### Implementation

All AI panels (lint, insights, risk signals, what-if, plan recommendations, manager summary, calibration) default to collapsed inside `CollapsibleSection` wrappers with summary badges. Explicit invocation via buttons for Draft Assist, Commit Composer, and Semantic Search.

---

## Decision 4: Typography — Dual Font Stack

### Problem
The original design used Geist Mono as the only font for all text (headings, body, labels, navigation, charts). Users spend 10-15 min/session in this tool.

### Research Findings
- **Monospace is NOT recommended as primary UI font** for form-heavy enterprise tools.
- Downsides: lower readability for dense text, poor visual hierarchy (uniform character width), feels overly technical.
- **Linear uses Inter** (proportional sans-serif) as its primary font.
- **Best practice**: Sans-serif for UI chrome (labels, navigation, body text), monospace reserved for code, IDs, tabular numeric data.
- **Exception**: Developer-first tools (terminals, observability) can lean heavier on monospace, but still keep UI chrome in sans-serif.

### Recommendation: Dual Font Stack

```css
--font-family-base: "Inter", "Geist Sans", system-ui, sans-serif;
--font-family-mono: "Geist Mono", ui-monospace, monospace;
```

**Use proportional (Inter/Geist Sans) for**:
- Navigation labels
- Page headings
- Card titles
- Button text
- Body text / descriptions
- Form labels

**Keep monospace (Geist Mono) for**:
- Commit titles (they're "code-like" work contracts)
- Ticket IDs
- RCDO paths
- Estimate points
- Date/time values
- Report data cells
- Code-like identifiers

This preserves the "utilitarian/technical" feel while dramatically improving readability for the 70%+ of text that's prose.

### Implementation

Inter loaded from Google Fonts CDN as `--font-family-base`; body element uses `var(--font-family-base)`. Geist Mono reserved for `font-mono` utility class — applied selectively to commit titles, estimate points, date/time values, report data cells, ticket IDs, and chart axes. Navigation, headings, card titles, button text, and body text all render in Inter.

---

## Decision 5: Charts — CSS vs Charting Library

### Problem
The original Reports page rendered 8 chart types (velocity trend with rolling average, planned vs achieved, achievement rate, chess distribution, scope changes, carry-forward, compliance, exception aging) using pure CSS divs.

### Research Findings
- Pure CSS charts are viable for ≤ 2-3 simple chart types with minimal interaction.
- **Switch threshold**: ≥ 4-5 chart types, any time-series, multi-series, or interactive charts.
- The project has **8 types** — already past the threshold.
- Key gaps in current CSS approach: manual accessibility (ARIA roles, screen reader support), fragile tooltips, no coordinated animation, manual responsive scaling, duplicated axis/stacking logic.

### Recommendation: Adopt a Charting Library

| Library | Best For | Bundle | Notes |
|---|---|---|---|
| **Tremor** | Fastest for business dashboards | ~50KB | Opinionated, Tailwind-native, perfect for this stack |
| **Recharts** | Most flexibility | ~80KB | Most popular React chart lib, good docs |
| **Nivo** | Complex visualizations | ~100KB+ | Overkill for this use case |

**Keep pure CSS for**: simple progress bars (capacity meter), inline sparklines, the stacked chess distribution bar.

**Migrate to library for**: velocity trend, planned vs achieved, achievement rate, carry-forward, compliance, scope changes.

### Implementation

**Recharts 3.8.1** was chosen over Tremor for its greater flexibility with custom chart compositions (e.g., `ComposedChart` with mixed bar + line + reference lines for the velocity trend). Reports page renders 13 `ResponsiveContainer` chart instances using `ComposedChart` (velocity trend with rolling average + target reference lines) and `BarChart` (planned vs achieved, achievement rate, chess distribution, scope changes, carry-forward, compliance, exception aging). CSS-only charts retained for the capacity meter progress bar and inline sparklines as recommended.

---

## Decision 6: Loading & Empty States

### Problem
The original implementation showed "Loading…" text on most pages. Skeleton component existed but was barely used. Empty states were minimal text with no guidance.

### Research Findings
- Skeleton loaders are **superior for perceived performance** when layout is predictable and load time is 0.5-2s.
- Add timeout threshold (~2-3s): switch from skeleton → explicit progress state ("Still loading…", retry option).
- Empty states should include **illustration + description + CTA** (e.g., "No commits yet — add your first commit to start planning your week").

### Recommendation: Three-Tier Loading Strategy

**Tier 1 — Skeleton (< 2s expected)**:
- Plan header
- Commit list (show 3-4 skeleton commit rows)
- Team Week overview cards
- Report chart placeholders

**Tier 2 — Progress indicator (2-5s expected)**:
- AI suggestions (show shimmer + "AI is analyzing…")
- Semantic search results

**Tier 3 — Explicit state (> 5s or uncertain)**:
- "Still loading… [Retry]" with elapsed time
- AI unavailable fallback

**Empty states** — add for each major surface:
- My Week (no plan): illustration + "Start your week — add your first commitment"
- Commit list (empty): "No commits yet" + prominent Add button
- Team Week (no team): clear guidance on team selection
- Reports (no data): "Not enough data yet — reports will populate after your first reconciled week"
- RCDO tree (empty): "No strategy nodes — create your first Rally Cry to get started"

### Implementation

Dedicated skeleton components: `PlanHeaderSkeleton`, `CommitListSkeleton`, `TeamWeekSkeleton`, `ReportChartSkeleton`. `LoadingWithTimeout` handles slow-load states with retry. `EmptyState` component (icon + title + description + CTA) used in MyWeek, TeamWeek, Reports, and RCDOs.

---

## Implementation Status

| Decision | Status | Implementation Evidence |
|---|---|---|
| 1. Semantic colors | ✅ **Implemented** | `index.css` defines WCAG AA-validated success/warning/danger/info token sets (base, bg, border, foreground) for both light and dark mode. `Badge`, `Button`, `Toast`, and `Input` components consume these via variant props. Semantic colors are used in 20+ component files across routes and AI surfaces — error states, compliance badges, reconcile status, exception severity, form validation. Light-mode base values pass ≥4.5:1 contrast against their respective backgrounds. |
| 2. Progressive disclosure | ✅ **Implemented** | `CollapsibleSection` component with `usePersistedState` hook persists open/close state to localStorage per section. Used in MyWeek, TeamWeek, and AI sections. Supports `overrideExpanded` for "Expand all / Collapse all" controls. Animated height via CSS grid `0fr↔1fr` trick. Accessibility: `aria-expanded`, `aria-controls`, `role="region"`. |
| 3. AI suggestion tiering | ✅ **Implemented** | All AI panels (lint, insights, risk signals, what-if, plan recommendations, manager summary, calibration) default to collapsed inside `CollapsibleSection` wrappers with summary badges. Explicit invocation via buttons for Draft Assist, Commit Composer, Semantic Search. |
| 4. Typography dual font stack | ✅ **Implemented** | Inter loaded from Google Fonts CDN as `--font-family-base`; body element uses `var(--font-family-base)`. Geist Mono reserved for `font-mono` utility class — applied selectively to commit titles, estimate points, date/time values, report data cells, ticket IDs, and chart axes. Navigation, headings, card titles, button text, body text all render in Inter. |
| 5. Loading/empty states | ✅ **Implemented** | Dedicated skeleton components: `PlanHeaderSkeleton`, `CommitListSkeleton`, `TeamWeekSkeleton`, `ReportChartSkeleton`. `LoadingWithTimeout` handles slow-load states with retry. `EmptyState` component (icon + title + description + CTA) used in MyWeek, TeamWeek, Reports, and RCDOs. |
| 6. Charts library migration | ✅ **Implemented** | Recharts 3.8.1 installed. Reports page renders 13 `ResponsiveContainer` chart instances using `ComposedChart` (velocity trend with rolling average + reference lines) and `BarChart` (planned vs achieved, achievement rate, chess distribution, scope changes, carry-forward, compliance, exception aging). CSS-only charts retained for capacity meter progress bar and inline sparklines. |

---

## Sources
- ChatGPT Pro extended analysis (March 2026)
- Claude Research with web search (March 2026)
- Linear design system analysis
- GitHub/Notion/Jira pattern comparison
- WCAG 2.1 AA accessibility guidelines
