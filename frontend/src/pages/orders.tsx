import { CreditCard, FileUp, MessageSquareText, PackageCheck, RefreshCw, Star, XCircle } from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import {
  buyerCancelOrder,
  confirmCompletion,
  createRefund,
  createReview,
  getOrderCompletionRequest,
  getOrderDetail,
  getOrderRefunds,
  getOrderReviews,
  getOrders,
  getPaymentStatus,
  getPaymentTransactions,
  getSettlementStatus,
  requestCompletion,
  sellerConfirmOrder,
  sellerRejectOrder,
  simulatePayment
} from "../api/orders";
import { uploadFile } from "../api/m1";
import { ReportButton } from "../components/ReportButton";
import type {
  CompletionRequestSummary,
  CurrentUser,
  OrderSummary,
  PaymentSummary,
  PaymentTransactionSummary,
  RefundSummary,
  ReviewSummary,
  SettlementSummary,
  StoredFileSummary
} from "../api/types";

type Notify = (tone: "success" | "error", text: string) => void;

const orderStatusLabels: Record<string, string> = {
  PENDING_SELLER_CONFIRM: "等待卖家确认",
  PENDING_PAYMENT: "等待买家支付",
  PAID_PENDING_MEETUP: "已托管付款，等待见面交易",
  COMPLETED_PENDING_SETTLEMENT: "交易完成，等待平台结算",
  COMPLETED: "交易完成",
  CANCELLED: "订单已取消",
  CLOSED: "订单已关闭",
  DISPUTE_PROCESSING: "争议处理中",
  REFUND_PROCESSING: "退款处理中"
};

const steps = [
  { key: "PENDING_SELLER_CONFIRM", title: "已下单", text: "等待卖家确认" },
  { key: "PENDING_PAYMENT", title: "卖家已确认", text: "等待买家支付" },
  { key: "PAID_PENDING_MEETUP", title: "已托管付款", text: "线下面交" },
  { key: "COMPLETED_PENDING_SETTLEMENT", title: "完成确认", text: "等待结算" },
  { key: "COMPLETED", title: "评价完成", text: "交易闭环结束" }
];

const stepIndexByStatus: Record<string, number> = {
  PENDING_SELLER_CONFIRM: 0,
  PENDING_PAYMENT: 1,
  PAID_PENDING_MEETUP: 2,
  COMPLETED_PENDING_SETTLEMENT: 3,
  COMPLETED: 4
};

export function OrdersPage(props: {
  currentUser: CurrentUser;
  notify: Notify;
  onOpenOrder: (id: number) => void;
}) {
  const [mode, setMode] = useState<"all" | "buyer" | "seller">("all");
  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setOrders((await getOrders()).items);
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setLoading(false);
    }
  }, [props.notify]);

  useEffect(() => { void load(); }, [load]);

  const visibleOrders = useMemo(() => orders.filter((order) => {
    if (mode === "buyer") return order.buyer.id === props.currentUser.id;
    if (mode === "seller") return order.seller.id === props.currentUser.id;
    return order.buyer.id === props.currentUser.id || order.seller.id === props.currentUser.id;
  }), [mode, orders, props.currentUser.id]);

  return (
    <section>
      <PageHeading eyebrow="交易订单" title="我的订单" text="查看买入和卖出的订单进度，按当前身份完成下一步操作。" />
      <div className="order-toolbar">
        <div className="segmented-control order-tabs">
          <button className={mode === "all" ? "active" : ""} type="button" onClick={() => setMode("all")}>全部</button>
          <button className={mode === "buyer" ? "active" : ""} type="button" onClick={() => setMode("buyer")}>我买到的</button>
          <button className={mode === "seller" ? "active" : ""} type="button" onClick={() => setMode("seller")}>我卖出的</button>
        </div>
        <button className="icon-button subtle" aria-label="刷新订单" type="button" onClick={() => void load()}><RefreshCw size={17} /></button>
      </div>
      <div className="order-list">
        {loading ? <StateBlock title="订单加载中" /> : visibleOrders.length === 0 ? <StateBlock title="暂无订单记录" /> : visibleOrders.map((order) => {
          const role = order.buyer.id === props.currentUser.id ? "我是买家" : "我是卖家";
          return (
            <button className="order-card" type="button" key={order.id} onClick={() => props.onOpenOrder(order.id)}>
              <div>
                <div className="badge-row">
                  <span className="badge neutral">{role}</span>
                  <OrderStatusBadge status={order.status} />
                </div>
                <h2>{order.goodsTitle}</h2>
                <p>{order.orderNo} · {order.tradePlaceName ?? "待约定地点"} · {formatDate(order.meetupTime)}</p>
              </div>
              <strong>¥{order.frozenAmount}</strong>
            </button>
          );
        })}
      </div>
    </section>
  );
}

export function OrderDetailPage(props: {
  id: number;
  currentUser: CurrentUser;
  notify: Notify;
  onBack: () => void;
}) {
  const [order, setOrder] = useState<OrderSummary | null>(null);
  const [payment, setPayment] = useState<PaymentSummary | null>(null);
  const [transactions, setTransactions] = useState<PaymentTransactionSummary[]>([]);
  const [refunds, setRefunds] = useState<RefundSummary[]>([]);
  const [settlement, setSettlement] = useState<SettlementSummary | null>(null);
  const [completion, setCompletion] = useState<CompletionRequestSummary | null>(null);
  const [reviews, setReviews] = useState<ReviewSummary[]>([]);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const next = await getOrderDetail(props.id);
      setOrder(next);
      void getOrderReviews(props.id).then(setReviews).catch(() => setReviews([]));
      if (["PENDING_PAYMENT", "PAID_PENDING_MEETUP", "COMPLETED_PENDING_SETTLEMENT", "COMPLETED"].includes(next.status)) {
        void getPaymentStatus(props.id).then(setPayment).catch(() => setPayment(null));
        void getPaymentTransactions(props.id).then(setTransactions).catch(() => setTransactions([]));
      }
      void getOrderRefunds(props.id).then(setRefunds).catch(() => setRefunds([]));
      if (["COMPLETED_PENDING_SETTLEMENT", "COMPLETED"].includes(next.status)) {
        void getSettlementStatus(props.id).then(setSettlement).catch(() => setSettlement(null));
      }
      if (next.status === "PAID_PENDING_MEETUP") {
        void getOrderCompletionRequest(props.id).then(setCompletion).catch(() => setCompletion(null));
      }
    } catch (error) {
      props.notify("error", messageOf(error));
    }
  }, [props.id, props.notify]);

  useEffect(() => { void load(); }, [load]);

  const action = async (runner: () => Promise<OrderSummary | CompletionRequestSummary | PaymentSummary | ReviewSummary | RefundSummary>, success: string) => {
    setBusy(true);
    try {
      const result = await runner();
      if ("goodsTitle" in result) setOrder(result);
      if ("paymentNo" in result) {
        setPayment(result);
        await load();
      }
      if ("windowEndsAt" in result) setCompletion(result);
      if ("windowEndsAt" in result) storeCompletion(result);
      if ("goodsTitle" in result && result.status === "COMPLETED_PENDING_SETTLEMENT") {
        clearStoredCompletion(result.id);
        setCompletion(null);
      }
      if ("rating" in result) {
        setReviews((current) => [result, ...current.filter((item) => item.id !== result.id)]);
        await load();
      }
      if ("refundNo" in result) {
        setRefunds((current) => [result, ...current]);
        await load();
      }
      props.notify("success", success);
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  if (!order) return <StateBlock title="订单加载中" />;

  const isBuyer = order.buyer.id === props.currentUser.id;
  const isSeller = order.seller.id === props.currentUser.id;
  const myReview = reviews.find((review) => review.reviewerId === props.currentUser.id);
  const canReview = (isBuyer || isSeller) && ["COMPLETED_PENDING_SETTLEMENT", "COMPLETED"].includes(order.status) && !myReview;
  const canRefund = ["PAID_PENDING_MEETUP", "COMPLETED_PENDING_SETTLEMENT"].includes(order.status) && (isBuyer || isSeller);

  return (
    <section className="order-detail">
      <button className="text-button back-button" type="button" onClick={props.onBack}>返回订单列表</button>
      <div className="order-detail-grid">
        <article className="form-panel">
          <div className="panel-title">
            <div>
              <p className="eyebrow">订单 {order.orderNo}</p>
              <h1>{order.goodsTitle}</h1>
            </div>
            <OrderStatusBadge status={order.status} />
          </div>
          <strong className="detail-price">¥{order.frozenAmount}</strong>
          <div className="order-timeline">
            {steps.map((step, index) => {
              const activeIndex = stepIndexByStatus[order.status] ?? -1;
              const className = index < activeIndex ? "done" : index === activeIndex ? "current" : "";
              return <div className={`order-step ${className}`} key={step.key}><strong>{step.title}</strong><span>{step.text}</span></div>;
            })}
          </div>
          <dl className="detail-list">
            <div><dt>买家</dt><dd>{order.buyer.nickname}</dd></div>
            <div><dt>卖家</dt><dd>{order.seller.nickname}</dd></div>
            <div><dt>交易地点</dt><dd>{order.tradePlaceName ?? "待约定"} {order.tradePlaceDetail ?? ""}</dd></div>
            <div><dt>见面时间</dt><dd>{formatDate(order.meetupTime)}</dd></div>
            <div><dt>备注</dt><dd>{order.buyerNote || "无"}</dd></div>
            {payment && <div><dt>支付状态</dt><dd>{paymentStatusLabel(payment.status)}</dd></div>}
            {settlement && <div><dt>结算状态</dt><dd>{settlementStatusLabel(settlement.status)} · ¥{settlement.settlementAmount}</dd></div>}
            {order.closedAt && <div><dt>关闭时间</dt><dd>{formatDate(order.closedAt)}</dd></div>}
          </dl>
          <MoneyTrail payment={payment} transactions={transactions} settlement={settlement} refunds={refunds} />
        </article>
        <aside className="side-panel action-panel">
          <div className="panel-title"><h2>下一步操作</h2></div>
          <p>{actionHint(order, isBuyer, isSeller, Boolean(completion))}</p>
          <OrderActions
            order={order}
            isBuyer={isBuyer}
            isSeller={isSeller}
            busy={busy}
            completionId={completion?.id ?? null}
            onSellerConfirm={() => action(() => sellerConfirmOrder(order.id), "卖家已确认订单")}
            onSellerReject={() => action(() => sellerRejectOrder(order.id, window.prompt("填写拒绝原因", "无法按时交易") ?? "无法按时交易"), "订单已拒绝")}
            onBuyerCancel={() => action(() => buyerCancelOrder(order.id, window.prompt("填写取消原因", "暂时不需要了") ?? "暂时不需要了"), "订单已取消")}
            onPay={() => action(() => simulatePayment(order.id), "模拟支付成功")}
            onRequestCompletion={() => action(() => requestCompletion(order.id), "已发起完成确认")}
            onConfirmCompletion={() => action(() => confirmCompletion(order.id, completion!.id), "交易完成确认成功")}
          />
          <ReportButton currentUser={props.currentUser} targetType="ORDER" targetId={order.id} notify={props.notify} />
          {canRefund && (
            <RefundForm
              busy={busy}
              maxAmount={payment?.amount ?? order.frozenAmount}
              onSubmit={(request) => action(() => createRefund(order.id, request), "退款申请已提交")}
              notify={props.notify}
            />
          )}
          {canReview && <ReviewForm busy={busy} onSubmit={(rating, content) => action(() => createReview(order.id, rating, content), "评价已提交")} />}
          {reviews.length > 0 && <ReviewList reviews={reviews} currentUser={props.currentUser} order={order} />}
        </aside>
      </div>
    </section>
  );
}

function OrderActions(props: {
  order: OrderSummary;
  isBuyer: boolean;
  isSeller: boolean;
  busy: boolean;
  completionId: number | null;
  onSellerConfirm: () => void;
  onSellerReject: () => void;
  onBuyerCancel: () => void;
  onPay: () => void;
  onRequestCompletion: () => void;
  onConfirmCompletion: () => void;
}) {
  if (props.order.status === "PENDING_SELLER_CONFIRM" && props.isSeller) {
    return <div className="button-row"><button className="primary-button" disabled={props.busy} type="button" onClick={props.onSellerConfirm}>确认接单</button><button className="secondary-button" disabled={props.busy} type="button" onClick={props.onSellerReject}>拒绝订单</button></div>;
  }
  if (props.order.status === "PENDING_PAYMENT" && props.isBuyer) {
    return <div className="button-row"><button className="primary-button" disabled={props.busy} type="button" onClick={props.onPay}><CreditCard size={17} /> 模拟支付</button><button className="secondary-button" disabled={props.busy} type="button" onClick={props.onBuyerCancel}>取消订单</button></div>;
  }
  if (props.order.status === "PAID_PENDING_MEETUP" && props.isSeller) {
    return <button className="primary-button full-width" disabled={props.busy} type="button" onClick={props.onRequestCompletion}><PackageCheck size={17} /> 发起完成确认</button>;
  }
  if (props.order.status === "PAID_PENDING_MEETUP" && props.isBuyer && props.completionId) {
    return <button className="primary-button full-width" disabled={props.busy} type="button" onClick={props.onConfirmCompletion}>确认交易完成</button>;
  }
  if (["CANCELLED", "CLOSED", "DISPUTE_PROCESSING", "REFUND_PROCESSING"].includes(props.order.status)) {
    return <div className="closed-hint"><XCircle size={18} /> 当前订单无需继续操作。</div>;
  }
  return <div className="closed-hint"><MessageSquareText size={18} /> 等待对方完成下一步。</div>;
}

function MoneyTrail(props: {
  payment: PaymentSummary | null;
  transactions: PaymentTransactionSummary[];
  settlement: SettlementSummary | null;
  refunds: RefundSummary[];
}) {
  return (
    <section className="money-trail">
      <div className="panel-title"><h2>资金链路</h2></div>
      <div className="money-grid">
        <MoneyItem label="支付单" value={props.payment ? paymentStatusLabel(props.payment.status) : "暂无"} sub={props.payment ? `${props.payment.paymentNo} · ¥${props.payment.amount}` : "卖家确认后生成"} />
        <MoneyItem label="支付流水" value={`${props.transactions.length} 条`} sub={props.transactions[0]?.transactionNo ?? "等待支付回调"} />
        <MoneyItem label="退款" value={`${props.refunds.length} 笔`} sub={props.refunds[0] ? `${refundStatusLabel(props.refunds[0].status)} · ¥${props.refunds[0].amount}` : "暂无退款申请"} />
        <MoneyItem label="结算" value={props.settlement ? settlementStatusLabel(props.settlement.status) : "暂无"} sub={props.settlement ? `${props.settlement.settlementNo} · ¥${props.settlement.settlementAmount}` : "完成确认后生成"} />
      </div>
      {props.refunds.length > 0 && (
        <div className="refund-list">
          {props.refunds.map((refund) => (
            <div className="refund-row" key={refund.id}>
              <span>{refund.refundNo}</span>
              <strong>¥{refund.amount}</strong>
              <em>{refundStatusLabel(refund.status)}</em>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function MoneyItem(props: { label: string; value: string; sub: string }) {
  return <div className="money-item"><span>{props.label}</span><strong>{props.value}</strong><small>{props.sub}</small></div>;
}

function RefundForm(props: {
  busy: boolean;
  maxAmount: string;
  onSubmit: (request: { refundType: string; amount: string; reason: string; evidenceFileIds: number[] }) => void;
  notify: Notify;
}) {
  const [open, setOpen] = useState(false);
  const [refundType, setRefundType] = useState("FULL");
  const [amount, setAmount] = useState(props.maxAmount);
  const [reason, setReason] = useState("");
  const [files, setFiles] = useState<StoredFileSummary[]>([]);
  const [uploading, setUploading] = useState(false);

  useEffect(() => setAmount(props.maxAmount), [props.maxAmount]);

  const upload = async (file?: File) => {
    if (!file) return;
    setUploading(true);
    try {
      const uploaded = await uploadFile(file, "ORDER_EVIDENCE");
      setFiles((current) => [...current, uploaded]);
      props.notify("success", "退款证据已上传");
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setUploading(false);
    }
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    props.onSubmit({
      refundType,
      amount,
      reason: reason || "交易协商退款",
      evidenceFileIds: files.map((file) => file.id)
    });
  };

  if (!open) {
    return <button className="secondary-button full-width" disabled={props.busy} type="button" onClick={() => setOpen(true)}>申请退款</button>;
  }

  return (
    <form className="refund-form" onSubmit={submit}>
      <div className="panel-title"><h2>退款申请</h2></div>
      <div className="segmented-control">
        <button className={refundType === "FULL" ? "active" : ""} type="button" onClick={() => { setRefundType("FULL"); setAmount(props.maxAmount); }}>全额</button>
        <button className={refundType === "PARTIAL" ? "active" : ""} type="button" onClick={() => setRefundType("PARTIAL")}>部分</button>
      </div>
      <FormField label="退款金额">
        <input value={amount} onChange={(event) => setAmount(event.target.value)} inputMode="decimal" />
      </FormField>
      <FormField label="退款原因">
        <textarea rows={3} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="描述退款原因和协商情况" />
      </FormField>
      <label className="upload-zone compact-zone">
        <FileUp size={18} />
        <span>{uploading ? "上传中..." : "上传退款证据"}</span>
        <input type="file" accept="image/png,image/jpeg,image/webp" disabled={uploading || props.busy} onChange={(event) => void upload(event.target.files?.[0])} />
      </label>
      {files.map((file) => <div className="file-row" key={file.id}><FileUp size={15} /><span>{file.originalName}</span><small>#{file.id}</small></div>)}
      <div className="button-row">
        <button className="primary-button" disabled={props.busy || uploading} type="submit">提交退款</button>
        <button className="secondary-button" disabled={props.busy} type="button" onClick={() => setOpen(false)}>收起</button>
      </div>
    </form>
  );
}

function ReviewForm(props: { busy: boolean; onSubmit: (rating: number, content: string) => void }) {
  const [rating, setRating] = useState(5);
  const [content, setContent] = useState("");
  const submit = (event: FormEvent) => {
    event.preventDefault();
    props.onSubmit(rating, content || "交易顺利，物品与描述一致。");
  };
  return (
    <form className="review-form" onSubmit={submit}>
      <FormField label="交易评分">
        <select value={rating} onChange={(event) => setRating(Number(event.target.value))}>
          {[5, 4, 3, 2, 1].map((value) => <option value={value} key={value}>{value} 星</option>)}
        </select>
      </FormField>
      <FormField label="评价内容">
        <textarea rows={3} value={content} onChange={(event) => setContent(event.target.value)} placeholder="这次交易体验如何" />
      </FormField>
      <button className="primary-button full-width" disabled={props.busy} type="submit">提交评价</button>
    </form>
  );
}

function ReviewList(props: { reviews: ReviewSummary[]; currentUser: CurrentUser; order: OrderSummary }) {
  return (
    <div className="review-stack" aria-label="订单评价">
      {props.reviews.map((review) => {
        const reviewerName = participantName(props.order, review.reviewerId, props.currentUser.id);
        const reviewedName = participantName(props.order, review.reviewedUserId, props.currentUser.id);
        return (
          <div className="review-summary" key={review.id}>
            <Star size={17} />
            <span>{reviewerName} 评价 {reviewedName}：{review.rating} 星 · {review.content || "未填写评价内容"} · {review.status === "VISIBLE" ? "已公开" : "待双方评价"}</span>
          </div>
        );
      })}
    </div>
  );
}

function participantName(order: OrderSummary, userId: number, currentUserId: number) {
  if (userId === currentUserId) return "我";
  if (userId === order.buyer.id) return order.buyer.nickname;
  if (userId === order.seller.id) return order.seller.nickname;
  return `用户 ${userId}`;
}

export function OrderStatusBadge({ status }: { status: string }) {
  const tone = ["COMPLETED", "COMPLETED_PENDING_SETTLEMENT", "PAID_PENDING_MEETUP"].includes(status)
    ? "success"
    : ["CANCELLED", "CLOSED", "DISPUTE_PROCESSING", "REFUND_PROCESSING"].includes(status)
      ? "danger"
      : "warning";
  return <span className={`badge ${tone}`}>{orderStatusLabels[status] ?? status}</span>;
}

function actionHint(order: OrderSummary, isBuyer: boolean, isSeller: boolean, hasCompletionRequest: boolean) {
  if (order.status === "PENDING_SELLER_CONFIRM") return isSeller ? "买家已提交订单，请确认是否接单。" : "订单已提交，等待卖家确认。";
  if (order.status === "PENDING_PAYMENT") return isBuyer ? "卖家已确认，请完成模拟支付。" : "等待买家完成模拟支付。";
  if (order.status === "PAID_PENDING_MEETUP" && isSeller) return "买家已完成托管支付，线下见面后发起完成确认。";
  if (order.status === "PAID_PENDING_MEETUP" && isBuyer) return hasCompletionRequest ? "卖家已发起完成确认，请确认交易是否完成。" : "已完成托管支付，请按约定地点见面交易。";
  if (order.status === "COMPLETED_PENDING_SETTLEMENT") return isBuyer ? "交易已完成，可以提交评价。" : "交易已完成，等待平台结算。";
  if (order.status === "COMPLETED") return "交易闭环已结束。";
  return "订单已进入异常或关闭状态，请查看状态说明。";
}

function PageHeading(props: { eyebrow: string; title: string; text: string }) {
  return <section className="page-heading"><div><p className="eyebrow">{props.eyebrow}</p><h1>{props.title}</h1><p>{props.text}</p></div></section>;
}

function FormField(props: { label: string; children: React.ReactNode }) {
  return <label className="form-field"><span>{props.label}</span>{props.children}</label>;
}

function StateBlock({ title }: { title: string }) {
  return <div className="state-block"><RefreshCw size={22} /><strong>{title}</strong></div>;
}

function paymentStatusLabel(status: string) {
  return ({ PENDING: "待支付", PROCESSING: "处理中", ESCROWED: "已托管", FAILED: "支付失败", CLOSED: "已关闭" } as Record<string, string>)[status] ?? status;
}

function settlementStatusLabel(status: string) {
  return ({ PENDING: "待结算", PROCESSING: "结算处理中", SETTLED: "已结算", FAILED: "结算失败", CLOSED: "已关闭" } as Record<string, string>)[status] ?? status;
}

function refundStatusLabel(status: string) {
  return ({ PENDING: "待审核", PROCESSING: "退款处理中", REFUNDED: "已退款", FAILED: "退款失败", CLOSED: "已关闭" } as Record<string, string>)[status] ?? status;
}

function readStoredCompletion(orderId: number) {
  try {
    const raw = window.sessionStorage.getItem(`completion-request:${orderId}`);
    return raw ? JSON.parse(raw) as CompletionRequestSummary : null;
  } catch {
    return null;
  }
}

function storeCompletion(value: CompletionRequestSummary) {
  window.sessionStorage.setItem(`completion-request:${value.orderId}`, JSON.stringify(value));
}

function clearStoredCompletion(orderId: number) {
  window.sessionStorage.removeItem(`completion-request:${orderId}`);
}

function formatDate(value?: string | null) {
  return value ? new Date(value).toLocaleString("zh-CN") : "待约定";
}

function messageOf(error: unknown) {
  return error instanceof Error ? error.message : "请求失败，请稍后重试";
}
