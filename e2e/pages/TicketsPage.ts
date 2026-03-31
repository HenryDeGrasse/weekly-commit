import { type Page, type Locator, expect } from "@playwright/test";

/**
 * Page Object Model for the Tickets page.
 */
export class TicketsPage {
  readonly page: Page;
  readonly pageContainer: Locator;
  readonly createTicketBtn: Locator;
  readonly ticketCount: Locator;
  readonly ticketListTable: Locator;
  readonly ticketListEmpty: Locator;
  readonly ticketListLoading: Locator;
  readonly ticketFormDialog: Locator;
  readonly ticketDetailPanel: Locator;
  readonly filters: Locator;
  readonly filterClearAll: Locator;
  readonly pagination: Locator;

  constructor(page: Page) {
    this.page = page;
    this.pageContainer = page.getByTestId("page-tickets");
    this.createTicketBtn = page.getByTestId("create-ticket-btn");
    this.ticketCount = page.getByTestId("ticket-count");
    this.ticketListTable = page.getByTestId("ticket-list-table");
    this.ticketListEmpty = page.getByTestId("ticket-list-empty");
    this.ticketListLoading = page.getByTestId("ticket-list-loading");
    this.ticketFormDialog = page.getByTestId("ticket-form-dialog");
    this.ticketDetailPanel = page.getByTestId("ticket-detail-panel");
    this.filters = page.getByTestId("ticket-filters");
    this.filterClearAll = page.getByTestId("filter-clear-all");
    this.pagination = page.getByTestId("ticket-list-pagination");
  }

  async goto() {
    await this.page.goto("/weekly/tickets");
    await this.pageContainer.waitFor({ timeout: 10000 });
  }

  async waitForListLoaded() {
    // Wait until either the table or empty state is visible (loading finished)
    await expect(
      this.ticketListTable.or(this.ticketListEmpty),
    ).toBeVisible({ timeout: 10000 });
  }

  async openCreateForm() {
    await this.createTicketBtn.click();
    await expect(this.ticketFormDialog).toBeVisible({ timeout: 5000 });
  }

  async fillAndSubmitTicket(opts: {
    title: string;
    priority?: string;
    team?: string;
    reporter?: string;
    description?: string;
    status?: string;
    assignee?: string;
    estimate?: string;
    targetWeek?: string;
  }) {
    await this.page.getByTestId("ticket-form-title").fill(opts.title);

    if (opts.priority) {
      await this.page.getByTestId("ticket-form-priority").selectOption(opts.priority);
    }
    if (opts.team) {
      await this.page.getByTestId("ticket-form-team").fill(opts.team);
    }
    if (opts.reporter) {
      await this.page.getByTestId("ticket-form-reporter").fill(opts.reporter);
    }
    if (opts.description) {
      await this.page.getByTestId("ticket-form-description").fill(opts.description);
    }
    if (opts.status) {
      await this.page.getByTestId("ticket-form-status").selectOption(opts.status);
    }
    if (opts.assignee) {
      await this.page.getByTestId("ticket-form-assignee").fill(opts.assignee);
    }
    if (opts.estimate) {
      await this.page.getByTestId("ticket-form-estimate").selectOption(opts.estimate);
    }
    if (opts.targetWeek) {
      await this.page.getByTestId("ticket-form-target-week").fill(opts.targetWeek);
    }

    await this.page.getByTestId("ticket-form-submit").click();
  }

  async selectTicketRow(index: number) {
    const rows = this.ticketListTable.locator("tbody tr");
    await rows.nth(index).click();
    await expect(this.ticketDetailPanel).toBeVisible({ timeout: 5000 });
  }

  async closeDetailPanel() {
    await this.page.getByTestId("ticket-detail-close-btn").click();
    await expect(this.ticketDetailPanel).toBeHidden({ timeout: 3000 });
  }

  async setFilter(testId: string, value: string) {
    const el = this.page.getByTestId(testId);
    const tagName = await el.evaluate((e) => e.tagName.toLowerCase());
    if (tagName === "select") {
      await el.selectOption(value);
    } else {
      await el.fill(value);
    }
  }
}
