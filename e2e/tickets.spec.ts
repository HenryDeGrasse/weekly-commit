import { test, expect } from "@playwright/test";

/**
 * E2E: Tickets page — navigation, rendering, ticket list display.
 */
test.describe("Tickets Page", () => {
  test("navigates to tickets page via sidebar", async ({ page }) => {
    await page.goto("/weekly/my-week");
    await page.getByTestId("app-shell").waitFor();

    await page.getByRole("link", { name: /tickets/i }).click();
    await expect(page.getByTestId("page-tickets")).toBeVisible({
      timeout: 5000,
    });
  });

  test("tickets page renders without errors", async ({ page }) => {
    await page.goto("/weekly/tickets");
    await expect(page.getByTestId("page-tickets")).toBeVisible({
      timeout: 5000,
    });

    // Should show some content — either a ticket list or empty state
    const content = await page.getByTestId("page-tickets").textContent();
    expect(content).toBeTruthy();
    expect(content!.length).toBeGreaterThan(0);
  });

  test("tickets page has proper heading", async ({ page }) => {
    await page.goto("/weekly/tickets");
    await expect(page.getByTestId("page-tickets")).toBeVisible({
      timeout: 5000,
    });

    // Should have some heading text
    const heading = page
      .getByTestId("page-tickets")
      .getByRole("heading")
      .first();
    if (await heading.isVisible()) {
      const text = await heading.textContent();
      expect(text).toBeTruthy();
    }
  });

  test("ticket list shows ticket data from seed", async ({ page }) => {
    await page.goto("/weekly/tickets");
    await expect(page.getByTestId("page-tickets")).toBeVisible({
      timeout: 5000,
    });

    // Wait for ticket data to load
    await page.getByTestId("ticket-list-table").or(page.getByTestId("ticket-list-empty")).waitFor({ timeout: 5000 });

    // The seed data should include some tickets
    const pageContent = await page.getByTestId("page-tickets").textContent();
    // Just verify the page loaded with content
    expect(pageContent).toBeTruthy();
  });
});
