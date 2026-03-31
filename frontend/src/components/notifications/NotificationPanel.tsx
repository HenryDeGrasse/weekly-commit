/**
 * Notification dropdown panel — attaches to the Bell icon in the header.
 * Shows recent notifications with priority indicators and mark-as-read.
 */
import { useState, useEffect, useCallback, useMemo, useRef } from "react";
import {
  Bell,
  Check,
  CheckCheck,
  AlertTriangle,
  Clock,
  Info,
  X,
} from "lucide-react";
import { Button } from "../ui/Button.js";
import { Badge } from "../ui/Badge.js";
import { useHostBridge } from "../../host/HostProvider.js";
import { createApiClient } from "../../api/client.js";
import {
  createNotificationApi,
  type NotificationResponse,
  type PagedNotificationResponse,
} from "../../api/notificationApi.js";

// ── Priority icon + color mapping ───────────────────────────────────────────

const PRIORITY_CONFIG = {
  HIGH: { icon: AlertTriangle, className: "text-foreground", bgClassName: "bg-foreground/10" },
  MEDIUM: { icon: Clock, className: "text-muted", bgClassName: "bg-foreground/5" },
  LOW: { icon: Info, className: "text-muted", bgClassName: "bg-surface-raised" },
} as const;

function timeAgo(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60_000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}

// ── NotificationPanel ───────────────────────────────────────────────────────

export function NotificationPanel() {
  const bridge = useHostBridge();
  const { authToken, authenticatedUser } = bridge.context;

  const api = useMemo(() => {
    const client = createApiClient({
      baseUrl: API_BASE_URL,
      getAuthToken: () => bridge.context.authToken,
    });
    return createNotificationApi(client, authenticatedUser.id);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authToken, authenticatedUser.id]);

  const [open, setOpen] = useState(false);
  const [data, setData] = useState<PagedNotificationResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const panelRef = useRef<HTMLDivElement>(null);

  // Fetch notifications on open + periodic refresh
  const fetchNotifications = useCallback(async () => {
    setLoading(true);
    try {
      const result = await api.list(1, 15);
      setData(result);
    } catch {
      // silently fail — notification panel is non-critical
    } finally {
      setLoading(false);
    }
  }, [api]);

  // Fetch unread count on mount for badge
  useEffect(() => {
    void fetchNotifications();
    const interval = setInterval(() => void fetchNotifications(), 30_000);
    return () => clearInterval(interval);
  }, [fetchNotifications]);

  // Fetch full list when opening
  useEffect(() => {
    if (open) void fetchNotifications();
  }, [open, fetchNotifications]);

  // Close on outside click
  useEffect(() => {
    if (!open) return;
    function handleClick(e: MouseEvent) {
      if (panelRef.current && !panelRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [open]);

  const handleMarkRead = useCallback(
    async (id: string) => {
      try {
        await api.markAsRead(id);
        setData((prev) => {
          if (!prev) return prev;
          return {
            ...prev,
            unreadCount: Math.max(0, prev.unreadCount - 1),
            items: prev.items.map((n) => (n.id === id ? { ...n, read: true } : n)),
          };
        });
      } catch {
        // ignore
      }
    },
    [api],
  );

  const handleMarkAllRead = useCallback(async () => {
    try {
      await api.markAllAsRead();
      setData((prev) => {
        if (!prev) return prev;
        return {
          ...prev,
          unreadCount: 0,
          items: prev.items.map((n) => ({ ...n, read: true })),
        };
      });
    } catch {
      // ignore
    }
  }, [api]);

  const unreadCount = data?.unreadCount ?? 0;

  return (
    <div ref={panelRef} className="relative" data-testid="notification-panel">
      {/* Bell button */}
      <Button
        variant="ghost"
        size="icon"
        aria-label={`Notifications${unreadCount > 0 ? ` (${unreadCount} unread)` : ""}`}
        className="relative"
        onClick={() => setOpen(!open)}
        data-testid="notification-bell"
      >
        <Bell className="h-4 w-4" />
        {unreadCount > 0 && (
          <span
            className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-foreground px-1 text-[9px] font-bold text-background"
            data-testid="notification-badge"
          >
            {unreadCount > 99 ? "99+" : unreadCount}
          </span>
        )}
      </Button>

      {/* Dropdown panel */}
      {open && (
        <div
          className="absolute right-0 top-full mt-2 w-96 max-h-[28rem] overflow-hidden rounded-lg border border-border bg-surface shadow-lg z-50 flex flex-col"
          role="dialog"
          aria-label="Notifications"
          data-testid="notification-dropdown"
        >
          {/* Header */}
          <div className="flex items-center justify-between border-b border-border px-4 py-2.5">
            <h3 className="text-sm font-semibold">Notifications</h3>
            <div className="flex items-center gap-2">
              {unreadCount > 0 && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => void handleMarkAllRead()}
                  className="text-xs"
                  data-testid="mark-all-read-btn"
                >
                  <CheckCheck className="h-3 w-3 mr-1" /> Mark all read
                </Button>
              )}
              <Button
                variant="ghost"
                size="icon"
                onClick={() => setOpen(false)}
                aria-label="Close notifications"
              >
                <X className="h-3.5 w-3.5" />
              </Button>
            </div>
          </div>

          {/* List */}
          <div className="flex-1 overflow-y-auto">
            {loading && !data && (
              <p className="p-4 text-sm text-muted animate-pulse">Loading…</p>
            )}
            {data && data.items.length === 0 && (
              <div className="p-8 text-center text-sm text-muted">
                <Bell className="h-8 w-8 mx-auto mb-2 opacity-30" />
                No notifications yet
              </div>
            )}
            {data?.items.map((notif) => (
              <NotificationItem
                key={notif.id}
                notification={notif}
                onMarkRead={handleMarkRead}
              />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

// ── Single notification item ────────────────────────────────────────────────

function NotificationItem({
  notification,
  onMarkRead,
}: {
  readonly notification: NotificationResponse;
  readonly onMarkRead: (id: string) => void;
}) {
  const priorityCfg = PRIORITY_CONFIG[notification.priority] ?? PRIORITY_CONFIG.LOW;
  const Icon = priorityCfg.icon;

  return (
    <div
      className={`flex gap-3 px-4 py-3 border-b border-border/50 transition-colors ${
        notification.read ? "opacity-60" : "bg-primary/[0.02]"
      }`}
      data-testid={`notification-item-${notification.id}`}
    >
      {/* Priority icon */}
      <div className={`shrink-0 mt-0.5 rounded-full p-1.5 ${priorityCfg.bgClassName}`}>
        <Icon className={`h-3.5 w-3.5 ${priorityCfg.className}`} />
      </div>

      {/* Content */}
      <div className="flex-1 min-w-0">
        <p className={`text-sm leading-snug ${notification.read ? "" : "font-medium"}`}>
          {notification.title}
        </p>
        <p className="text-xs text-muted mt-0.5 line-clamp-2">{notification.body}</p>
        <div className="flex items-center gap-2 mt-1">
          <span className="text-[10px] text-muted">{timeAgo(notification.createdAt)}</span>
          <Badge
            variant={notification.priority === "HIGH" ? "locked" : "draft"}
            className="text-[9px] px-1.5 py-0"
          >
            {notification.priority}
          </Badge>
        </div>
      </div>

      {/* Mark read button */}
      {!notification.read && (
        <button
          type="button"
          onClick={() => onMarkRead(notification.id)}
          className="shrink-0 mt-0.5 rounded-full p-1 hover:bg-surface-raised transition-colors"
          aria-label="Mark as read"
          data-testid={`mark-read-${notification.id}`}
        >
          <Check className="h-3.5 w-3.5 text-muted" />
        </button>
      )}
    </div>
  );
}
