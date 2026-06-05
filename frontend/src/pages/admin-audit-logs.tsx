import { Filter, RefreshCw, ShieldCheck, ShieldOff } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import {
  getOperationLogs,
  getSensitiveAccessLogs,
  type OperationLogItem,
  type SensitiveAccessLogItem
} from "../api/admin";
import type { PageResponse } from "../api/types";

type Notify = (tone: "success" | "error", text: string) => void;
type LogTab = "operation" | "sensitive";

// ─────────────────────────────────────────────
// 主组件
// ─────────────────────────────────────────────

export function AdminAuditLogsPage({ notify }: { notify: Notify }) {
  const [tab, setTab] = useState<LogTab>("operation");

  return (
    <div className="audit-logs-page">
      <div className="page-heading">
        <div>
          <p className="eyebrow">安全审计</p>
          <h2>审计日志</h2>
        </div>
      </div>

      {/* Tab 切换 */}
      <div className="segmented-control admin-tabs">
        <button
          className={tab === "operation" ? "active" : ""}
          type="button"
          onClick={() => setTab("operation")}
        >
          <ShieldCheck size={15} /> 操作日志
        </button>
        <button
          className={tab === "sensitive" ? "active" : ""}
          type="button"
          onClick={() => setTab("sensitive")}
        >
          <ShieldOff size={15} /> 敏感访问日志
        </button>
      </div>

      {/* 内容区 */}
      {tab === "operation"
        ? <OperationLogsPanel notify={notify} />
        : <SensitiveAccessLogsPanel notify={notify} />
      }
    </div>
  );
}

// ─────────────────────────────────────────────
// 操作日志面板
// ─────────────────────────────────────────────

function OperationLogsPanel({ notify }: { notify: Notify }) {
  const [data, setData] = useState<PageResponse<OperationLogItem> | null>(null);
  const [loading, setLoading] = useState(false);
  const [filter, setFilter] = useState({ action: "", result: "", startTime: "", endTime: "" });
  const [applied, setApplied] = useState(filter);
  const [page, setPage] = useState(1);
  const pageSize = 20;

  const load = useCallback(async (p: number, f: typeof filter) => {
    setLoading(true);
    try {
      const result = await getOperationLogs({
        action: f.action || undefined,
        result: f.result || undefined,
        startTime: f.startTime ? new Date(f.startTime).toISOString() : undefined,
        endTime: f.endTime ? new Date(f.endTime).toISOString() : undefined,
        page: p,
        pageSize
      });
      setData(result);
    } catch (err) {
      notify("error", err instanceof Error ? err.message : "加载操作日志失败");
    } finally {
      setLoading(false);
    }
  }, [notify]);

  useEffect(() => { void load(page, applied); }, [load, page, applied]);

  const applyFilter = () => {
    setPage(1);
    setApplied({ ...filter });
  };

  const resetFilter = () => {
    const empty = { action: "", result: "", startTime: "", endTime: "" };
    setFilter(empty);
    setPage(1);
    setApplied(empty);
  };

  return (
    <div className="log-panel">
      {/* 筛选栏 */}
      <div className="log-filter-bar">
        <label className="filter-field">
          <span>操作类型</span>
          <input
            placeholder="如 GOODS_APPROVE"
            value={filter.action}
            onChange={(e) => setFilter({ ...filter, action: e.target.value })}
            onKeyDown={(e) => e.key === "Enter" && applyFilter()}
          />
        </label>
        <label className="filter-field">
          <span>结果</span>
          <select value={filter.result} onChange={(e) => setFilter({ ...filter, result: e.target.value })}>
            <option value="">全部</option>
            <option value="SUCCESS">成功</option>
            <option value="FAILURE">失败</option>
            <option value="PARTIAL">部分</option>
          </select>
        </label>
        <label className="filter-field">
          <span>开始时间</span>
          <input type="datetime-local" value={filter.startTime} onChange={(e) => setFilter({ ...filter, startTime: e.target.value })} />
        </label>
        <label className="filter-field">
          <span>结束时间</span>
          <input type="datetime-local" value={filter.endTime} onChange={(e) => setFilter({ ...filter, endTime: e.target.value })} />
        </label>
        <button className="primary-button compact" type="button" onClick={applyFilter}>
          <Filter size={15} /> 筛选
        </button>
        <button className="secondary-button compact" type="button" onClick={resetFilter}>
          重置
        </button>
      </div>

      {/* 表格 */}
      {loading ? (
        <div className="state-block"><RefreshCw className="spin" size={20} /><strong>加载中</strong></div>
      ) : (
        <div className="log-table-wrap">
          <table className="log-table">
            <thead>
              <tr>
                <th>时间</th>
                <th>操作类型</th>
                <th>目标</th>
                <th>方法</th>
                <th>路径</th>
                <th>结果</th>
                <th>管理员 ID</th>
              </tr>
            </thead>
            <tbody>
              {!data || data.items.length === 0
                ? <tr><td colSpan={7} className="log-empty">暂无日志记录</td></tr>
                : data.items.map((item) => (
                  <tr key={item.id}>
                    <td className="log-time">{formatDateTime(item.createdAt)}</td>
                    <td><code className="log-action">{item.action}</code></td>
                    <td className="log-target">{item.targetType}{item.targetId != null ? ` #${item.targetId}` : ""}</td>
                    <td><span className="badge neutral">{item.httpMethod ?? "—"}</span></td>
                    <td className="log-path" title={item.requestPath ?? ""}>{item.requestPath ?? "—"}</td>
                    <td><ResultBadge result={item.result} /></td>
                    <td>{item.adminId ?? "系统"}</td>
                  </tr>
                ))
              }
            </tbody>
          </table>
        </div>
      )}

      {/* 分页 */}
      {data && <Pagination page={page} pageSize={pageSize} total={data.total} onChange={setPage} />}
    </div>
  );
}

// ─────────────────────────────────────────────
// 敏感访问日志面板
// ─────────────────────────────────────────────

function SensitiveAccessLogsPanel({ notify }: { notify: Notify }) {
  const [data, setData] = useState<PageResponse<SensitiveAccessLogItem> | null>(null);
  const [loading, setLoading] = useState(false);
  const [filter, setFilter] = useState({ targetType: "", adminId: "", startTime: "", endTime: "" });
  const [applied, setApplied] = useState(filter);
  const [page, setPage] = useState(1);
  const pageSize = 20;

  const load = useCallback(async (p: number, f: typeof filter) => {
    setLoading(true);
    try {
      const result = await getSensitiveAccessLogs({
        targetType: f.targetType || undefined,
        adminId: f.adminId ? Number(f.adminId) : undefined,
        startTime: f.startTime ? new Date(f.startTime).toISOString() : undefined,
        endTime: f.endTime ? new Date(f.endTime).toISOString() : undefined,
        page: p,
        pageSize
      });
      setData(result);
    } catch (err) {
      notify("error", err instanceof Error ? err.message : "加载敏感访问日志失败");
    } finally {
      setLoading(false);
    }
  }, [notify]);

  useEffect(() => { void load(page, applied); }, [load, page, applied]);

  const applyFilter = () => {
    setPage(1);
    setApplied({ ...filter });
  };

  const resetFilter = () => {
    const empty = { targetType: "", adminId: "", startTime: "", endTime: "" };
    setFilter(empty);
    setPage(1);
    setApplied(empty);
  };

  return (
    <div className="log-panel">
      <p className="audit-notice">
        <ShieldOff size={14} /> 敏感材料访问记录受审计保护，所有访问行为均已留痕
      </p>

      {/* 筛选栏 */}
      <div className="log-filter-bar">
        <label className="filter-field">
          <span>目标类型</span>
          <select value={filter.targetType} onChange={(e) => setFilter({ ...filter, targetType: e.target.value })}>
            <option value="">全部</option>
            <option value="CAMPUS_AUTH_MATERIAL">认证材料</option>
          </select>
        </label>
        <label className="filter-field">
          <span>管理员 ID</span>
          <input
            type="number"
            placeholder="输入管理员 ID"
            value={filter.adminId}
            onChange={(e) => setFilter({ ...filter, adminId: e.target.value })}
          />
        </label>
        <label className="filter-field">
          <span>开始时间</span>
          <input type="datetime-local" value={filter.startTime} onChange={(e) => setFilter({ ...filter, startTime: e.target.value })} />
        </label>
        <label className="filter-field">
          <span>结束时间</span>
          <input type="datetime-local" value={filter.endTime} onChange={(e) => setFilter({ ...filter, endTime: e.target.value })} />
        </label>
        <button className="primary-button compact" type="button" onClick={applyFilter}>
          <Filter size={15} /> 筛选
        </button>
        <button className="secondary-button compact" type="button" onClick={resetFilter}>
          重置
        </button>
      </div>

      {/* 表格 */}
      {loading ? (
        <div className="state-block"><RefreshCw className="spin" size={20} /><strong>加载中</strong></div>
      ) : (
        <div className="log-table-wrap">
          <table className="log-table">
            <thead>
              <tr>
                <th>时间</th>
                <th>管理员 ID</th>
                <th>目标类型</th>
                <th>目标 ID</th>
                <th>原因</th>
                <th>结果</th>
                <th>IP</th>
              </tr>
            </thead>
            <tbody>
              {!data || data.items.length === 0
                ? <tr><td colSpan={7} className="log-empty">暂无敏感访问记录</td></tr>
                : data.items.map((item) => (
                  <tr key={item.id}>
                    <td className="log-time">{formatDateTime(item.createdAt)}</td>
                    <td>{item.adminId ?? "—"}</td>
                    <td><code className="log-action">{item.targetType}</code></td>
                    <td>{item.targetId}</td>
                    <td className="log-reason" title={item.reason}>{item.reason}</td>
                    <td><ResultBadge result={item.result} /></td>
                    <td className="log-ip">{item.ipAddress ?? "—"}</td>
                  </tr>
                ))
              }
            </tbody>
          </table>
        </div>
      )}

      {/* 分页 */}
      {data && <Pagination page={page} pageSize={pageSize} total={data.total} onChange={setPage} />}
    </div>
  );
}

// ─────────────────────────────────────────────
// 共用子组件
// ─────────────────────────────────────────────

function ResultBadge({ result }: { result: string }) {
  const tone = result === "SUCCESS" || result === "ALLOWED"
    ? "success"
    : result === "FAILURE" || result === "DENIED"
    ? "danger"
    : "warning";
  const label: Record<string, string> = {
    SUCCESS: "成功", FAILURE: "失败", PARTIAL: "部分",
    ALLOWED: "允许", DENIED: "拒绝", FAILED: "异常"
  };
  return <span className={`badge ${tone}`}>{label[result] ?? result}</span>;
}

function Pagination({
  page,
  pageSize,
  total,
  onChange
}: {
  page: number;
  pageSize: number;
  total: number;
  onChange: (p: number) => void;
}) {
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  if (totalPages <= 1) return null;

  return (
    <div className="pagination" role="navigation" aria-label="分页导航">
      <button
        className="secondary-button compact"
        type="button"
        disabled={page <= 1}
        onClick={() => onChange(page - 1)}
      >
        上一页
      </button>
      <span className="pagination-info">
        第 {page} / {totalPages} 页，共 {total} 条
      </span>
      <button
        className="secondary-button compact"
        type="button"
        disabled={page >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        下一页
      </button>
    </div>
  );
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString("zh-CN", {
    year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit"
  });
}
