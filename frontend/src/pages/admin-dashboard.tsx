import { BarChart3, RefreshCw, ShieldAlert, TrendingUp, Users } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import {
  getAdminDashboard,
  getOrderTrend,
  type DashboardStats,
  type OrderDailyTrendItem
} from "../api/admin";

type Notify = (tone: "success" | "error", text: string) => void;

// ─────────────────────────────────────────────
// 主组件
// ─────────────────────────────────────────────

export function AdminDashboardPage({ notify }: { notify: Notify }) {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [trend, setTrend] = useState<OrderDailyTrendItem[]>([]);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const [dashData, trendData] = await Promise.all([
        getAdminDashboard(),
        getOrderTrend()
      ]);
      setStats(dashData);
      // 趋势图需要从旧到新展示，后端是倒序，这里翻转
      setTrend([...trendData].reverse());
    } catch (err) {
      notify("error", err instanceof Error ? err.message : "统计数据加载失败");
    } finally {
      setLoading(false);
    }
  }, [notify]);

  useEffect(() => { void refresh(); }, [refresh]);

  if (loading) {
    return (
      <div className="state-block">
        <RefreshCw className="spin" size={24} />
        <strong>加载统计数据...</strong>
      </div>
    );
  }

  if (!stats) return <div className="state-block"><strong>暂无数据</strong></div>;

  const { orders, payments, settlements, goods, reviews, users, campusAuths, operationLogs } = stats;
  const orderPending = orders.pendingSellerConfirm + orders.pendingPayment;

  return (
    <div className="dashboard-page">
      {/* 顶部刷新 */}
      <div className="dashboard-header">
        <div>
          <p className="eyebrow">运营概览</p>
          <h2>数据看板</h2>
        </div>
        <button className="icon-button subtle" type="button" onClick={() => void refresh()} title="刷新">
          <RefreshCw size={17} />
        </button>
      </div>

      {/* 核心指标卡片行 */}
      <div className="stat-grid">
        <StatCard
          icon={<BarChart3 size={20} />}
          label="待处理订单"
          value={orderPending}
          sub={`共 ${orders.totalOrders} 笔 · 已完成 ${orders.completed}`}
          tone={orderPending > 0 ? "warning" : "neutral"}
        />
        <StatCard
          icon={<TrendingUp size={20} />}
          label="商品审核待处理"
          value={goods.auditPending}
          sub={`在售 ${goods.onSale} · 共 ${goods.totalGoods} 件`}
          tone={goods.auditPending > 0 ? "warning" : "neutral"}
        />
        <StatCard
          icon={<ShieldAlert size={20} />}
          label="认证待审核"
          value={campusAuths.pendingReview}
          sub={`已认证 ${campusAuths.approved} · 共 ${campusAuths.totalVerifications}`}
          tone={campusAuths.pendingReview > 0 ? "warning" : "neutral"}
        />
        <StatCard
          icon={<Users size={20} />}
          label="今日新用户"
          value={users.newToday}
          sub={`本月 +${users.newThisMonth} · 总计 ${users.totalUsers}`}
          tone="neutral"
        />
      </div>

      {/* 财务指标行 */}
      <div className="stat-section-title">资金流水</div>
      <div className="stat-grid">
        <AmountCard label="活跃冻结金额" amount={orders.activeFrozenAmount} sub="占用中订单" />
        <AmountCard label="已完成流水" amount={orders.completedAmount} sub="已成交订单总额" />
        <AmountCard label="托管中金额" amount={payments.escrowedAmount} sub="支付托管" />
        <AmountCard label="已结算金额" amount={settlements.totalSettledAmount} sub="结算完成" />
      </div>

      {/* 订单状态分布 */}
      <div className="stat-section-title">订单状态分布</div>
      <div className="status-breakdown">
        <StatusBar label="待卖家确认" value={orders.pendingSellerConfirm} total={orders.totalOrders} tone="warning" />
        <StatusBar label="待支付" value={orders.pendingPayment} total={orders.totalOrders} tone="warning" />
        <StatusBar label="待面交" value={orders.paidPendingMeetup} total={orders.totalOrders} tone="info" />
        <StatusBar label="待结算" value={orders.completedPendingSettlement} total={orders.totalOrders} tone="info" />
        <StatusBar label="已完成" value={orders.completed} total={orders.totalOrders} tone="success" />
        <StatusBar label="已取消" value={orders.cancelled} total={orders.totalOrders} tone="danger" />
        <StatusBar label="纠纷中" value={orders.disputeProcessing} total={orders.totalOrders} tone="danger" />
      </div>

      {/* 近 30 天每日订单趋势 */}
      <div className="stat-section-title">近 30 天订单趋势</div>
      {trend.length === 0
        ? <p className="dashboard-empty">暂无近 30 天订单数据</p>
        : <TrendChart data={trend} />
      }

      {/* 评价与日志摘要 */}
      <div className="stat-section-title">评价 · 日志摘要</div>
      <div className="stat-grid">
        <StatCard
          icon={<BarChart3 size={20} />}
          label="平均评分"
          value={reviews.avgRating !== null ? `${reviews.avgRating} ★` : "暂无"}
          sub={`共 ${reviews.totalReviews} 条 · 隐藏 ${reviews.hidden}`}
          tone="neutral"
        />
        <StatCard
          icon={<ShieldAlert size={20} />}
          label="今日操作日志"
          value={operationLogs.todayCount}
          sub={`成功 ${operationLogs.successCount} · 失败 ${operationLogs.failureCount}`}
          tone={operationLogs.failureCount > 0 ? "warning" : "neutral"}
        />
        <StatCard
          icon={<Users size={20} />}
          label="支付处理"
          value={payments.totalPayments}
          sub={`托管中 ${payments.escrowed} · 失败 ${payments.failed}`}
          tone={payments.failed > 0 ? "warning" : "neutral"}
        />
        <StatCard
          icon={<TrendingUp size={20} />}
          label="结算进度"
          value={settlements.settled}
          sub={`待结算 ${settlements.pending + settlements.processing} · 失败 ${settlements.failed}`}
          tone={settlements.failed > 0 ? "warning" : "neutral"}
        />
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────
// 子组件
// ─────────────────────────────────────────────

function StatCard({
  icon,
  label,
  value,
  sub,
  tone
}: {
  icon: React.ReactNode;
  label: string;
  value: number | string;
  sub: string;
  tone: "neutral" | "warning" | "success" | "danger";
}) {
  return (
    <div className={`stat-card stat-card--${tone}`}>
      <div className="stat-card-icon">{icon}</div>
      <div className="stat-card-body">
        <span className="stat-card-label">{label}</span>
        <strong className="stat-card-value">{value}</strong>
        <span className="stat-card-sub">{sub}</span>
      </div>
    </div>
  );
}

function AmountCard({ label, amount, sub }: { label: string; amount: string; sub: string }) {
  const num = parseFloat(amount ?? "0");
  return (
    <div className="stat-card stat-card--neutral">
      <div className="stat-card-body">
        <span className="stat-card-label">{label}</span>
        <strong className="stat-card-value amount">¥{num.toFixed(2)}</strong>
        <span className="stat-card-sub">{sub}</span>
      </div>
    </div>
  );
}

function StatusBar({
  label,
  value,
  total,
  tone
}: {
  label: string;
  value: number;
  total: number;
  tone: "success" | "warning" | "danger" | "info";
}) {
  const pct = total > 0 ? Math.round((value / total) * 100) : 0;
  return (
    <div className="status-bar-row">
      <span className="status-bar-label">{label}</span>
      <div className="status-bar-track">
        <div
          className={`status-bar-fill status-bar-fill--${tone}`}
          style={{ width: `${pct}%` }}
          role="progressbar"
          aria-valuenow={pct}
          aria-valuemin={0}
          aria-valuemax={100}
        />
      </div>
      <span className="status-bar-count">{value}</span>
    </div>
  );
}

/**
 * 趋势折线图 —— 纯 CSS/SVG 实现，不依赖任何图表库。
 * 数据已从旧到新排序。
 */
function TrendChart({ data }: { data: OrderDailyTrendItem[] }) {
  const width = 600;
  const height = 120;
  const pad = { top: 8, right: 8, bottom: 24, left: 32 };
  const innerW = width - pad.left - pad.right;
  const innerH = height - pad.top - pad.bottom;

  const maxVal = Math.max(...data.map((d) => d.totalCreated), 1);

  const toX = (i: number) => pad.left + (i / (data.length - 1 || 1)) * innerW;
  const toY = (v: number) => pad.top + innerH - (v / maxVal) * innerH;

  const polyline = (getter: (d: OrderDailyTrendItem) => number, color: string) => {
    const pts = data.map((d, i) => `${toX(i)},${toY(getter(d))}`).join(" ");
    return <polyline key={color} points={pts} fill="none" stroke={color} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />;
  };
  const labelIndexes = [...new Set([0, Math.floor((data.length - 1) / 2), data.length - 1])];

  return (
    <div className="trend-chart-wrap">
      <svg viewBox={`0 0 ${width} ${height}`} className="trend-svg" aria-label="近 30 天订单趋势折线图" role="img">
        {/* Y 轴刻度线 */}
        {[0, 0.5, 1].map((r) => (
          <line
            key={r}
            x1={pad.left} y1={pad.top + innerH * (1 - r)}
            x2={pad.left + innerW} y2={pad.top + innerH * (1 - r)}
            stroke="#e5e7eb" strokeWidth={1}
          />
        ))}
        {/* 折线：建单量 / 完成 / 取消 */}
        {polyline((d) => d.totalCreated, "#6366f1")}
        {polyline((d) => d.completedCount, "#22c55e")}
        {polyline((d) => d.cancelledCount, "#f87171")}
        {/* X 轴标签：仅首、中、末 */}
        {labelIndexes.map((i) => {
          const d = data[i];
          if (!d) return null;
          return (
            <text key={i} x={toX(i)} y={height - 4} textAnchor="middle" fontSize={10} fill="#9ca3af">
              {d.statDate.slice(5)}
            </text>
          );
        })}
      </svg>
      {/* 图例 */}
      <div className="trend-legend">
        <span className="legend-item legend-item--indigo">建单量</span>
        <span className="legend-item legend-item--green">完成</span>
        <span className="legend-item legend-item--red">取消</span>
      </div>
    </div>
  );
}
