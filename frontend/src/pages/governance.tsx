// 文件功能：提供治理与信用中心页面，聚合举报、申诉、退款记录、收藏关注、管理员待办和信用画像。
import { AlertTriangle, RefreshCw, ShieldAlert } from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import {
  decideRefund,
  getGovernanceOverview,
  GovernanceOverview,
  handleReport,
  liftPenalty,
  reviewAppeal,
  submitAppeal
} from "../api/governance";
import { uploadFile } from "../api/m1";
import type { CurrentUser } from "../api/types";

type Notify = (tone: "success" | "error", text: string) => void;

const reportStatusLabels: Record<string, string> = {
  PENDING: "待处理",
  PROCESSING: "处理中",
  UPHELD: "举报成立",
  REJECTED: "未成立",
  CLOSED: "已关闭"
};

const appealStatusLabels: Record<string, string> = {
  PENDING_REVIEW: "待复核",
  APPROVED: "申诉通过",
  REJECTED: "申诉驳回",
  CLOSED: "已关闭"
};

const refundStatusLabels: Record<string, string> = {
  PENDING: "待处理",
  PROCESSING: "处理中",
  REFUNDED: "已退款",
  FAILED: "退款失败",
  CLOSED: "已关闭"
};

const penaltyTypeLabels: Record<string, string> = {
  WARNING: "警告",
  TRADE_RESTRICT: "限制交易",
  ACCOUNT_LOCK: "账号锁定"
};

export function GovernancePage(props: { currentUser: CurrentUser; notify: Notify }) {
  const [overview, setOverview] = useState<GovernanceOverview | null>(null);
  const [busy, setBusy] = useState(false);
  const [appealForm, setAppealForm] = useState({ reportId: "", description: "" });
  const [appealEvidenceIds, setAppealEvidenceIds] = useState<number[]>([]);

  const load = useCallback(async () => {
    try {
      setOverview(await getGovernanceOverview());
    } catch (error) {
      props.notify("error", messageOf(error));
    }
  }, [props]);

  useEffect(() => { void load(); }, [load]);

  const appealableReports = useMemo(() => overview?.reports ?? [], [overview?.reports]);

  useEffect(() => {
    if (!appealForm.reportId && appealableReports.length > 0) {
      setAppealForm((current) => ({ ...current, reportId: String(appealableReports[0].id) }));
    }
  }, [appealForm.reportId, appealableReports]);

  const run = async (task: () => Promise<unknown>, success: string) => {
    setBusy(true);
    try {
      await task();
      props.notify("success", success);
      await load();
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  const onAppeal = (event: FormEvent) => {
    event.preventDefault();
    if (!appealForm.reportId) {
      props.notify("error", "请先选择要申诉的举报记录");
      return;
    }
    void run(() => submitAppeal({
      reportId: Number(appealForm.reportId),
      description: appealForm.description,
      evidenceFileIds: appealEvidenceIds
    }), "申诉已提交");
  };

  const uploadAppealEvidence = async (file?: File) => {
    if (!file) return;
    await run(async () => {
      const uploaded = await uploadFile(file, "APPEAL_EVIDENCE");
      setAppealEvidenceIds((current) => [...current, uploaded.id]);
    }, "证据文件已上传");
  };

  const adminQueue = overview?.adminQueue;

  return (
    <section>
      <section className="page-heading">
        <div>
          <p className="eyebrow">平台治理</p>
          <h1>治理与信用中心</h1>
          <p>集中查看举报与收藏关注记录、提交申诉与退款、查看信用画像，管理员账号可直接处理待办。</p>
        </div>
        <button className="secondary-button" disabled={busy} type="button" onClick={() => void load()}><RefreshCw size={17} /> 刷新</button>
      </section>

      {!overview ? <LoadingBlock /> : (
        <>
          <section id="governance-credit-section" className="section-title-row">
            <div>
              <p className="eyebrow">信用聚合</p>
              <h2>信用画像</h2>
            </div>
            <span className="badge neutral">订单 / 评价 / 处罚聚合</span>
          </section>
          <section className="governance-metrics" aria-label="信用摘要">
            <Metric title="信用等级" value={overview.credit.internalLevel} text={`${overview.credit.internalScore} 分`} />
            <Metric title="完成交易" value={overview.credit.fulfillmentCount} text="含待结算完成单" />
            <Metric title="好评记录" value={overview.credit.positiveReviewCount} text="4 星及以上" />
            <Metric title="负面记录" value={overview.credit.negativeEventCount} text={overview.credit.publicTags.join(" / ")} />
          </section>
          <section className="credit-rule-panel" aria-label="信用评分公式">
            <strong>评分口径</strong>
            <p>基准 80 分，完成交易每笔 +2，4 星及以上好评每条 +3，有效处罚或 2 星及以下评价每条 -15，最终限制在 0 到 100 分；A 为 90 分及以上，B 为 75 到 89 分，C 为 60 到 74 分，D 为 60 分以下。</p>
          </section>

          <div className="governance-layout">
            <form id="governance-appeal-section" className="form-panel" onSubmit={onAppeal}>
              <div className="panel-title"><h2><ShieldAlert size={17} /> 提交申诉</h2></div>
              <FormField label="关联举报记录">
                <select required value={appealForm.reportId} onChange={(event) => setAppealForm({ ...appealForm, reportId: event.target.value })}>
                  {appealableReports.length === 0 ? <option value="">暂无可选举报记录</option> : appealableReports.map((item) => (
                    <option value={item.id} key={item.id}>
                      #{item.id} · {targetLabel(item.targetType)} {item.targetId} · {reportStatusLabels[item.status] ?? item.status}
                    </option>
                  ))}
                </select>
              </FormField>
              <FormField label="申诉说明">
                <textarea required rows={3} value={appealForm.description} onChange={(event) => setAppealForm({ ...appealForm, description: event.target.value })} />
              </FormField>
              <label className="upload-zone compact-zone">
                <ShieldAlert size={19} />
                <span>上传申诉证据</span>
                <input accept="image/jpeg,image/png,image/webp" type="file" onChange={(event) => void uploadAppealEvidence(event.target.files?.[0])} />
              </label>
              {appealEvidenceIds.length > 0 && <p className="form-hint">已关联证据 #{appealEvidenceIds.join(", #")}</p>}
              <button className="primary-button full-width" disabled={busy || appealableReports.length === 0} type="submit">提交申诉</button>
              {appealableReports.length === 0 && <p className="form-hint">暂无举报记录时不能发起申诉；退款请在对应订单详情页申请。</p>}
            </form>
          </div>

          <section id="governance-report-section" className="governance-board">
            <RecordColumn title="我的举报" empty="暂无举报记录">
              {overview.reports.map((item) => <RecordRow key={item.id} title={`#${item.id} ${targetLabel(item.targetType)} ${item.targetId}`} badge={reportStatusLabels[item.status] ?? item.status} text={item.description} />)}
            </RecordColumn>
            <RecordColumn title="我的申诉" empty="暂无申诉记录">
              {overview.appeals.map((item) => <RecordRow key={item.id} title={`#${item.id} 关联举报 ${item.reportId}`} badge={appealStatusLabels[item.status] ?? item.status} text={item.description} />)}
            </RecordColumn>
            <RecordColumn title="我的退款" empty="暂无退款记录">
              {overview.refunds.map((item) => <RecordRow key={item.id} title={`${item.refundNo} · 订单 ${item.orderId}`} badge={refundStatusLabels[item.status] ?? item.status} text={`¥${item.amount} · ${item.reason}`} />)}
            </RecordColumn>
            <RecordColumn title="收藏关注" empty="暂无收藏或关注">
              {overview.favorites.map((item) => <RecordRow key={`f-${item.id}`} title={item.goodsTitle} badge={`¥${item.goodsPrice}`} text={`卖家：${item.seller.nickname}`} />)}
              {overview.follows.map((item) => <RecordRow key={`u-${item.id}`} title={item.followedUser.nickname} badge="已关注" text={`用户 ID：${item.followedUser.id}`} />)}
            </RecordColumn>
          </section>

          {adminQueue && (
            <section id="governance-admin-section" className="admin-governance">
              <div className="panel-title"><h2><AlertTriangle size={18} /> 管理员待办</h2><span className="badge neutral">治理队列</span></div>
              <div className="governance-board">
                <RecordColumn title="举报处理" empty="没有待处理举报">
                  {adminQueue.pendingReports.map((item) => (
                    <RecordRow key={item.id} title={`举报 #${item.id} · ${item.reporter.nickname}`} badge={reportStatusLabels[item.status] ?? item.status} text={`待核实内容：${item.description}`}>
                      <button className="secondary-button compact" disabled={busy} type="button" onClick={() => void run(() => handleReport(item.id, { status: "UPHELD", handlingNote: "管理员核实举报成立", penaltyUserId: item.targetType === "USER" ? item.targetId : null, penaltyType: "WARNING" }), "举报已处理")}>成立</button>
                      <button className="text-button" disabled={busy} type="button" onClick={() => void run(() => handleReport(item.id, { status: "REJECTED", handlingNote: "证据不足，举报不成立" }), "举报已驳回")}>驳回</button>
                    </RecordRow>
                  ))}
                </RecordColumn>
                <RecordColumn title="申诉复核" empty="没有待复核申诉">
                  {adminQueue.pendingAppeals.map((item) => (
                    <RecordRow key={item.id} title={`申诉 #${item.id} · ${item.appellant.nickname}`} badge={appealStatusLabels[item.status] ?? item.status} text={item.description}>
                      <button className="secondary-button compact" disabled={busy} type="button" onClick={() => void run(() => reviewAppeal(item.id, { status: "APPROVED", reviewNote: "申诉材料有效，予以通过" }), "申诉已通过")}>通过</button>
                      <button className="text-button" disabled={busy} type="button" onClick={() => void run(() => reviewAppeal(item.id, { status: "REJECTED", reviewNote: "申诉材料不足，维持原处理" }), "申诉已驳回")}>驳回</button>
                    </RecordRow>
                  ))}
                </RecordColumn>
                <RecordColumn title="退款工单" empty="没有退款待办">
                  {adminQueue.pendingRefunds.map((item) => (
                    <RecordRow key={item.id} title={`${item.refundNo} · ¥${item.amount}`} badge={refundStatusLabels[item.status] ?? item.status} text={item.reason}>
                      <button className="secondary-button compact" disabled={busy} type="button" onClick={() => void run(() => decideRefund(item.id, { status: "REFUNDED", decisionNote: "已完成模拟退款处理" }), "退款已处理")}>退款</button>
                      <button className="text-button" disabled={busy} type="button" onClick={() => void run(() => decideRefund(item.id, { status: "FAILED", decisionNote: "退款条件不满足" }), "退款失败")}>拒绝</button>
                    </RecordRow>
                  ))}
                </RecordColumn>
                <RecordColumn title="有效处罚" empty="没有有效处罚">
                  {adminQueue.activePenalties.map((item) => (
                    <RecordRow key={item.id} title={`${item.user.nickname} · ${penaltyTypeLabels[item.penaltyType] ?? item.penaltyType}`} badge={item.status} text={item.reason}>
                      <button className="secondary-button compact" disabled={busy} type="button" onClick={() => void run(() => liftPenalty(item.id, "管理员复核后解除处罚"), "处罚已解除")}>解除</button>
                    </RecordRow>
                  ))}
                </RecordColumn>
              </div>
            </section>
          )}
        </>
      )}
    </section>
  );
}

function Metric(props: { title: string; value: string | number; text: string }) {
  return <article className="metric-tile"><span>{props.title}</span><strong>{props.value}</strong><small>{props.text}</small></article>;
}

function FormField(props: { label: string; children: React.ReactNode }) {
  return <label className="form-field"><span>{props.label}</span>{props.children}</label>;
}

function RecordColumn(props: { title: string; empty: string; children: React.ReactNode }) {
  const items = Array.isArray(props.children) ? props.children.filter(Boolean) : props.children ? [props.children] : [];
  return <section className="record-column"><h2>{props.title}</h2>{items.length === 0 ? <p className="empty-line">{props.empty}</p> : props.children}</section>;
}

function RecordRow(props: { title: string; badge: string; text: string; children?: React.ReactNode }) {
  return (
    <article className="record-row">
      <div><strong>{props.title}</strong><p>{props.text}</p></div>
      <span className="badge neutral">{props.badge}</span>
      {props.children && <div className="button-row">{props.children}</div>}
    </article>
  );
}

function LoadingBlock() {
  return <div className="state-block"><RefreshCw className="spin" size={24} /><strong>加载中</strong></div>;
}

function targetLabel(value: string) {
  return ({ GOODS: "商品", ORDER: "订单", USER: "用户" } as Record<string, string>)[value] ?? value;
}

function messageOf(error: unknown) {
  return error instanceof Error ? error.message : "请求失败，请稍后重试";
}
