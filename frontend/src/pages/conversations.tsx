import { Archive, ArchiveRestore, Ban, HandCoins, ImagePlus, MessageSquareText, RefreshCw, Send, ShoppingBag, Trash2, Wifi, WifiOff } from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  archiveConversation,
  blockConversation,
  acceptBargainCard,
  createBargainCard,
  deleteConversation,
  getConversationDetail,
  getConversationMessagesAfter,
  getConversations,
  rejectBargainCard,
  sendConversationImageMessage,
  sendConversationMessage,
  unarchiveConversation
} from "../api/conversations";
import { uploadFile } from "../api/m1";
import { createOrder } from "../api/orders";
import type {
  BargainCardSummary,
  CampusPlaceSummary,
  ConversationDetail,
  ConversationRealtimeEvent,
  ConversationSummary,
  CurrentUser,
  MessageSummary
} from "../api/types";

type Notify = (tone: "success" | "error", text: string) => void;
type ConnectionState = "connecting" | "online" | "offline";
type ConversationTab = "active" | "archived";

const bargainStatusLabels: Record<string, string> = {
  PENDING: "待处理",
  ACCEPTED: "已接受",
  REJECTED: "已拒绝",
  EXPIRED: "已过期",
  CANCELLED: "已取消"
};

export function ConversationsPage(props: {
  currentUser: CurrentUser;
  notify: Notify;
  onOpenConversation: (id: number) => void;
}) {
  const [items, setItems] = useState<ConversationSummary[]>([]);
  const [tab, setTab] = useState<ConversationTab>("active");
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await getConversations({ archivedOnly: tab === "archived" }));
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setLoading(false);
    }
  }, [props.notify, tab]);

  useEffect(() => { void load(); }, [load]);

  return (
    <section>
      <section className="page-heading">
        <div>
          <p className="eyebrow">站内协商</p>
          <h1>我的消息</h1>
          <p>围绕商品保留沟通记录和议价卡片，协商价可在聊天内直接创建订单。</p>
        </div>
        <button className="icon-button subtle" type="button" aria-label="刷新会话" onClick={() => void load()}><RefreshCw size={17} /></button>
      </section>
      <div className="segmented-control conversation-tabs" aria-label="消息筛选">
        <button className={tab === "active" ? "active" : ""} type="button" onClick={() => setTab("active")}>当前消息</button>
        <button className={tab === "archived" ? "active" : ""} type="button" onClick={() => setTab("archived")}>已归档</button>
      </div>
      <div className="conversation-list">
        {loading ? <StateBlock title="消息加载中" /> : items.length === 0 ? <StateBlock title={tab === "archived" ? "暂无已归档消息" : "暂无消息"} /> : items.map((item) => {
          const role = item.buyer.id === props.currentUser.id ? "我是买家" : "我是卖家";
          const unread = item.unreadCount > 0;
          return (
            <button className={`conversation-row ${unread ? "unread" : ""}`} type="button" key={item.id} onClick={() => props.onOpenConversation(item.id)}>
              <div className="conversation-thumb"><ShoppingBag size={22} /></div>
              <div>
                <div className="badge-row">
                  <span className="badge neutral">{role}</span>
                  <span className={item.status === "BLOCKED" ? "badge danger" : "badge success"}>{item.status}</span>
                  {item.archived && <span className="badge neutral">已归档</span>}
                  {unread && <span className="badge warning">{item.unreadCount} 条未读</span>}
                </div>
                <h2>{item.goodsTitle}</h2>
                <p>{item.lastMessageText || "还没有文本消息"} · {formatDate(item.lastMessageAt ?? item.createdAt)}</p>
              </div>
            </button>
          );
        })}
      </div>
    </section>
  );
}

export function ConversationDetailPage(props: {
  id: number;
  currentUser: CurrentUser;
  places: CampusPlaceSummary[];
  notify: Notify;
  onBack: () => void;
  onOpenOrder: (id: number) => void;
}) {
  const [detail, setDetail] = useState<ConversationDetail | null>(null);
  const detailRef = useRef<ConversationDetail | null>(null);
  const [connection, setConnection] = useState<ConnectionState>("connecting");
  const [message, setMessage] = useState("");
  const [bargain, setBargain] = useState({ amount: "", note: "" });
  const [orderForm, setOrderForm] = useState({ tradePlaceId: "", tradePlaceDetail: "", meetupTime: "", note: "" });
  const [busy, setBusy] = useState(false);
  const [imageBusy, setImageBusy] = useState(false);
  const [imageError, setImageError] = useState("");

  useEffect(() => { detailRef.current = detail; }, [detail]);

  const load = useCallback(async () => {
    try {
      setDetail(await getConversationDetail(props.id));
    } catch (error) {
      props.notify("error", messageOf(error));
    }
  }, [props.id, props.notify]);

  const syncMissing = useCallback(async () => {
    const current = detailRef.current;
    if (!current) {
      await load();
      return;
    }
    try {
      const missing = await getConversationMessagesAfter(props.id, latestMessageId(current.messages));
      if (missing.length > 0) {
        setDetail((value) => value ? mergeDetail(value, { conversationId: props.id, message: missing[missing.length - 1], occurredAt: new Date().toISOString(), type: "MESSAGE_RECEIVED" }, missing) : value);
      }
    } catch (error) {
      setConnection("offline");
    }
  }, [load, props.id]);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    let closed = false;
    const socket = new WebSocket(`${window.location.protocol === "https:" ? "wss" : "ws"}://${window.location.host}/ws/conversations`);
    setConnection("connecting");
    socket.onopen = () => {
      if (closed) return;
      setConnection("online");
      socket.send(JSON.stringify({ type: "SUBSCRIBE_CONVERSATION", conversationId: props.id }));
      void syncMissing();
    };
    socket.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data) as ConversationRealtimeEvent;
        if (payload.conversationId !== props.id) return;
        setDetail((value) => value ? mergeDetail(value, payload) : value);
      } catch {
        // Ignore control frames and malformed development proxy responses.
      }
    };
    socket.onerror = () => {
      setConnection("offline");
      socket.close();
    };
    socket.onclose = () => {
      if (!closed) setConnection("offline");
    };
    return () => {
      closed = true;
      socket.close();
    };
  }, [props.id, syncMissing]);

  useEffect(() => {
    if (connection === "online") return;
    const timer = window.setInterval(() => void syncMissing(), 5000);
    return () => window.clearInterval(timer);
  }, [connection, syncMissing]);

  const conversation = detail?.conversation;
  const isBuyer = conversation?.buyer.id === props.currentUser.id;
  const isSeller = conversation?.seller.id === props.currentUser.id;
  const acceptedBargain = useMemo(() => latestAcceptedBargain(detail?.bargainCards ?? []), [detail?.bargainCards]);
  const canWrite = conversation?.status === "NORMAL";

  const send = async (event: FormEvent) => {
    event.preventDefault();
    if (!message.trim() || !canWrite) return;
    setBusy(true);
    try {
      const sent = await sendConversationMessage(props.id, message);
      setMessage("");
      setDetail((value) => value ? mergeDetail(value, { type: "MESSAGE_RECEIVED", conversationId: props.id, message: sent, occurredAt: sent.sentAt }) : value);
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  const uploadImage = async (file?: File) => {
    if (!file || !canWrite) return;
    setImageBusy(true);
    setImageError("");
    try {
      const uploaded = await uploadFile(file, "MESSAGE_IMAGE");
      const sent = await sendConversationImageMessage(props.id, [uploaded.id]);
      setDetail((value) => value ? mergeDetail(value, { type: "MESSAGE_RECEIVED", conversationId: props.id, message: sent, occurredAt: sent.sentAt }) : value);
    } catch (error) {
      const text = messageOf(error);
      setImageError(text);
      props.notify("error", text);
    } finally {
      setImageBusy(false);
    }
  };

  const submitBargain = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    try {
      await createBargainCard(props.id, bargain.amount, bargain.note);
      setBargain({ amount: "", note: "" });
      props.notify("success", "议价已发送");
      await load();
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  const decide = async (cardId: number, action: "accept" | "reject") => {
    setBusy(true);
    try {
      if (action === "accept") {
        await acceptBargainCard(props.id, cardId);
        props.notify("success", "已接受议价");
      } else {
        await rejectBargainCard(props.id, cardId);
        props.notify("success", "已拒绝议价");
      }
      await load();
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  const archive = async () => {
    setBusy(true);
    try {
      if (conversation?.archived) {
        const updated = await unarchiveConversation(props.id);
        setDetail((value) => value ? { ...value, conversation: updated } : value);
        props.notify("success", "已移出归档");
      } else {
        const updated = await archiveConversation(props.id);
        setDetail((value) => value ? { ...value, conversation: updated } : value);
        props.notify("success", "消息已归档");
        props.onBack();
      }
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    setBusy(true);
    try {
      await deleteConversation(props.id);
      props.notify("success", "消息已删除");
      props.onBack();
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  const block = async () => {
    setBusy(true);
    try {
      const updated = await blockConversation(props.id);
      setDetail((value) => value ? { ...value, conversation: updated } : value);
      props.notify("success", "会话已屏蔽");
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  const submitOrder = async (event: FormEvent) => {
    event.preventDefault();
    if (!conversation || !acceptedBargain) return;
    setBusy(true);
    try {
      const order = await createOrder({
        goodsId: conversation.goodsId,
        acceptedBargainCardId: acceptedBargain.id,
        tradePlaceId: Number(orderForm.tradePlaceId) || null,
        tradePlaceDetail: orderForm.tradePlaceDetail,
        meetupTime: orderForm.meetupTime ? new Date(orderForm.meetupTime).toISOString() : null,
        note: orderForm.note
      });
      props.notify("success", "已按协商价创建订单");
      await load();
      props.onOpenOrder(order.id);
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  if (!detail || !conversation) return <StateBlock title="消息加载中" />;

  return (
    <section className="conversation-detail-page">
      <button className="text-button back-button" type="button" onClick={props.onBack}>返回消息列表</button>
      <div className="conversation-detail-grid">
        <article className="form-panel conversation-thread">
          <div className="panel-title">
            <div>
              <p className="eyebrow">商品消息</p>
              <h1>{conversation.goodsTitle}</h1>
            </div>
            <span className={`badge ${connection === "online" ? "success" : "warning"}`}>{connection === "online" ? <Wifi size={13} /> : <WifiOff size={13} />}{connection === "online" ? "实时在线" : "轮询补偿"}</span>
          </div>
          <div className="message-list">
            {detail.messages.length === 0 ? <StateBlock title="还没有消息" /> : detail.messages.map((item) => {
              const mine = item.sender?.id === props.currentUser.id;
              const card = item.cardId ? detail.bargainCards.find((entry) => entry.id === item.cardId) : null;
              return (
                <div className={`message-bubble ${mine ? "mine" : ""}`} key={item.id}>
                  <small>{item.sender?.nickname ?? "系统"} · {formatDate(item.sentAt)}</small>
                  {card ? <BargainCard card={card} isSeller={Boolean(isSeller)} busy={busy} onAccept={() => void decide(card.id, "accept")} onReject={() => void decide(card.id, "reject")} /> : <MessageBody item={item} />}
                </div>
              );
            })}
          </div>
          <form className="message-compose" onSubmit={(event) => void send(event)}>
            <label className="icon-button image-button" aria-label="发送图片">
              <ImagePlus size={17} />
              <input accept="image/jpeg,image/png,image/webp" disabled={imageBusy || busy || !canWrite} type="file" onChange={(event) => void uploadImage(event.target.files?.[0])} />
            </label>
            <input value={message} disabled={!canWrite} onChange={(event) => setMessage(event.target.value)} placeholder={canWrite ? "输入消息" : "消息当前不可发送"} />
            <button className="primary-button compact" disabled={busy || !message.trim() || !canWrite} type="submit"><Send size={16} /> 发送</button>
          </form>
          {imageError && <p className="form-hint error-text">{imageError}</p>}
        </article>
        <aside className="side-panel conversation-side">
          <div className="conversation-actions">
            <span className="badge neutral">{isBuyer ? "我是买家" : "我是卖家"}</span>
            <button className="secondary-button compact" disabled={busy} type="button" onClick={() => void archive()}>{conversation.archived ? <ArchiveRestore size={15} /> : <Archive size={15} />}{conversation.archived ? "移出归档" : "归档"}</button>
            <button className="secondary-button compact danger-action" disabled={busy} type="button" onClick={() => void remove()}><Trash2 size={15} /> 删除</button>
            <button className="secondary-button compact danger-action" disabled={busy || conversation.status === "BLOCKED"} type="button" onClick={() => void block()}><Ban size={15} /> 屏蔽</button>
          </div>
          {!canWrite && <p className="closed-hint">消息已屏蔽，不能继续发送消息或议价。</p>}
          {isBuyer && canWrite && (
            <form className="inline-action" onSubmit={(event) => void submitBargain(event)}>
              <div className="panel-title"><h2><HandCoins size={17} /> 发起议价</h2></div>
              <label><span>议价金额</span><input required min="0.01" step="0.01" type="number" value={bargain.amount} onChange={(event) => setBargain({ ...bargain, amount: event.target.value })} /></label>
              <label><span>备注</span><input value={bargain.note} onChange={(event) => setBargain({ ...bargain, note: event.target.value })} placeholder="例如：今晚可自取" /></label>
              <button className="primary-button full-width" disabled={busy} type="submit">发送议价</button>
            </form>
          )}
          {isBuyer && acceptedBargain && (
            <form className="inline-action" onSubmit={(event) => void submitOrder(event)}>
              <div className="panel-title"><h2>按协商价下单</h2><strong>¥{acceptedBargain.amount}</strong></div>
              <label><span>交易地点</span><select required value={orderForm.tradePlaceId} onChange={(event) => setOrderForm({ ...orderForm, tradePlaceId: event.target.value })}><option value="">请选择</option>{props.places.map((place) => <option value={place.id} key={place.id}>{place.campus} · {place.name}</option>)}</select></label>
              <label><span>地点补充</span><input value={orderForm.tradePlaceDetail} onChange={(event) => setOrderForm({ ...orderForm, tradePlaceDetail: event.target.value })} /></label>
              <label><span>见面时间</span><input required type="datetime-local" value={orderForm.meetupTime} onChange={(event) => setOrderForm({ ...orderForm, meetupTime: event.target.value })} /></label>
              <label><span>备注</span><input value={orderForm.note} onChange={(event) => setOrderForm({ ...orderForm, note: event.target.value })} /></label>
              <button className="primary-button full-width" disabled={busy} type="submit">创建订单</button>
            </form>
          )}
          <div className="conversation-card-list">
            <div className="panel-title"><h2>议价记录</h2></div>
            {detail.bargainCards.length === 0 ? <p className="empty-line">暂无议价卡片</p> : detail.bargainCards.map((card) => <BargainCard key={card.id} card={card} isSeller={false} busy={busy} />)}
          </div>
        </aside>
      </div>
    </section>
  );
}

function MessageBody({ item }: { item: MessageSummary }) {
  return (
    <>
      {item.textContent && <p>{item.textContent}</p>}
      {item.attachments.length > 0 && (
        <div className="message-attachments">
          {item.attachments.map((attachment) => (
            <img alt={attachment.originalName} key={attachment.id} src={attachment.url} />
          ))}
        </div>
      )}
    </>
  );
}

function BargainCard(props: {
  card: BargainCardSummary;
  isSeller: boolean;
  busy: boolean;
  onAccept?: () => void;
  onReject?: () => void;
}) {
  const expired = isExpired(props.card);
  const status = expired && props.card.actionStatus === "PENDING" ? "EXPIRED" : props.card.actionStatus;
  const actionable = props.isSeller && status === "PENDING";
  return (
    <div className={`bargain-card ${expired ? "expired" : ""}`}>
      <div><strong>¥{props.card.amount}</strong><span className="badge warning">{bargainStatusLabels[status] ?? status}</span></div>
      {props.card.note && <p>{props.card.note}</p>}
      <small>有效至 {formatDate(props.card.expiresAt)}</small>
      {actionable && <div className="button-row"><button className="primary-button compact" disabled={props.busy} type="button" onClick={props.onAccept}>接受</button><button className="secondary-button compact" disabled={props.busy} type="button" onClick={props.onReject}>拒绝</button></div>}
    </div>
  );
}

function mergeDetail(detail: ConversationDetail, event: ConversationRealtimeEvent, missingMessages: MessageSummary[] = []) {
  const messages = mergeMessages(detail.messages, missingMessages.length > 0 ? missingMessages : event.message ? [event.message] : []);
  const last = messages[messages.length - 1];
  return {
    ...detail,
    conversation: {
      ...(event.conversation ?? detail.conversation),
      lastMessageId: last?.id ?? detail.conversation.lastMessageId ?? null,
      lastMessageText: last ? previewMessage(last) : detail.conversation.lastMessageText,
      lastMessageAt: last?.sentAt ?? detail.conversation.lastMessageAt,
      unreadCount: 0
    },
    messages,
    bargainCards: event.bargainCard ? mergeBargainCards(detail.bargainCards, [event.bargainCard]) : detail.bargainCards
  };
}

function mergeMessages(current: MessageSummary[], incoming: MessageSummary[]) {
  const byId = new Map(current.map((message) => [message.id, message]));
  incoming.forEach((message) => byId.set(message.id, message));
  return [...byId.values()].sort((a, b) => a.id - b.id);
}

function mergeBargainCards(current: BargainCardSummary[], incoming: BargainCardSummary[]) {
  const byId = new Map(current.map((card) => [card.id, card]));
  incoming.forEach((card) => byId.set(card.id, card));
  return [...byId.values()].sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime());
}

function latestAcceptedBargain(cards: BargainCardSummary[]) {
  return [...cards]
    .filter((card) => card.actionStatus === "ACCEPTED" && !isExpired(card))
    .sort((a, b) => new Date(b.actedAt ?? b.createdAt).getTime() - new Date(a.actedAt ?? a.createdAt).getTime())[0] ?? null;
}

function latestMessageId(messages: MessageSummary[]) {
  return messages.reduce((max, message) => Math.max(max, message.id), 0);
}

function previewMessage(message: MessageSummary) {
  if (message.cardId) return message.textContent ?? "系统卡片";
  if (message.attachments.length > 0) return message.textContent || "[图片]";
  return message.textContent ?? "";
}

function isExpired(card: BargainCardSummary) {
  return Boolean(card.expiresAt && new Date(card.expiresAt).getTime() < Date.now());
}

function StateBlock({ title }: { title: string }) {
  return <div className="state-block"><MessageSquareText size={24} /><strong>{title}</strong></div>;
}

function messageOf(error: unknown) {
  return error instanceof Error ? error.message : "请求失败，请稍后重试";
}

function formatDate(value?: string | null) {
  return value ? new Date(value).toLocaleString("zh-CN") : "尚未产生";
}
