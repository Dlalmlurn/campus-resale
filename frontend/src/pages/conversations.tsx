import { HandCoins, MessageSquareText, RefreshCw, Send, ShoppingBag } from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import {
  acceptBargainCard,
  createBargainCard,
  getConversationDetail,
  getConversations,
  rejectBargainCard,
  sendConversationMessage
} from "../api/conversations";
import { createOrder } from "../api/orders";
import type {
  BargainCardSummary,
  CampusPlaceSummary,
  ConversationDetail,
  ConversationSummary,
  CurrentUser
} from "../api/types";

type Notify = (tone: "success" | "error", text: string) => void;

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
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await getConversations());
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setLoading(false);
    }
  }, [props.notify]);

  useEffect(() => { void load(); }, [load]);

  return (
    <section>
      <section className="page-heading">
        <div>
          <p className="eyebrow">站内协商</p>
          <h1>我的会话</h1>
          <p>围绕商品保留沟通记录和议价卡片，协商价只能来自卖家已接受的议价。</p>
        </div>
        <button className="icon-button subtle" type="button" aria-label="刷新会话" onClick={() => void load()}><RefreshCw size={17} /></button>
      </section>
      <div className="conversation-list">
        {loading ? <StateBlock title="会话加载中" /> : items.length === 0 ? <StateBlock title="暂无会话" /> : items.map((item) => {
          const role = item.buyer.id === props.currentUser.id ? "我是买家" : "我是卖家";
          return (
            <button className="conversation-row" type="button" key={item.id} onClick={() => props.onOpenConversation(item.id)}>
              <div className="conversation-thumb"><ShoppingBag size={22} /></div>
              <div>
                <div className="badge-row"><span className="badge neutral">{role}</span><span className="badge success">{item.status}</span></div>
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
  const [message, setMessage] = useState("");
  const [bargain, setBargain] = useState({ amount: "", note: "" });
  const [orderForm, setOrderForm] = useState({ tradePlaceId: "", tradePlaceDetail: "", meetupTime: "", note: "" });
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setDetail(await getConversationDetail(props.id));
    } catch (error) {
      props.notify("error", messageOf(error));
    }
  }, [props.id, props.notify]);

  useEffect(() => { void load(); }, [load]);

  const conversation = detail?.conversation;
  const isBuyer = conversation?.buyer.id === props.currentUser.id;
  const isSeller = conversation?.seller.id === props.currentUser.id;
  const acceptedBargain = useMemo(() => latestAcceptedBargain(detail?.bargainCards ?? []), [detail?.bargainCards]);

  const send = async (event: FormEvent) => {
    event.preventDefault();
    if (!message.trim()) return;
    setBusy(true);
    try {
      await sendConversationMessage(props.id, message);
      setMessage("");
      await load();
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
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
      props.onOpenOrder(order.id);
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  if (!detail || !conversation) return <StateBlock title="会话加载中" />;

  return (
    <section className="conversation-detail-page">
      <button className="text-button back-button" type="button" onClick={props.onBack}>返回会话列表</button>
      <div className="conversation-detail-grid">
        <article className="form-panel conversation-thread">
          <div className="panel-title">
            <div>
              <p className="eyebrow">商品会话</p>
              <h1>{conversation.goodsTitle}</h1>
            </div>
            <span className="badge neutral">{isBuyer ? "我是买家" : "我是卖家"}</span>
          </div>
          <div className="message-list">
            {detail.messages.length === 0 ? <StateBlock title="还没有消息" /> : detail.messages.map((item) => {
              const mine = item.sender?.id === props.currentUser.id;
              const card = item.cardId ? detail.bargainCards.find((entry) => entry.id === item.cardId) : null;
              return (
                <div className={`message-bubble ${mine ? "mine" : ""}`} key={item.id}>
                  <small>{item.sender?.nickname ?? "系统"} · {formatDate(item.sentAt)}</small>
                  {card ? <BargainCard card={card} isSeller={Boolean(isSeller)} busy={busy} onAccept={() => void decide(card.id, "accept")} onReject={() => void decide(card.id, "reject")} /> : <p>{item.textContent}</p>}
                </div>
              );
            })}
          </div>
          <form className="message-compose" onSubmit={(event) => void send(event)}>
            <input value={message} onChange={(event) => setMessage(event.target.value)} placeholder="输入消息" />
            <button className="primary-button compact" disabled={busy || !message.trim()} type="submit"><Send size={16} /> 发送</button>
          </form>
        </article>
        <aside className="side-panel conversation-side">
          {isBuyer && (
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

function BargainCard(props: {
  card: BargainCardSummary;
  isSeller: boolean;
  busy: boolean;
  onAccept?: () => void;
  onReject?: () => void;
}) {
  const actionable = props.isSeller && props.card.actionStatus === "PENDING";
  return (
    <div className="bargain-card">
      <div><strong>¥{props.card.amount}</strong><span className="badge warning">{bargainStatusLabels[props.card.actionStatus] ?? props.card.actionStatus}</span></div>
      {props.card.note && <p>{props.card.note}</p>}
      <small>有效至 {formatDate(props.card.expiresAt)}</small>
      {actionable && <div className="button-row"><button className="primary-button compact" disabled={props.busy} type="button" onClick={props.onAccept}>接受</button><button className="secondary-button compact" disabled={props.busy} type="button" onClick={props.onReject}>拒绝</button></div>}
    </div>
  );
}

function latestAcceptedBargain(cards: BargainCardSummary[]) {
  return [...cards]
    .filter((card) => card.actionStatus === "ACCEPTED")
    .sort((a, b) => new Date(b.actedAt ?? b.createdAt).getTime() - new Date(a.actedAt ?? a.createdAt).getTime())[0] ?? null;
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
