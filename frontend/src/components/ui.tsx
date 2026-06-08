// 文件功能：App 各页面共享的小型展示组件。原本内联在 App.tsx，拆分页面后集中到此处复用。
import { Check, ClipboardCheck, RefreshCw, X } from "lucide-react";
import type { CurrentUser, GoodsSummary } from "../api/types";

// 没有实拍图的商品统一回退到这张占位图（提交进 public/，随 git 同步，全员一致、无外链）。
const GOODS_PLACEHOLDER = "/goods-placeholder.svg";

export function NavButton(props: { active: boolean; icon: React.ReactNode; label: string; onClick: () => void }) {
  return <button className={`nav-button ${props.active ? "active" : ""}`} type="button" onClick={props.onClick}>{props.icon}<span>{props.label}</span></button>;
}

export function Avatar({ user, large = false }: { user: CurrentUser; large?: boolean }) {
  const initial = user.nickname.trim().slice(0, 1).toUpperCase() || user.username.slice(0, 1).toUpperCase();
  return user.avatarUrl
    ? <img className={`avatar ${large ? "large" : ""}`} src={user.avatarUrl} alt={`${user.nickname}头像`} />
    : <span className={`avatar fallback ${large ? "large" : ""}`}>{initial}</span>;
}

export function PageHeading(props: { eyebrow: string; title: string; text: string }) {
  return <section className="page-heading"><div><p className="eyebrow">{props.eyebrow}</p><h1>{props.title}</h1><p>{props.text}</p></div></section>;
}

export function FormField(props: { label: string; children: React.ReactNode }) {
  return <label className="form-field"><span>{props.label}</span>{props.children}</label>;
}

export function GoodsImage({ item, large = false }: { item: GoodsSummary; large?: boolean }) {
  const src = item.primaryImage ? item.primaryImage.url : GOODS_PLACEHOLDER;
  return <img className={`goods-image ${large ? "large" : ""}`} src={src} alt={item.title} />;
}

export function StatusBadge({ value, labels }: { value: string; labels: Record<string, string> }) {
  const tone = ["APPROVED", "ON_SALE"].includes(value) ? "success" : ["REJECTED", "INVALID"].includes(value) ? "danger" : "warning";
  return <span className={`badge ${tone}`}>{labels[value] ?? value}</span>;
}

export function ReviewActions(props: { disabled: boolean; onApprove: () => void; onReject: () => void }) {
  return <div className="review-actions"><button className="approve-button" disabled={props.disabled} aria-label="通过" title="通过" type="button" onClick={props.onApprove}><Check size={17} /></button><button className="reject-button" disabled={props.disabled} aria-label="驳回" title="驳回" type="button" onClick={props.onReject}><X size={17} /></button></div>;
}

export function MetricTile(props: { label: string; value: string | number }) {
  return <div className="profile-stat"><span>{props.label}</span><strong>{props.value}</strong></div>;
}

export function ProfileColumn(props: { title: string; empty: string; children: React.ReactNode }) {
  const items = Array.isArray(props.children) ? props.children.filter(Boolean) : props.children ? [props.children] : [];
  return <section className="profile-column"><h2>{props.title}</h2>{items.length === 0 ? <p className="empty-line">{props.empty}</p> : props.children}</section>;
}

export function LoadingBlock() {
  return (
    <div className="state-block loading-block">
      <RefreshCw className="spin" size={24} />
      <strong>加载中</strong>
      <div className="loading-skeleton" aria-hidden="true"><span /><span /><span /></div>
    </div>
  );
}

export function EmptyBlock({ title }: { title: string }) {
  return <div className="state-block"><ClipboardCheck size={24} /><strong>{title}</strong></div>;
}
