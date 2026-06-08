// 文件功能：站内通知页（未读筛选、全部已读、跳转订单）。原内联于 App.tsx。
import { useCallback, useEffect, useState } from "react";
import { getNotifications, getUnreadNotificationCount, markAllNotificationsRead, type NotificationItem } from "../api/notifications";
import { EmptyBlock, LoadingBlock, PageHeading } from "../components/ui";
import { formatDate, messageOf, notificationTypeLabel, type Notify } from "../shared/app-shared";

export function NotificationsPage(props: { notify: Notify; onOpenOrder: (id: number) => void }) {
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [page, count] = await Promise.all([
        getNotifications({ unreadOnly, page: 1, pageSize: 20 }),
        getUnreadNotificationCount()
      ]);
      setItems(page.items);
      setUnreadCount(count.unreadCount);
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setLoading(false);
    }
  }, [props.notify, unreadOnly]);

  useEffect(() => { void load(); }, [load]);

  const markAllRead = async () => {
    try {
      await markAllNotificationsRead();
      setUnreadCount(0);
      setItems((current) => current.map((item) => ({ ...item, read: true, readAt: item.readAt ?? new Date().toISOString() })));
      props.notify("success", "通知已全部标为已读");
    } catch (error) {
      props.notify("error", messageOf(error));
    }
  };

  return (
    <section>
      <div className="order-toolbar">
        <PageHeading eyebrow="消息中心" title="站内通知" text="集中查看订单、支付、审核和结算状态变化。" />
        <div className="notification-actions">
          <span className="badge neutral">{unreadCount} 条未读</span>
          <button className={`secondary-button compact ${unreadOnly ? "active" : ""}`} type="button" onClick={() => setUnreadOnly((value) => !value)}>
            只看未读
          </button>
          <button className="primary-button compact" type="button" onClick={() => void markAllRead()} disabled={unreadCount === 0}>
            全部标为已读
          </button>
        </div>
      </div>

      {loading ? <LoadingBlock /> : items.length === 0 ? <EmptyBlock title="当前没有通知" /> : (
        <div className="notification-list">
          {items.map((item) => (
            <article className={`notification-row ${item.read ? "" : "unread"}`} key={item.id}>
              <div className="notification-main">
                <div className="notification-title-line">
                  <strong>{item.title}</strong>
                  {!item.read && <span className="badge warning">未读</span>}
                </div>
                <p>{item.content}</p>
                <small>{formatDate(item.createdAt)} · {notificationTypeLabel(item.type)}</small>
              </div>
              {item.relatedType === "ORDER" && item.relatedId !== null && (
                <button className="secondary-button compact" type="button" onClick={() => props.onOpenOrder(item.relatedId!)}>
                  查看订单
                </button>
              )}
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
