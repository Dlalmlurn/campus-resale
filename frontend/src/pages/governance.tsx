import { AlertTriangle, BadgeCheck, FileWarning, Heart, RefreshCw, RotateCcw, ShieldAlert, UserPlus } from "lucide-react";
import { FormEvent, useCallback, useEffect, useState } from "react";
import {
  addFavorite,
  createRefund,
  decideRefund,
  getGovernanceOverview,
  GovernanceOverview,
  handleReport,
  liftPenalty,
  removeFavorite,
  reviewAppeal,
  submitAppeal,
  submitReport,
  followUser,
  unfollowUser
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
  const [reportForm, setReportForm] = useState({ targetType: "GOODS", targetId: "", reasonType: "FRAUD", description: "" });
  const [appealForm, setAppealForm] = useState({ reportId: "", description: "" });
  const [refundForm, setRefundForm] = useState({ orderId: "", refundType: "FULL", amount: "", reason: "" });
  const [favoriteId, setFavoriteId] = useState("");
  const [followId, setFollowId] = useState("");
  const [reportEvidenceIds, setReportEvidenceIds] = useState<number[]>([]);
  const [appealEvidenceIds, setAppealEvidenceIds] = useState<number[]>([]);

  const load = useCallback(async () => {
    try {
      setOverview(await getGovernanceOverview());
    } catch (error) {
      props.notify("error", messageOf(error));
    }
  }, [props]);

  useEffect(() => { void load(); }, [load]);

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

  const onReport = (event: FormEvent) => {
    event.preventDefault();
    void run(() => submitReport({
      targetType: reportForm.targetType,
      targetId: Number(reportForm.targetId),
      reasonType: reportForm.reasonType,
      description: reportForm.description,
      evidenceFileIds: reportEvidenceIds
    }), "举报已提交");
  };

  const onAppeal = (event: FormEvent) => {
    event.preventDefault();
    void run(() => submitAppeal({
      reportId: Number(appealForm.reportId),
      description: appealForm.description,
      evidenceFileIds: appealEvidenceIds
    }), "申诉已提交");
  };

  const uploadEvidence = async (file: File | undefined, fileKind: "REPORT_EVIDENCE" | "APPEAL_EVIDENCE") => {
    if (!file) return;
    await run(async () => {
      const uploaded = await uploadFile(file, fileKind);
      if (fileKind === "REPORT_EVIDENCE") setReportEvidenceIds((current) => [...current, uploaded.id]);
      else setAppealEvidenceIds((current) => [...current, uploaded.id]);
    }, "证据文件已上传");
  };

  const onRefund = (event: FormEvent) => {
    event.preventDefault();
    void run(() => createRefund({
      orderId: Number(refundForm.orderId),
      refundType: refundForm.refundType,
      amount: refundForm.amount,
      reason: refundForm.reason
    }), "退款申请已提交");
  };

  const adminQueue = overview?.adminQueue;

  return (
    <section>
      <section className="page-heading">
        <div>
          <p className="eyebrow">N3 平台治理</p>
          <h1>治理与信用中心</h1>
          <p>集中处理举报、申诉、退款、收藏关注和信用摘要，管理员账号可直接处理待办。</p>
        </div>
        <button className="secondary-button" disabled={busy} type="button" onClick={() => void load()}><RefreshCw size={17} /> 刷新</button>
      </section>

      {!overview ? <LoadingBlock /> : (
        <>
          <section className="governance-metrics" aria-label="信用摘要">
            <Metric title="信用等级" value={overview.credit.internalLevel} text={`${overview.credit.internalScore} 分`} />
            <Metric title="完成交易" value={overview.credit.fulfillmentCount} text="含待结算完成单" />
            <Metric title="好评记录" value={overview.credit.positiveReviewCount} text="4 星及以上" />
            <Metric title="有效处罚" value={overview.credit.negativeEventCount} text={overview.credit.publicTags.join(" / ")} />
          </section>

          <div className="governance-layout">
            <form className="form-panel" onSubmit={onReport}>
              <div className="panel-title"><h2><FileWarning size={17} /> 提交举报</h2></div>
              <div className="form-grid">
                <FormField label="对象类型">
                  <select value={reportForm.targetType} onChange={(event) => setReportForm({ ...reportForm, targetType: event.target.value })}>
                    <option value="GOODS">商品</option>
                    <option value="ORDER">订单</option>
                    <option value="USER">用户</option>
                  </select>
                </FormField>
                <FormField label="对象 ID">
                  <input required min="1" type="number" value={reportForm.targetId} onChange={(event) => setReportForm({ ...reportForm, targetId: event.target.value })} />
                </FormField>
              </div>
              <FormField label="举报原因">
                <select value={reportForm.reasonType} onChange={(event) => setReportForm({ ...reportForm, reasonType: event.target.value })}>
                  <option value="FRAUD">疑似欺诈</option>
                  <option value="FAKE_GOODS">商品不实</option>
                  <option value="HARASSMENT">骚扰沟通</option>
                  <option value="SAFETY">安全风险</option>
                </select>
              </FormField>
              <FormField label="说明">
                <textarea required rows={3} value={reportForm.description} onChange={(event) => setReportForm({ ...reportForm, description: event.target.value })} />
              </FormField>
              <label className="upload-zone compact-zone">
                <FileWarning size={19} />
                <span>上传举报证据</span>
                <input accept="image/jpeg,image/png,image/webp" type="file" onChange={(event) => void uploadEvidence(event.target.files?.[0], "REPORT_EVIDENCE")} />
              </label>
              {reportEvidenceIds.length > 0 && <p className="form-hint">已关联证据 #{reportEvidenceIds.join(", #")}</p>}
              <button className="primary-button full-width" disabled={busy} type="submit">提交举报</button>
            </form>

            <form className="form-panel" onSubmit={onAppeal}>
              <div className="panel-title"><h2><ShieldAlert size={17} /> 提交申诉</h2></div>
              <FormField label="关联举报 ID">
                <input required min="1" type="number" value={appealForm.reportId} onChange={(event) => setAppealForm({ ...appealForm, reportId: event.target.value })} />
              </FormField>
              <FormField label="申诉说明">
                <textarea required rows={3} value={appealForm.description} onChange={(event) => setAppealForm({ ...appealForm, description: event.target.value })} />
              </FormField>
              <label className="upload-zone compact-zone">
                <ShieldAlert size={19} />
                <span>上传申诉证据</span>
                <input accept="image/jpeg,image/png,image/webp" type="file" onChange={(event) => void uploadEvidence(event.target.files?.[0], "APPEAL_EVIDENCE")} />
              </label>
              {appealEvidenceIds.length > 0 && <p className="form-hint">已关联证据 #{appealEvidenceIds.join(", #")}</p>}
              <button className="primary-button full-width" disabled={busy} type="submit">提交申诉</button>
            </form>

            <form className="form-panel" onSubmit={onRefund}>
              <div className="panel-title"><h2><RotateCcw size={17} /> 申请退款</h2></div>
              <div className="form-grid">
                <FormField label="订单 ID"><input required min="1" type="number" value={refundForm.orderId} onChange={(event) => setRefundForm({ ...refundForm, orderId: event.target.value })} /></FormField>
                <FormField label="退款金额"><input required min="0.01" step="0.01" type="number" value={refundForm.amount} onChange={(event) => setRefundForm({ ...refundForm, amount: event.target.value })} /></FormField>
              </div>
              <FormField label="退款类型">
                <select value={refundForm.refundType} onChange={(event) => setRefundForm({ ...refundForm, refundType: event.target.value })}>
                  <option value="FULL">全额</option>
                  <option value="PARTIAL">部分</option>
                </select>
              </FormField>
              <FormField label="退款原因">
                <textarea required rows={3} value={refundForm.reason} onChange={(event) => setRefundForm({ ...refundForm, reason: event.target.value })} />
              </FormField>
              <button className="primary-button full-width" disabled={busy} type="submit">提交退款申请</button>
            </form>

            <section className="side-panel governance-actions">
              <div className="panel-title"><h2>收藏与关注</h2></div>
              <InlineAction icon={<Heart size={16} />} value={favoriteId} label="商品 ID" onChange={setFavoriteId} primary="收藏" secondary="取消" onPrimary={() => void run(() => addFavorite(Number(favoriteId)), "已收藏商品")} onSecondary={() => void run(() => removeFavorite(Number(favoriteId)), "已取消收藏")} />
              <InlineAction icon={<UserPlus size={16} />} value={followId} label="用户 ID" onChange={setFollowId} primary="关注" secondary="取消" onPrimary={() => void run(() => followUser(Number(followId)), "已关注用户")} onSecondary={() => void run(() => unfollowUser(Number(followId)), "已取消关注")} />
            </section>
          </div>

          <section className="governance-board">
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
            <section className="admin-n3">
              <div className="panel-title"><h2><AlertTriangle size={18} /> 管理员待办</h2><span className="badge neutral">N3 A/B/C</span></div>
              <div className="governance-board">
                <RecordColumn title="举报处理" empty="没有待处理举报">
                  {adminQueue.pendingReports.map((item) => (
                    <RecordRow key={item.id} title={`举报 #${item.id} · ${item.reporter.nickname}`} badge={reportStatusLabels[item.status] ?? item.status} text={item.description}>
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

function InlineAction(props: { icon: React.ReactNode; label: string; value: string; onChange: (value: string) => void; primary: string; secondary: string; onPrimary: () => void; onSecondary: () => void }) {
  return (
    <div className="inline-action">
      <label><span>{props.icon}{props.label}</span><input min="1" type="number" value={props.value} onChange={(event) => props.onChange(event.target.value)} /></label>
      <div className="button-row">
        <button className="secondary-button compact" type="button" onClick={props.onPrimary}>{props.primary}</button>
        <button className="text-button" type="button" onClick={props.onSecondary}>{props.secondary}</button>
      </div>
    </div>
  );
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
