// 文件功能：后台验收闭环页（数据看板、认证/商品审核、审计日志、资金、账号管理）。原内联于 App.tsx。
import { BarChart3, CreditCard, History, ShieldAlert, ShieldCheck, UserCog } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { getAdminGoods, getAdminVerifications, reviewGoods, reviewVerification } from "../api/m1";
import type { CampusVerification, GoodsSummary } from "../api/types";
import { EmptyBlock, PageHeading, ReviewActions, StatusBadge } from "../components/ui";
import { auditStatusLabels, goodsStatusLabels, messageOf, verificationStatusLabels, type Notify, type Route } from "../shared/app-shared";
import { AdminDashboardPage } from "./admin-dashboard";
import { AdminAuditLogsPage } from "./admin-audit-logs";
import { AdminFundsPage } from "./admin-funds";
import { AdminGovernanceTracePage } from "./admin-governance-trace";
import { AdminUsersPage } from "./admin-users";

type AdminTab = "verification" | "goods" | "dashboard" | "audit" | "funds" | "users" | "governanceTrace";

export function AdminPage(props: { notify: Notify; navigate: (route: Route) => void; isSuperAdmin: boolean; traceUserId?: number; traceReportId?: number }) {
  const [tab, setTab] = useState<AdminTab>("dashboard");
  const [verifications, setVerifications] = useState<CampusVerification[]>([]);
  const [goods, setGoods] = useState<GoodsSummary[]>([]);
  const [busy, setBusy] = useState(false);
  // 治理追踪的初始目标：来自账号管理「追踪」按钮（同页）或举报队列跳转（路由参数）。
  const [traceTarget, setTraceTarget] = useState<{ userId?: number; reportId?: number }>({});

  // 路由带入 traceUserId/traceReportId 时（举报队列跨页跳转）自动切到追踪页并预填。
  useEffect(() => {
    if (props.traceUserId || props.traceReportId) {
      setTraceTarget({ userId: props.traceUserId, reportId: props.traceReportId });
      setTab("governanceTrace");
    }
  }, [props.traceUserId, props.traceReportId]);

  const openUserTrace = (userId: number) => {
    setTraceTarget({ userId });
    setTab("governanceTrace");
  };

  const load = useCallback(async () => {
    try {
      const [verificationPage, goodsPage] = await Promise.all([
        getAdminVerifications("PENDING_REVIEW"),
        getAdminGoods("PENDING_REVIEW", "PENDING")
      ]);
      setVerifications(verificationPage.items);
      setGoods(goodsPage.items);
    } catch (error) {
      props.notify("error", messageOf(error));
    }
  }, [props.notify]);

  useEffect(() => {
    // 审核队列只在进入 verification/goods tab 时加载，避免看板页不必要的请求
    if (tab === "verification" || tab === "goods") {
      void load();
    }
  }, [load, tab]);

  const review = async (kind: "verification" | "goods", id: number, action: "approve" | "reject") => {
    const reason = window.prompt(action === "approve" ? "填写审核备注（可选）" : "填写驳回原因", "") ?? "";
    setBusy(true);
    try {
      if (kind === "verification") await reviewVerification(id, action, reason);
      else await reviewGoods(id, action, reason);
      props.notify("success", action === "approve" ? "审核已通过" : "记录已驳回");
      await load();
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section>
      <PageHeading eyebrow="后台验收" title="后台验收闭环" text="汇总统计看板、审核队列、审计日志和通知入口，演示关键状态变化可追溯。" />
      <div className="admin-demo-guide">
        <div>
          <p className="eyebrow">Demo flow</p>
          <h2>演示导航</h2>
        </div>
        <div className="admin-demo-actions">
          <button className="secondary-button compact" type="button" onClick={() => setTab("dashboard")}>查看统计看板</button>
          <button className="secondary-button compact" type="button" onClick={() => setTab("verification")}>认证审核</button>
          <button className="secondary-button compact" type="button" onClick={() => setTab("goods")}>商品审核</button>
          <button className="secondary-button compact" type="button" onClick={() => setTab("audit")}>查看审计日志</button>
          <button className="secondary-button compact" type="button" onClick={() => setTab("funds")}>资金管理</button>
          <button className="secondary-button compact" type="button" onClick={() => setTab("governanceTrace")}>治理追踪</button>
          {props.isSuperAdmin && <button className="secondary-button compact" type="button" onClick={() => setTab("users")}>账号管理</button>}
          <button className="primary-button compact" type="button" onClick={() => props.navigate({ name: "notifications" })}>通知列表</button>
        </div>
      </div>
      {/* 顶部 Tab 导航 */}
      <div className="segmented-control admin-tabs admin-tabs--wide">
        <button className={tab === "dashboard" ? "active" : ""} type="button" onClick={() => setTab("dashboard")}>
          <BarChart3 size={15} /> 数据看板
        </button>
        <button className={tab === "verification" ? "active" : ""} type="button" onClick={() => setTab("verification")}>
          <ShieldCheck size={15} /> 校园认证
          {verifications.length > 0 && <b>{verifications.length}</b>}
        </button>
        <button className={tab === "goods" ? "active" : ""} type="button" onClick={() => setTab("goods")}>
          <ShieldCheck size={15} /> 商品审核
          {goods.length > 0 && <b>{goods.length}</b>}
        </button>
        <button className={tab === "audit" ? "active" : ""} type="button" onClick={() => setTab("audit")}>
          <ShieldAlert size={15} /> 审计日志
        </button>
        <button className={tab === "funds" ? "active" : ""} type="button" onClick={() => setTab("funds")}>
          <CreditCard size={15} /> 资金
        </button>
        <button className={tab === "governanceTrace" ? "active" : ""} type="button" onClick={() => setTab("governanceTrace")}>
          <History size={15} /> 治理追踪
        </button>
        {props.isSuperAdmin && (
          <button className={tab === "users" ? "active" : ""} type="button" onClick={() => setTab("users")}>
            <UserCog size={15} /> 账号
          </button>
        )}
      </div>

      {/* 数据看板 */}
      {tab === "dashboard" && <AdminDashboardPage notify={props.notify} />}

      {/* 审计日志 */}
      {tab === "audit" && <AdminAuditLogsPage notify={props.notify} />}

      {tab === "funds" && <AdminFundsPage notify={props.notify} />}

      {tab === "governanceTrace" && <AdminGovernanceTracePage notify={props.notify} initialUserId={traceTarget.userId} initialReportId={traceTarget.reportId} />}

      {/* 账号管理（仅超管） */}
      {tab === "users" && props.isSuperAdmin && <AdminUsersPage notify={props.notify} onTrace={openUserTrace} />}

      {/* 审核队列 */}
      {(tab === "verification" || tab === "goods") && (
        <div className="review-list">
          {tab === "verification" && (verifications.length === 0
            ? <EmptyBlock title="当前没有待审核的认证记录" />
            : verifications.map((item) => (
              <article className="review-row" key={item.id}>
                <div><strong>{item.realName} · {item.studentNo}</strong><p>{item.department} · {item.campusEmail}</p></div>
                <div className="review-meta"><span className="score">{item.score} 分</span><StatusBadge value={item.status} labels={verificationStatusLabels} /></div>
                <ReviewActions disabled={busy} onApprove={() => void review("verification", item.id!, "approve")} onReject={() => void review("verification", item.id!, "reject")} />
              </article>
            ))
          )}
          {tab === "goods" && (goods.length === 0
            ? <EmptyBlock title="当前没有待审核的商品" />
            : goods.map((item) => (
              <article className="review-row" key={item.id}>
                <div><strong>{item.title}</strong><p>{item.category.name} · {item.seller.nickname} · ¥{item.listPrice}</p></div>
                <div className="review-meta"><StatusBadge value={item.status} labels={goodsStatusLabels} /><StatusBadge value={item.auditStatus} labels={auditStatusLabels} /></div>
                <ReviewActions disabled={busy} onApprove={() => void review("goods", item.id, "approve")} onReject={() => void review("goods", item.id, "reject")} />
              </article>
            ))
          )}
        </div>
      )}
    </section>
  );
}
