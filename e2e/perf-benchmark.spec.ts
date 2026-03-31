/**
 * Frontend Performance Benchmark
 *
 * Measures real browser page load times for the primary surfaces.
 * PRD target: P95 initial page load for My Week and Team Week < 2.5s.
 *
 * Collects Navigation Timing and Largest Contentful Paint (LCP) metrics
 * via PerformanceObserver. Runs N iterations per page and computes P50/P95/P99.
 *
 * Usage:
 *   npx playwright test --config=e2e/playwright.config.ts e2e/perf-benchmark.spec.ts
 *
 * Results written to: scripts/perf-results-frontend.json
 */
import { test, expect, type Page } from "@playwright/test";
import * as fs from "fs";
import * as path from "path";

// ── Configuration ───────────────────────────────────────────────────────────
const ITERATIONS = parseInt(process.env.PERF_ITERATIONS ?? "20", 10);
const BASE_URL = "http://localhost:5173";

interface PageTiming {
  url: string;
  ttfb_ms: number;
  dom_content_loaded_ms: number;
  load_ms: number;
  lcp_ms: number | null;
}

interface BenchmarkResult {
  name: string;
  route: string;
  iterations: number;
  timings: {
    ttfb: Stats;
    dom_content_loaded: Stats;
    load: Stats;
    lcp: Stats;
  };
}

interface Stats {
  min_ms: number;
  avg_ms: number;
  p50_ms: number;
  p90_ms: number;
  p95_ms: number;
  p99_ms: number;
  max_ms: number;
}

function computeStats(values: number[]): Stats {
  if (values.length === 0) {
    return { min_ms: 0, avg_ms: 0, p50_ms: 0, p90_ms: 0, p95_ms: 0, p99_ms: 0, max_ms: 0 };
  }
  const sorted = [...values].sort((a, b) => a - b);
  const n = sorted.length;
  return {
    min_ms: Math.round(sorted[0]),
    avg_ms: Math.round(sorted.reduce((a, b) => a + b, 0) / n),
    p50_ms: Math.round(sorted[Math.floor(n * 0.5)]),
    p90_ms: Math.round(sorted[Math.floor(n * 0.9)]),
    p95_ms: Math.round(sorted[Math.floor(n * 0.95)]),
    p99_ms: Math.round(sorted[Math.min(Math.floor(n * 0.99), n - 1)]),
    max_ms: Math.round(sorted[n - 1]),
  };
}

/**
 * Navigate to a page and collect timing metrics.
 * Uses a fresh page context each time to avoid cache effects.
 */
async function measurePageLoad(page: Page, url: string): Promise<PageTiming> {
  // Inject LCP observer before navigation
  await page.addInitScript(() => {
    (window as any).__lcp_value = null;
    const observer = new PerformanceObserver((list) => {
      const entries = list.getEntries();
      if (entries.length > 0) {
        (window as any).__lcp_value = entries[entries.length - 1].startTime;
      }
    });
    observer.observe({ type: "largest-contentful-paint", buffered: true });
  });

  await page.goto(url, { waitUntil: "load" });

  // Wait a bit for LCP to finalize
  await page.waitForTimeout(500);

  const timing = await page.evaluate(() => {
    const nav = performance.getEntriesByType("navigation")[0] as PerformanceNavigationTiming;
    return {
      ttfb_ms: nav.responseStart - nav.startTime,
      dom_content_loaded_ms: nav.domContentLoadedEventEnd - nav.startTime,
      load_ms: nav.loadEventEnd - nav.startTime,
      lcp_ms: (window as any).__lcp_value as number | null,
    };
  });

  return { url, ...timing };
}

// ── Pages to benchmark ──────────────────────────────────────────────────────
const PAGES = [
  { name: "My Week", route: "/weekly/my-week" },
  { name: "Team Week", route: "/weekly/team-week" },
  { name: "Reconcile", route: "/weekly/reconcile" },
  { name: "Tickets", route: "/weekly/tickets" },
  { name: "RCDOs", route: "/weekly/rcdos" },
  { name: "Reports", route: "/weekly/reports" },
];

test.describe("Frontend Performance Benchmark", () => {
  const allResults: BenchmarkResult[] = [];

  for (const pageConfig of PAGES) {
    test(`${pageConfig.name} page load (${ITERATIONS} iterations)`, async ({ browser }) => {
      const ttfbValues: number[] = [];
      const dclValues: number[] = [];
      const loadValues: number[] = [];
      const lcpValues: number[] = [];

      for (let i = 0; i < ITERATIONS; i++) {
        // Fresh context each iteration to avoid caching
        const context = await browser.newContext();
        const page = await context.newPage();

        try {
          const timing = await measurePageLoad(page, `${BASE_URL}${pageConfig.route}`);
          ttfbValues.push(timing.ttfb_ms);
          dclValues.push(timing.dom_content_loaded_ms);
          loadValues.push(timing.load_ms);
          if (timing.lcp_ms !== null) {
            lcpValues.push(timing.lcp_ms);
          }
        } finally {
          await context.close();
        }
      }

      const result: BenchmarkResult = {
        name: pageConfig.name,
        route: pageConfig.route,
        iterations: ITERATIONS,
        timings: {
          ttfb: computeStats(ttfbValues),
          dom_content_loaded: computeStats(dclValues),
          load: computeStats(loadValues),
          lcp: computeStats(lcpValues),
        },
      };

      allResults.push(result);

      // Assert PRD targets — My Week and Team Week must be < 2500ms P95 load
      if (pageConfig.name === "My Week" || pageConfig.name === "Team Week") {
        console.log(`  ${pageConfig.name}: P95 load = ${result.timings.load.p95_ms}ms (target: <2500ms)`);
        expect(result.timings.load.p95_ms).toBeLessThan(2500);
      }
    });
  }

  test.afterAll(async () => {
    // Write results JSON
    const output = {
      timestamp: new Date().toISOString(),
      iterations_per_page: ITERATIONS,
      prd_target_p95_page_load_ms: 2500,
      environment: "local dev (Vite HMR + Spring Boot)",
      results: allResults,
    };

    const resultsPath = path.join(__dirname, "..", "scripts", "perf-results-frontend.json");
    fs.writeFileSync(resultsPath, JSON.stringify(output, null, 2));

    // Print summary
    console.log("\n══════════════════════════════════════════════════════════════");
    console.log("  Frontend Page Load Benchmark Results");
    console.log("══════════════════════════════════════════════════════════════\n");
    console.log(
      `${"Page".padEnd(15)} ${"P50".padStart(8)} ${"P90".padStart(8)} ${"P95".padStart(8)} ${"P99".padStart(8)} ${"LCP P95".padStart(10)} ${"Pass".padStart(6)}`
    );
    console.log("─".repeat(70));

    for (const r of allResults) {
      const isPrdPage = r.name === "My Week" || r.name === "Team Week";
      const target = isPrdPage ? 2500 : 2500; // apply same threshold for context
      const pass = r.timings.load.p95_ms < target ? "✓" : "✗";
      const lcpStr = r.timings.lcp.p95_ms > 0 ? `${r.timings.lcp.p95_ms}ms` : "N/A";
      console.log(
        `${r.name.padEnd(15)} ${(r.timings.load.p50_ms + "ms").padStart(8)} ${(r.timings.load.p90_ms + "ms").padStart(8)} ${(r.timings.load.p95_ms + "ms").padStart(8)} ${(r.timings.load.p99_ms + "ms").padStart(8)} ${lcpStr.padStart(10)} ${pass.padStart(6)}`
      );
    }

    console.log("─".repeat(70));
    console.log(`  Results saved to: scripts/perf-results-frontend.json\n`);
  });
});
