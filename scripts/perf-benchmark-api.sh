#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# Backend API Performance Benchmark
# ──────────────────────────────────────────────────────────────────────────────
#
# Measures P95 response times for the three PRD performance targets:
#   1. Dashboard query endpoints (target: <1.5s P95)
#   2. Commit CRUD operations   (target: <500ms P95)
#   3. Plan/page-level queries  (target: <2.5s P95)
#
# Prerequisites: backend running on localhost:8080 with demo seed loaded.
# Usage:         ./scripts/perf-benchmark-api.sh [ITERATIONS]
# Default:       100 iterations per endpoint
#
# Output: JSON results to stdout, human-readable summary to stderr,
#         full results to scripts/perf-results-api.json
# ──────────────────────────────────────────────────────────────────────────────

set -euo pipefail

ITERATIONS="${1:-100}"
BASE="http://localhost:8080"

# Known IDs from the demo seed
USER_ID="00000000-0000-0000-0000-000000000001"
TEAM_ID="00000000-0000-0000-0000-000000000010"

# ── Preflight check ──────────────────────────────────────────────────────────
if ! curl -sf "$BASE/health" > /dev/null 2>&1; then
  echo "ERROR: Backend not running at $BASE" >&2
  exit 1
fi

# ── Find a DRAFT plan for commit CRUD benchmarks ─────────────────────────────
DRAFT_PLAN_ID=$(curl -s "$BASE/api/plans?userId=$USER_ID" | python3 -c "
import json, sys
plans = json.load(sys.stdin)
drafts = [p for p in plans if p['state'] == 'DRAFT']
if drafts:
    print(drafts[0]['id'])
else:
    sys.exit(1)
")

# Find a LOCKED plan for read benchmarks
LOCKED_PLAN_ID=$(curl -s "$BASE/api/plans?userId=$USER_ID" | python3 -c "
import json, sys
plans = json.load(sys.stdin)
locked = [p for p in plans if p['state'] == 'LOCKED']
if locked:
    print(locked[0]['id'])
else:
    sys.exit(1)
")

# Find an RCDO node ID
RCDO_NODE=$(curl -s "$BASE/api/rcdo/nodes" | python3 -c "
import json, sys
nodes = json.load(sys.stdin)
outcomes = [n for n in nodes if n.get('nodeType') == 'OUTCOME' and n.get('status') == 'ACTIVE']
if outcomes:
    print(outcomes[0]['id'])
else:
    active = [n for n in nodes if n.get('status') == 'ACTIVE']
    print(active[0]['id'] if active else '')
" 2>/dev/null || echo "")

echo "Benchmark config:" >&2
echo "  Iterations:  $ITERATIONS" >&2
echo "  Draft plan:  $DRAFT_PLAN_ID" >&2
echo "  Locked plan: $LOCKED_PLAN_ID" >&2
echo "  RCDO node:   $RCDO_NODE" >&2
echo "" >&2

# ── Timing function ──────────────────────────────────────────────────────────
# Runs N requests and collects response times in milliseconds
benchmark() {
  local name="$1"
  local method="$2"
  local url="$3"
  local body="${4:-}"
  local headers="${5:-}"
  local n="$ITERATIONS"
  local times_file
  times_file=$(mktemp)

  echo -n "  $name ($n requests)..." >&2

  for ((i=1; i<=n; i++)); do
    if [ "$method" = "GET" ]; then
      curl -sf -o /dev/null -w "%{time_total}\n" \
        -H "X-Actor-User-Id: $USER_ID" \
        $headers \
        "$url" >> "$times_file" 2>/dev/null || true
    elif [ "$method" = "POST" ]; then
      curl -sf -o /dev/null -w "%{time_total}\n" \
        -X POST \
        -H "Content-Type: application/json" \
        -H "X-Actor-User-Id: $USER_ID" \
        $headers \
        -d "$body" \
        "$url" >> "$times_file" 2>/dev/null || true
    elif [ "$method" = "PUT" ]; then
      curl -sf -o /dev/null -w "%{time_total}\n" \
        -X PUT \
        -H "Content-Type: application/json" \
        -H "X-Actor-User-Id: $USER_ID" \
        $headers \
        -d "$body" \
        "$url" >> "$times_file" 2>/dev/null || true
    elif [ "$method" = "DELETE" ]; then
      curl -sf -o /dev/null -w "%{time_total}\n" \
        -X DELETE \
        -H "X-Actor-User-Id: $USER_ID" \
        $headers \
        "$url" >> "$times_file" 2>/dev/null || true
    fi
  done

  # Calculate stats
  python3 -c "
import sys, json

times_ms = []
with open('$times_file') as f:
    for line in f:
        line = line.strip()
        if line:
            try:
                times_ms.append(float(line) * 1000)
            except ValueError:
                pass

if not times_ms:
    print(json.dumps({'name': '$name', 'error': 'no successful requests'}))
    sys.exit(0)

times_ms.sort()
n = len(times_ms)
p50 = times_ms[int(n * 0.50)]
p90 = times_ms[int(n * 0.90)]
p95 = times_ms[int(n * 0.95)]
p99 = times_ms[int(n * 0.99)] if n >= 100 else times_ms[-1]
avg = sum(times_ms) / n
mn  = times_ms[0]
mx  = times_ms[-1]

result = {
    'name': '$name',
    'requests': n,
    'min_ms':   round(mn, 1),
    'avg_ms':   round(avg, 1),
    'p50_ms':   round(p50, 1),
    'p90_ms':   round(p90, 1),
    'p95_ms':   round(p95, 1),
    'p99_ms':   round(p99, 1),
    'max_ms':   round(mx, 1),
}
print(json.dumps(result))
"

  local p95
  p95=$(python3 -c "
times = []
with open('$times_file') as f:
    for l in f:
        l = l.strip()
        if l:
            try: times.append(float(l)*1000)
            except: pass
if times:
    times.sort()
    print(f'{times[int(len(times)*0.95)]:.0f}ms')
else:
    print('N/A')
")
  echo " P95=${p95}" >&2

  rm -f "$times_file"
}

# ── Run benchmarks ───────────────────────────────────────────────────────────
echo "═══════════════════════════════════════════════════════════════" >&2
echo "  Backend API Performance Benchmark" >&2
echo "  $(date -u +"%Y-%m-%dT%H:%M:%SZ")" >&2
echo "═══════════════════════════════════════════════════════════════" >&2
echo "" >&2

RESULTS="["
SEP=""

# ── Category 1: Page / plan queries (PRD target: <2.5s) ──────────────────────
echo "▸ Page-level queries (PRD target: P95 < 2500ms)" >&2

R=$(benchmark "GET /api/plans (list user plans)" "GET" "$BASE/api/plans?userId=$USER_ID")
RESULTS="${RESULTS}${SEP}${R}"; SEP=","

R=$(benchmark "GET /api/plans/{id} (plan detail + commits)" "GET" "$BASE/api/plans/$LOCKED_PLAN_ID")
RESULTS="${RESULTS}${SEP}${R}"

R=$(benchmark "GET /api/plans/{id}/lock-snapshot" "GET" "$BASE/api/plans/$LOCKED_PLAN_ID/lock-snapshot")
RESULTS="${RESULTS}${SEP}${R}"

R=$(benchmark "GET /api/rcdo/nodes (full hierarchy)" "GET" "$BASE/api/rcdo/nodes")
RESULTS="${RESULTS}${SEP}${R}"

R=$(benchmark "GET /api/tickets (list)" "GET" "$BASE/api/tickets?assigneeId=$USER_ID")
RESULTS="${RESULTS}${SEP}${R}"

echo "" >&2

# ── Category 2: Dashboard queries (PRD target: <1.5s) ────────────────────────
echo "▸ Manager dashboard queries (PRD target: P95 < 1500ms)" >&2

R=$(benchmark "GET /api/teams/{id}/week (team week view)" "GET" \
  "$BASE/api/teams/$TEAM_ID/week/2026-03-09" \
  "" "-H X-Actor-User-Id:$USER_ID")
RESULTS="${RESULTS}${SEP}${R}"

R=$(benchmark "GET /api/teams/{id}/week/exceptions" "GET" \
  "$BASE/api/teams/$TEAM_ID/week/2026-03-09/exceptions")
RESULTS="${RESULTS}${SEP}${R}"

R=$(benchmark "GET /api/reports/planned-vs-achieved" "GET" \
  "$BASE/api/reports/planned-vs-achieved?teamId=$TEAM_ID&weekStart=2026-01-05&weekEnd=2028-12-31")
RESULTS="${RESULTS}${SEP}${R}"

R=$(benchmark "GET /api/reports/compliance" "GET" \
  "$BASE/api/reports/compliance?teamId=$TEAM_ID&weekStart=2026-01-05&weekEnd=2028-12-31")
RESULTS="${RESULTS}${SEP}${R}"

R=$(benchmark "GET /api/reports/carry-forward" "GET" \
  "$BASE/api/reports/carry-forward?teamId=$TEAM_ID&weekStart=2026-01-05&weekEnd=2028-12-31")
RESULTS="${RESULTS}${SEP}${R}"

R=$(benchmark "GET /api/reports/chess-distribution" "GET" \
  "$BASE/api/reports/chess-distribution?teamId=$TEAM_ID&weekStart=2026-01-05&weekEnd=2028-12-31")
RESULTS="${RESULTS}${SEP}${R}"

R=$(benchmark "GET /api/reports/scope-changes" "GET" \
  "$BASE/api/reports/scope-changes?teamId=$TEAM_ID&weekStart=2026-01-05&weekEnd=2028-12-31")
RESULTS="${RESULTS}${SEP}${R}"

R=$(benchmark "GET /api/reports/exception-aging" "GET" \
  "$BASE/api/reports/exception-aging?teamId=$TEAM_ID&weekStart=2026-01-05&weekEnd=2028-12-31")
RESULTS="${RESULTS}${SEP}${R}"

R=$(benchmark "GET /api/teams/{id}/history (team trends)" "GET" \
  "$BASE/api/teams/$TEAM_ID/history")
RESULTS="${RESULTS}${SEP}${R}"

echo "" >&2

# ── Category 3: Commit CRUD (PRD target: <500ms) ─────────────────────────────
echo "▸ Commit CRUD operations (PRD target: P95 < 500ms)" >&2

# Use a create → update → delete cycle so we leave no garbage behind.
# Each iteration creates a commit, then we batch-update and batch-delete.
create_times=$(mktemp)
update_times=$(mktemp)
delete_times=$(mktemp)
created_ids=$(mktemp)

echo -n "  Commit CRUD cycle ($ITERATIONS iterations)..." >&2
for ((i=1; i<=ITERATIONS; i++)); do
  # CREATE
  CREATE_RESP=$(curl -sf -w "\n%{time_total}" \
    -X POST \
    -H "Content-Type: application/json" \
    -H "X-Actor-User-Id: $USER_ID" \
    -d "{\"title\":\"__perf_bench_$i\",\"chessPiece\":\"PAWN\",\"priorityOrder\":99,\"estimatePoints\":1,\"rcdoNodeId\":\"$RCDO_NODE\"}" \
    "$BASE/api/plans/$DRAFT_PLAN_ID/commits" 2>/dev/null || echo "")
  CREATE_TIME=$(echo "$CREATE_RESP" | tail -1)
  CREATE_BODY=$(echo "$CREATE_RESP" | sed '$ d')
  echo "$CREATE_TIME" >> "$create_times"
  NEW_ID=$(echo "$CREATE_BODY" | python3 -c "import json,sys; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || echo "")
  [ -n "$NEW_ID" ] && echo "$NEW_ID" >> "$created_ids"

  # UPDATE (same commit we just created)
  if [ -n "$NEW_ID" ]; then
    curl -sf -o /dev/null -w "%{time_total}\n" \
      -X PUT \
      -H "Content-Type: application/json" \
      -H "X-Actor-User-Id: $USER_ID" \
      -d "{\"title\":\"__perf_bench_upd_$i\",\"chessPiece\":\"PAWN\",\"priorityOrder\":1,\"estimatePoints\":2,\"rcdoNodeId\":\"$RCDO_NODE\"}" \
      "$BASE/api/plans/$DRAFT_PLAN_ID/commits/$NEW_ID" >> "$update_times" 2>/dev/null || true
  fi

  # DELETE (clean up immediately)
  if [ -n "$NEW_ID" ]; then
    curl -sf -o /dev/null -w "%{time_total}\n" \
      -X DELETE \
      -H "X-Actor-User-Id: $USER_ID" \
      "$BASE/api/plans/$DRAFT_PLAN_ID/commits/$NEW_ID" >> "$delete_times" 2>/dev/null || true
  fi
done
echo " done" >&2

# Compute stats for each operation
for op_name in "POST commit (create)" "PUT commit (update)" "DELETE commit (delete)"; do
  case "$op_name" in
    *create*) tf="$create_times" ;;
    *update*) tf="$update_times" ;;
    *delete*) tf="$delete_times" ;;
  esac

  R=$(python3 -c "
import sys, json
times_ms = []
with open('$tf') as f:
    for line in f:
        line = line.strip()
        if line:
            try: times_ms.append(float(line) * 1000)
            except ValueError: pass
if not times_ms:
    print(json.dumps({'name': '$op_name', 'error': 'no successful requests'}))
    sys.exit(0)
times_ms.sort()
n = len(times_ms)
print(json.dumps({
    'name': '$op_name', 'requests': n,
    'min_ms': round(times_ms[0], 1),
    'avg_ms': round(sum(times_ms)/n, 1),
    'p50_ms': round(times_ms[int(n*0.50)], 1),
    'p90_ms': round(times_ms[int(n*0.90)], 1),
    'p95_ms': round(times_ms[int(n*0.95)], 1),
    'p99_ms': round(times_ms[min(int(n*0.99), n-1)], 1),
    'max_ms': round(times_ms[-1], 1),
}))
")
  p95_display=$(echo "$R" | python3 -c "import json,sys; print(f\"{json.load(sys.stdin).get('p95_ms',0):.0f}ms\")")
  echo "  $op_name: P95=${p95_display}" >&2
  RESULTS="${RESULTS}${SEP}${R}"
done

# Safety net: delete any orphaned perf commits (in case a cycle was interrupted)
curl -s "$BASE/api/plans/$DRAFT_PLAN_ID" | python3 -c "
import json, sys
data = json.load(sys.stdin)
for c in data.get('commits', []):
    if c.get('title','').startswith('__perf_bench'):
        print(c['id'])
" 2>/dev/null | while read orphan_id; do
  [ -z "$orphan_id" ] && continue
  curl -sf -X DELETE "$BASE/api/plans/$DRAFT_PLAN_ID/commits/$orphan_id" > /dev/null 2>&1
done

rm -f "$create_times" "$update_times" "$delete_times" "$created_ids"

echo "" >&2

RESULTS="${RESULTS}]"

# ── Output results ───────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RESULTS_FILE="$SCRIPT_DIR/perf-results-api.json"

python3 -c "
import json, sys

results = json.loads('''$RESULTS''')
timestamp = '$(date -u +"%Y-%m-%dT%H:%M:%SZ")'
iterations = $ITERATIONS

output = {
    'timestamp': timestamp,
    'iterations_per_endpoint': iterations,
    'prd_targets': {
        'page_load_p95_ms': 2500,
        'dashboard_query_p95_ms': 1500,
        'commit_crud_p95_ms': 500,
    },
    'results': results,
}

# Write JSON file
with open('$RESULTS_FILE', 'w') as f:
    json.dump(output, f, indent=2)

# Print summary table
print()
print('═' * 80)
print('  RESULTS SUMMARY')
print('═' * 80)
print()
print(f'{\"Endpoint\":<55} {\"P95\":>8} {\"Target\":>8} {\"Pass\":>6}')
print('─' * 80)

# Group by category
page_endpoints = [r for r in results if any(k in r['name'] for k in ['list user', 'plan detail', 'lock-snapshot', 'rcdo/nodes', 'tickets'])]
dash_endpoints = [r for r in results if any(k in r['name'] for k in ['team week', 'exceptions', 'reports', 'history'])]
crud_endpoints = [r for r in results if any(k in r['name'] for k in ['commit'])]

def print_group(label, endpoints, target_ms):
    print(f'\n  {label} (target: P95 < {target_ms}ms)')
    all_pass = True
    for r in endpoints:
        if 'error' in r:
            print(f'    {r[\"name\"]:<51} {\"ERR\":>8} {target_ms:>7}ms {\"✗\":>6}')
            all_pass = False
        else:
            p95 = r['p95_ms']
            passed = '✓' if p95 < target_ms else '✗'
            if p95 >= target_ms:
                all_pass = False
            print(f'    {r[\"name\"]:<51} {p95:>7.0f}ms {target_ms:>7}ms {passed:>6}')
    return all_pass

p1 = print_group('Page-level queries', page_endpoints, 2500)
p2 = print_group('Manager dashboard queries', dash_endpoints, 1500)
p3 = print_group('Commit CRUD operations', crud_endpoints, 500)

print()
print('─' * 80)
overall = '✓ ALL TARGETS MET' if (p1 and p2 and p3) else '✗ SOME TARGETS MISSED'
print(f'  Overall: {overall}')
print('─' * 80)
print(f'  Results saved to: $RESULTS_FILE')
print()
" >&2

echo "$RESULTS"
