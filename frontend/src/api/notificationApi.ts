/**
 * Notification API calls mapping to NotificationController endpoints.
 */
import type { ApiClient } from "./client.js";

// ── Response types ──────────────────────────────────────────────────────────

export interface NotificationResponse {
  readonly id: string;
  readonly notificationType: string;
  readonly title: string;
  readonly body: string;
  readonly priority: "HIGH" | "MEDIUM" | "LOW";
  readonly referenceId: string | null;
  readonly referenceType: string | null;
  readonly read: boolean;
  readonly createdAt: string;
}

export interface PagedNotificationResponse {
  readonly items: NotificationResponse[];
  readonly total: number;
  readonly page: number;
  readonly pageSize: number;
  readonly unreadCount: number;
}

// ── API factory ─────────────────────────────────────────────────────────────

export function createNotificationApi(
  client: ApiClient,
  actorUserId: string,
) {
  const actorHeader = { "X-Actor-User-Id": actorUserId };

  return {
    /** Fetches paginated notifications. Optional read filter (true/false/omit). */
    list: (
      page = 1,
      pageSize = 20,
      read?: boolean,
    ): Promise<PagedNotificationResponse> => {
      const params = new URLSearchParams();
      params.set("page", String(page));
      params.set("pageSize", String(pageSize));
      if (read !== undefined) params.set("read", String(read));
      return client.get(`/notifications?${params.toString()}`, {
        headers: actorHeader,
      });
    },

    /** Marks a single notification as read. */
    markAsRead: (notificationId: string): Promise<void> =>
      client.put(`/notifications/${encodeURIComponent(notificationId)}/read`, {}, {
        headers: actorHeader,
      }),

    /** Marks all notifications as read. */
    markAllAsRead: (): Promise<void> =>
      client.put("/notifications/read-all", {}, { headers: actorHeader }),
  };
}

export type NotificationApi = ReturnType<typeof createNotificationApi>;
