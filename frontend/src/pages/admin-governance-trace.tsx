// 文件功能：管理员治理追踪页，按用户或举报串起举报、处罚、申诉与信用影响。
import { RefreshCw, Search, ShieldAlert, UserSearch } from "lucide-react";
import type { FormEvent, ReactNode } from "react";
import { useCallback, useEffect, useState } from "react";
import {
  getAdminReportTrace,
  getAdminUserCreditTrace,
  getAdminUserGovernanceTrace,
  searchAdminTraceUsers,
  type AdminCreditTraceItem,
  type AdminReportUserTraceItem,
  type GovernanceUser
} from "../api/governance";
import { EmptyBlock, LoadingBlock } from "../components/ui";
import { formatDate, messageOf, type Notify } from "../shared/app-shared";

const targetLabels: Record<string, string> = {
  GOODS: "商品",
  ORDER: "订单",
  USER: "账号",
  MESSAGE: "消息",
  COMMENT: "留言"
};

const relationLabels: Record<string, string> = {
  REPORTER: "举报人",
  REPORTED_USER: "被举报账号",
  GOODS_SELLER: "商品卖家",
  ORDER_BUYER: "订单买家",
  ORDER_SELLER: "订单卖家",
  MESSAGE_SENDER: "消息发送人",
  MESSAGE_BUYER: "消息买家",
  MESSAGE_SELLER: "消息卖家"
};

// initialUserId / initialReportId 由外部（账号管理「追踪」按钮、举报队列跳转）带入并自动加载。
export function AdminGovernanceTracePage(props: { notify: Notify; initialUserId?: number; initialReportId?: number }) {
  const [userId, setUserId] = useState(props.initialUserId ? String(props.initialUserId) : "");
  const [reportId, setReportId] = useState(props.initialReportId ? String(props.initialReportId) : "");
  const [reportTraces, setReportTraces] = useState<AdminReportUserTraceItem[]>([]);
  const [creditTraces, setCreditTraces] = useState<AdminCreditTraceItem[]>([]);
  const [reportLookupTraces, setReportLookupTraces] = useState<AdminReportUserTraceItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [userKeyword, setUserKeyword] = useState("");
  const [userMatches, setUserMatches] = useState<GovernanceUser[]>([]);
  const [searching, setSearching] = useState(false);

  const { notify } = props;

  // 按用户 ID 拉取治理追踪 + 信用流水；显式传 id 便于从搜索结果或外部入口直接加载。
  const runUserTrace = useCallback(async (rawUserId: string) => {
    const numericUserId = Number(rawUserId);
    if (!numericUserId) {
      notify("error", "请输入有效的用户 ID");
      return;
    }
    setLoading(true);
    try {
      const [governance, credit] = await Promise.all([
        getAdminUserGovernanceTrace(numericUserId),
        getAdminUserCreditTrace(numericUserId)
      ]);
      setReportTraces(governance);
      setCreditTraces(credit);
    } catch (error) {
      notify("error", messageOf(error));
    } finally {
      setLoading(false);
    }
  }, [notify]);

  const runReportTrace = useCallback(async (rawReportId: string) => {
    const numericReportId = Number(rawReportId);
    if (!numericReportId) {
      notify("error", "请输入有效的举报 ID");
      return;
    }
    setLoading(true);
    try {
      setReportLookupTraces(await getAdminReportTrace(numericReportId));
    } catch (error) {
      notify("error", messageOf(error));
    } finally {
      setLoading(false);
    }
  }, [notify]);

  // 外部带入初始 ID 时自动查询，免去再点一次。
  useEffect(() => {
    if (props.initialUserId) void runUserTrace(String(props.initialUserId));
  }, [props.initialUserId, runUserTrace]);
  useEffect(() => {
    if (props.initialReportId) void runReportTrace(String(props.initialReportId));
  }, [props.initialReportId, runReportTrace]);

  const loadUserTrace = (event?: FormEvent) => {
    event?.preventDefault();
    void runUserTrace(userId);
  };

  const loadReportTrace = (event?: FormEvent) => {
    event?.preventDefault();
    void runReportTrace(reportId);
  };

  // 不知道 ID 时先按昵称/用户名/邮箱搜人，点中后填入 ID 并直接查询。
  const searchUsers = async (event?: FormEvent) => {
    event?.preventDefault();
    if (!userKeyword.trim()) {
      setUserMatches([]);
      return;
    }
    setSearching(true);
    try {
      setUserMatches(await searchAdminTraceUsers(userKeyword.trim()));
    } catch (error) {
      notify("error", messageOf(error));
    } finally {
      setSearching(false);
    }
  };

  const pickUser = (user: GovernanceUser) => {
    setUserId(String(user.id));
    setUserMatches([]);
    setUserKeyword("");
    void runUserTrace(String(user.id));
  };

  return (
    <section className="admin-trace-page">
      <div className="order-toolbar">
        <div>
          <p className="eyebrow">治理追踪</p>
          <h2><ShieldAlert size={18} /> 用户与信用影响</h2>
        </div>
        <button className="icon-button subtle" type="button" aria-label="刷新追踪结果" onClick={() => void runUserTrace(userId)}><RefreshCw size={17} /></button>
      </div>

      <div className="admin-trace-forms">
        {/* 按昵称/用户名/邮箱搜人，不必预先知道数字 ID */}
        <form className="log-filter-bar" onSubmit={searchUsers}>
          <label className="search-field">
            <input value={userKeyword} onChange={(event) => setUserKeyword(event.target.value)} placeholder="昵称 / 用户名 / 邮箱" />
          </label>
          <button className="secondary-button compact" type="submit" disabled={searching}><UserSearch size={15} /> 搜用户</button>
        </form>
        <form className="log-filter-bar" onSubmit={loadUserTrace}>
          <label className="search-field">
            <input value={userId} onChange={(event) => setUserId(event.target.value)} placeholder="用户 ID" />
          </label>
          <button className="primary-button compact" type="submit"><Search size={15} /> 查询用户</button>
        </form>
        <form className="log-filter-bar" onSubmit={loadReportTrace}>
          <label className="search-field">
            <input value={reportId} onChange={(event) => setReportId(event.target.value)} placeholder="举报 ID" />
          </label>
          <button className="secondary-button compact" type="submit"><Search size={15} /> 查询举报</button>
        </form>
      </div>

      {userMatches.length > 0 && (
        <div className="admin-trace-matches">
          {userMatches.map((user) => (
            <button className="badge neutral" type="button" key={user.id} onClick={() => pickUser(user)}>
              {user.nickname} #{user.id}
            </button>
          ))}
        </div>
      )}

      {loading ? <LoadingBlock /> : (
        <div className="admin-trace-grid">
          <TracePanel title="用户关联举报" empty="输入用户 ID 后查看其提交或被关联的举报">
            {reportTraces.map((item, index) => <ReportTraceRow key={`${item.reportId}-${item.relatedUserRole}-${index}`} item={item} />)}
          </TracePanel>
          <TracePanel title="用户信用流水" empty="输入用户 ID 后查看信用来源">
            {creditTraces.map((item) => <CreditTraceRow key={item.creditRecordId} item={item} />)}
          </TracePanel>
          <TracePanel title="举报关联用户" empty="输入举报 ID 后查看举报牵涉用户">
            {reportLookupTraces.map((item, index) => <ReportTraceRow key={`${item.reportId}-lookup-${item.relatedUserRole}-${index}`} item={item} />)}
          </TracePanel>
        </div>
      )}
    </section>
  );
}

function TracePanel(props: { title: string; empty: string; children: ReactNode }) {
  const items = Array.isArray(props.children) ? props.children.filter(Boolean) : props.children ? [props.children] : [];
  return (
    <section className="record-column admin-trace-panel">
      <h2>{props.title}</h2>
      {items.length === 0 ? <EmptyBlock title={props.empty} /> : props.children}
    </section>
  );
}

function ReportTraceRow(props: { item: AdminReportUserTraceItem }) {
  const item = props.item;
  return (
    <article className="record-row">
      <div>
        <strong>举报 #{item.reportId} · {targetLabels[item.targetType] ?? item.targetType} #{item.targetId}</strong>
        <p>{relationLabels[item.relatedUserRole] ?? item.relatedUserRole}：{item.relatedUser.nickname} #{item.relatedUser.id}</p>
        <p>{item.description}</p>
      </div>
      <div className="badge-row">
        <span className="badge neutral">{item.status}</span>
        <span className="badge neutral">{item.priority}</span>
        {item.penaltyId && <span className="badge warning">处罚 #{item.penaltyId} · {item.penaltyStatus}</span>}
        {item.appealId && <span className="badge success">申诉 #{item.appealId} · {item.appealStatus}</span>}
      </div>
      <small className="muted-line">举报人：{item.reporter.nickname} #{item.reporter.id} · {formatDate(item.createdAt)}</small>
    </article>
  );
}

function CreditTraceRow(props: { item: AdminCreditTraceItem }) {
  const item = props.item;
  const deltaTone = item.internalDeltaValue < 0 ? "danger" : item.internalDeltaValue > 0 ? "success" : "neutral";
  return (
    <article className="record-row">
      <div>
        <strong>{item.sourceLabel} · {item.publicLabel}</strong>
        <p>{item.reason}</p>
      </div>
      <div className="badge-row">
        <span className={`badge ${deltaTone}`}>{item.internalDeltaValue > 0 ? "+" : ""}{item.internalDeltaValue}</span>
        <span className="badge neutral">{item.sourceType}</span>
        {item.createdByAdminId && <span className="badge neutral">管理员 #{item.createdByAdminId}</span>}
      </div>
      <small className="muted-line">{formatDate(item.createdAt)}</small>
    </article>
  );
}
