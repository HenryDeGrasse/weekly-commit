# UI/UX Decision Guide — Research-Backed Recommendations

Generated from ChatGPT Pro/Deep Research + Claude Research analysis of the Weekly Commit Module frontend, cross-referenced against enterprise tool patterns (Linear, Notion, GitHub, Jira, Monday.com).

---

## Decision 1: Semantic Colors in the Monochrome System

### Problem
The current light-mode palette uses gray shades for success (#404040), warning (#737373), and danger (#171717). This makes it nearly impossible to quickly scan compliance status, risk signals, and plan states.

### Research Findings
- **Linear** (known for minimal design) uses semantic colored dots for issue status — **not monochrome**. The chrome recedes, but status colors are always present.
- **GitHub** uses subtle colored badges + strong red only for errors.
- **Best practice**: "Low-chroma by default, high-chroma on change" — neutral gray for steady state, muted semantic tones for status.
- **WCAG implication**: Gray-on-gray status indicators fail contrast requirements and cannot be distinguished by colorblind users.

### Recommendation: Add Muted Semantic Colors (Light Mode)
Keep the monochrome base but introduce **desaturated, restrained semantic colors** for status components only:

| Token | Light Mode | Dark Mode | Usage |
|-------|-----------|-----------|-------|
| **Success base** | `#5F7A66` | `#7E9B87` | Text/icons for compliant, achieved |
| **Success bg** | `#EEF3EF` | `#1A221C` | Badges, row highlights |
| **Success border** | `#C7D6CB` | `#2F3E33` | Card borders, left indicators |
| **Warning base** | `#8A7A5A` | `#A39473` | At-risk, over-budget |
| **Warning bg** | `#F5F2EA` | `#211F18` | Alert backgrounds |
| **Warning border** | `#DDD2B8` | `#3A3526` | Border accents |
| **Danger base** | `#8A5C5C` | `#A37878` | Missed deadline, critical |
| **Danger bg** | `#F6EEEE` | `#221A1A` | Error backgrounds |
| **Danger border** | `#E2CACA` | `#3B2A2A` | Border accents |
| **Info/AI base** | `#5F6B85` | `#7C8AA6` | AI suggestions, info |
| **Info/AI bg** | `#EEF1F6` | `#1A1D24` | AI panel backgrounds |
| **Info/AI border** | `#CCD3E0` | `#2F3542` | AI component borders |

**Always pair color with icon + text label** for redundancy (e.g., ✓ Compliant + green, ⚠ At Risk + amber).

**Scope of change**: Only status components — `Badge`, `ComplianceBadge`, `PlanStateBadge`, risk banners, exception severity indicators. Keep all chrome, navigation, and card backgrounds monochrome.

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

---

## Decision 4: Typography — Monospace as Primary Font

### Problem
Geist Mono is used as the **only** font for all text (headings, body, labels, navigation, charts). Users spend 10-15 min/session.

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

---

## Decision 5: Charts — CSS vs Charting Library

### Problem
Reports page renders 8 chart types (velocity trend with rolling average, planned vs achieved, achievement rate, chess distribution, scope changes, carry-forward, compliance, exception aging) using pure CSS divs. No charting library.

### Research Findings
- Pure CSS charts are viable for ≤ 2-3 simple chart types with minimal interaction.
- **Switch threshold**: ≥ 4-5 chart types, any time-series, multi-series, or interactive charts.
- The project has **8 types** — already past the threshold.
- Key gaps in current CSS approach: manual accessibility (ARIA roles, screen reader support), fragile tooltips, no coordinated animation, manual responsive scaling, duplicated axis/stacking logic.

### Recommendation: Adopt Recharts or Tremor

| Library | Best For | Bundle | Notes |
|---|---|---|---|
| **Tremor** | Fastest for business dashboards | ~50KB | Opinionated, Tailwind-native, perfect for this stack |
| **Recharts** | Most flexibility | ~80KB | Most popular React chart lib, good docs |
| **Nivo** | Complex visualizations | ~100KB+ | Overkill for this use case |

**Recommended: Tremor** — it's Tailwind-native, designed for exactly this kind of enterprise dashboard, and ships with accessible, responsive charts out of the box.

**Keep pure CSS for**: simple progress bars (capacity meter), inline sparklines, the stacked chess distribution bar.

**Migrate to library for**: velocity trend, planned vs achieved, achievement rate, carry-forward, compliance, scope changes.

---

## Decision 6: Loading & Empty States

### Problem
Most pages show "Loading…" text. Skeleton component exists but is barely used. Empty states are minimal text.

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

---

## Priority Order for Implementation

1. **Semantic colors** (highest impact, lowest effort) — swap 6 CSS vars
2. **Typography dual font stack** (high readability impact) — swap font vars, add font-mono utility classes
3. **Progressive disclosure on My Week** (reduce overwhelm) — wrap sections in collapsible containers
4. **Loading/empty states** (perceived quality) — add skeleton variants
5. **AI suggestion tiering** (reduce fatigue) — adjust default collapsed states
6. **Charts library migration** (medium effort, polish) — incremental per-chart migration

---

## Sources
- ChatGPT Pro extended analysis (March 2025)
- Claude Research with web search (March 2025)
- Linear design system analysis
- GitHub/Notion/Jira pattern comparison
- WCAG 2.1 AA accessibility guidelines
