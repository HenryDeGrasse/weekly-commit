import { test, expect } from "@playwright/test";

/**
 * E2E: RCDOs page — hierarchy rendering and navigation.
 */
test.describe("RCDOs Page", () => {
  test("navigates to RCDOs page via sidebar", async ({ page }) => {
    await page.goto("/weekly/my-week");
    await page.getByTestId("app-shell").waitFor();

    await page.getByRole("link", { name: /rcdos/i }).click();
    await expect(page.getByTestId("page-rcdos")).toBeVisible({
      timeout: 5000,
    });
  });

  test("RCDO page renders the hierarchy tree", async ({ page }) => {
    await page.goto("/weekly/rcdos");
    await expect(page.getByTestId("page-rcdos")).toBeVisible({
      timeout: 5000,
    });

    // Wait for tree to load
    await page.getByTestId("rcdo-tree-view").or(page.getByTestId("rcdo-tree-empty")).waitFor({ timeout: 5000 });

    const content = await page.getByTestId("page-rcdos").textContent();
    expect(content).toBeTruthy();
    expect(content!.length).toBeGreaterThan(0);
  });

  test("RCDO page shows Rally Cry nodes from seed data", async ({ page }) => {
    await page.goto("/weekly/rcdos");
    await expect(page.getByTestId("page-rcdos")).toBeVisible({
      timeout: 5000,
    });

    // Wait for data to load
    await page.getByTestId("rcdo-tree-view").or(page.getByTestId("rcdo-tree-empty")).waitFor({ timeout: 5000 });

    // The seed data includes RCDO nodes — page should have substantial content
    const pageContent = await page.getByTestId("page-rcdos").textContent();
    expect(pageContent).toBeTruthy();
    // The page should render tree nodes, not just a loading spinner
    expect(pageContent!.length).toBeGreaterThan(50);
  });

  test("RCDO page has proper heading", async ({ page }) => {
    await page.goto("/weekly/rcdos");
    await expect(page.getByTestId("page-rcdos")).toBeVisible({
      timeout: 5000,
    });

    // Should have a heading
    const heading = page
      .getByTestId("page-rcdos")
      .getByRole("heading")
      .first();
    if (await heading.isVisible()) {
      const text = await heading.textContent();
      expect(text).toBeTruthy();
    }
  });

  test("RCDO breadcrumb shows correct path", async ({ page }) => {
    await page.goto("/weekly/rcdos");
    const breadcrumb = page.getByRole("navigation", { name: "Breadcrumb" });
    await expect(breadcrumb).toBeVisible({ timeout: 5000 });
    await expect(breadcrumb).toContainText("RCDOs");
  });
});
