import { CheckCircle2, RefreshCw, RotateCw, XCircle } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import {
  advanceDueSettlements,
  advanceSettlement,
  decideAdminRefund,
  getAdminPayments,
  getAdminRefunds,
  getAdminSettlements
} from "../api/admin";
import type { PaymentSummary, RefundSummary, SettlementSummary } from "../api/types";

type Notify = (tone: "success" | "error", text: string) => void;

export function AdminFundsPage({ notify }: { notify: Notify }) {
  const [tab, setTab] = useState<"payments" | "refunds" | "settlements">("payments");
  const [payments, setPayments] = useState<PaymentSummary[]>([]);
  const [refunds, setRefunds] = useState<RefundSummary[]>([]);
  const [settlements, setSettlements] = useState<SettlementSummary[]>([]);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setBusy(true);
    try {
      const [paymentPage, refundPage, settlementPage] = await Promise.all([
        getAdminPayments(),
        getAdminRefunds(),
        getAdminSettlements()
      ]);
      setPayments(paymentPage.items);
      setRefunds(refundPage.items);
      setSettlements(settlementPage.items);
    } catch (error) {
      notify("error", error instanceof Error ? error.message : "资金数据加载失败");
    } finally {
      setBusy(false);
    }
  }, [notify]);

  useEffect(() => { void load(); }, [load]);

  const decide = async (id: number, decision: string) => {
    const note = window.prompt("填写处理说明", decision) ?? decision;
    setBusy(true);
    try {
      await decideAdminRefund(id, decision, note);
      notify("success", "退款状态已更新");
      await load();
    } catch (error) {
      notify("error", error instanceof Error ? error.message : "退款处理失败");
    } finally {
      setBusy(false);
    }
  };

  const advance = async (id: number) => {
    setBusy(true);
    try {
      await advanceSettlement(id);
      notify("success", "结算已推进");
      await load();
    } catch (error) {
      notify("error", error instanceof Error ? error.message : "结算推进失败");
    } finally {
      setBusy(false);
    }
  };

  const advanceDue = async () => {
    setBusy(true);
    try {
      const result = await advanceDueSettlements();
      notify("success", `已推进 ${result.length} 笔到期结算`);
      await load();
    } catch (error) {
      notify("error", error instanceof Error ? error.message : "到期结算推进失败");
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="funds-page">
      <div className="dashboard-header">
        <div>
          <p className="eyebrow">资金链路</p>
          <h2>支付 · 退款 · 结算</h2>
        </div>
        <div className="button-row">
          <button className="secondary-button compact" disabled={busy} type="button" onClick={() => void advanceDue()}><RotateCw size={15} /> 推进到期结算</button>
          <button className="icon-button subtle" disabled={busy} type="button" onClick={() => void load()} title="刷新"><RefreshCw size={17} /></button>
        </div>
      </div>
      <div className="segmented-control admin-tabs">
        <button className={tab === "payments" ? "active" : ""} type="button" onClick={() => setTab("payments")}>支付</button>
        <button className={tab === "refunds" ? "active" : ""} type="button" onClick={() => setTab("refunds")}>退款</button>
        <button className={tab === "settlements" ? "active" : ""} type="button" onClick={() => setTab("settlements")}>结算</button>
      </div>
      {tab === "payments" && (
        <FundsTable headings={["支付单", "订单", "金额", "状态", "渠道", "支付时间"]}>
          {payments.map((item) => <tr key={item.id}><td>{item.paymentNo}</td><td>#{item.orderId}</td><td>¥{item.amount}</td><td>{paymentStatusLabel(item.status)}</td><td>{item.provider}</td><td>{formatDate(item.paidAt)}</td></tr>)}
        </FundsTable>
      )}
      {tab === "refunds" && (
        <FundsTable headings={["退款单", "订单", "金额", "状态", "证据", "操作"]}>
          {refunds.map((item) => (
            <tr key={item.id}>
              <td>{item.refundNo}</td>
              <td>#{item.orderId}</td>
              <td>¥{item.amount}</td>
              <td>{refundStatusLabel(item.status)}</td>
              <td>{item.evidenceFileIds.length ? item.evidenceFileIds.map((id) => <a href={`/api/files/${id}/content?reason=refund-evidence`} target="_blank" rel="noreferrer" key={id}>#{id}</a>) : "无"}</td>
              <td>
                {["PENDING", "PROCESSING"].includes(item.status) ? (
                  <div className="table-actions">
                    {item.status === "PENDING" && <button className="icon-button subtle" title="通过审核" disabled={busy} type="button" onClick={() => void decide(item.id, "APPROVE")}><CheckCircle2 size={16} /></button>}
                    <button className="icon-button subtle" title="退款成功" disabled={busy} type="button" onClick={() => void decide(item.id, "REFUND_SUCCESS")}><CheckCircle2 size={16} /></button>
                    <button className="icon-button subtle" title="退款失败" disabled={busy} type="button" onClick={() => void decide(item.id, "REFUND_FAILED")}><XCircle size={16} /></button>
                  </div>
                ) : "已处理"}
              </td>
            </tr>
          ))}
        </FundsTable>
      )}
      {tab === "settlements" && (
        <FundsTable headings={["结算单", "订单", "金额", "状态", "冻结结束", "操作"]}>
          {settlements.map((item) => (
            <tr key={item.id}>
              <td>{item.settlementNo}</td>
              <td>#{item.orderId}</td>
              <td>¥{item.settlementAmount}</td>
              <td>{settlementStatusLabel(item.status)}</td>
              <td>{formatDate(item.freezeEndsAt)}</td>
              <td>{["PENDING", "FAILED"].includes(item.status) ? <button className="secondary-button compact" disabled={busy} type="button" onClick={() => void advance(item.id)}>推进</button> : "无"}</td>
            </tr>
          ))}
        </FundsTable>
      )}
    </section>
  );
}

function FundsTable(props: { headings: string[]; children: React.ReactNode }) {
  return (
    <div className="log-table-wrap">
      <table className="log-table">
        <thead><tr>{props.headings.map((heading) => <th key={heading}>{heading}</th>)}</tr></thead>
        <tbody>{props.children}</tbody>
      </table>
    </div>
  );
}

function paymentStatusLabel(status: string) {
  return ({ PENDING: "待支付", PROCESSING: "处理中", ESCROWED: "已托管", FAILED: "支付失败", CLOSED: "已关闭" } as Record<string, string>)[status] ?? status;
}

function refundStatusLabel(status: string) {
  return ({ PENDING: "待审核", PROCESSING: "退款处理中", REFUNDED: "已退款", FAILED: "退款失败", CLOSED: "已关闭" } as Record<string, string>)[status] ?? status;
}

function settlementStatusLabel(status: string) {
  return ({ PENDING: "待结算", PROCESSING: "结算处理中", SETTLED: "已结算", FAILED: "结算失败", CLOSED: "已关闭" } as Record<string, string>)[status] ?? status;
}

function formatDate(value?: string | null) {
  return value ? new Date(value).toLocaleString("zh-CN") : "未发生";
}
