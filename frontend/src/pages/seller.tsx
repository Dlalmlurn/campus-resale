// 文件功能：卖家工作台（新建草稿、AI 发布辅助、我的全部发布列表）。原内联于 App.tsx。
import { ChevronRight, FileUp, RefreshCw } from "lucide-react";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { assistGoods, type GoodsAssistResponse } from "../api/intelligence";
import { createGoodsDraft, getMyGoods, submitGoods, uploadFile } from "../api/m1";
import type { GoodsSummary, GoodsUpsertRequest, StoredFileSummary } from "../api/types";
import { EmptyBlock, FormField, PageHeading, StatusBadge } from "../components/ui";
import { auditStatusLabels, clearSellerDraft, conditionLabels, emptyGoodsDraft, goodsStatusLabels, loadSellerDraft, messageOf, saveSellerDraft, sellerStatusFilters, type Catalog, type Notify } from "../shared/app-shared";

export function SellerPage(props: { catalog: Catalog; notify: Notify }) {
  const [items, setItems] = useState<GoodsSummary[]>([]);
  const [statusFilter, setStatusFilter] = useState("");
  const [files, setFiles] = useState<StoredFileSummary[]>([]);
  const [busy, setBusy] = useState(false);
  const [aiBusy, setAiBusy] = useState(false);
  const [aiAdvice, setAiAdvice] = useState<GoodsAssistResponse | null>(null);
  const [form, setForm] = useState<GoodsUpsertRequest>(() => loadSellerDraft());

  const load = useCallback(async () => {
    try {
      setItems((await getMyGoods({ status: statusFilter, pageSize: 50 })).items);
    } catch (error) {
      props.notify("error", messageOf(error));
    }
  }, [props.notify, statusFilter]);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    saveSellerDraft(form);
  }, [form]);

  const upload = async (file?: File) => {
    if (!file) return;
    setBusy(true);
    try {
      const summary = await uploadFile(file, "GOODS_IMAGE");
      setFiles((current) => [...current, summary]);
      setForm((current) => ({ ...current, imageFileIds: [...current.imageFileIds, summary.id] }));
      props.notify("success", "商品图片已上传");
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  const create = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    try {
      const created = await createGoodsDraft(form);
      props.notify("success", "商品草稿已创建");
      setItems((current) => [created, ...current]);
      setFiles([]);
      clearSellerDraft();
      setForm(emptyGoodsDraft());
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  const submit = async (id: number) => {
    setBusy(true);
    try {
      await submitGoods(id);
      props.notify("success", "商品已提交审核");
      await load();
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  const requestAiAdvice = async () => {
    setAiBusy(true);
    try {
      const advice = await assistGoods({ title: form.title, description: form.description, price: form.listPrice });
      setAiAdvice(advice);
      props.notify("success", "AI 发布建议已生成");
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setAiBusy(false);
    }
  };

  const applyAiAdvice = () => {
    if (!aiAdvice) return;
    const matchedCategory = props.catalog.categories.find((item) => item.code === aiAdvice.suggestedCategoryCode);
    const matchedTagIds = props.catalog.tags
      .filter((tag) => aiAdvice.suggestedTags.some((name) => tag.name.includes(name) || name.includes(tag.name)))
      .map((tag) => tag.id);
    setForm((current) => ({
      ...current,
      title: aiAdvice.optimizedTitle,
      description: aiAdvice.optimizedDescription,
      categoryId: matchedCategory?.id ?? current.categoryId,
      tagIds: Array.from(new Set([...current.tagIds, ...matchedTagIds]))
    }));
  };

  return (
    <section>
      <PageHeading eyebrow="卖家工作台" title="发布商品" text="上传图片并创建草稿，确认后提交管理员审核。" />
      <div className="seller-layout">
        <form className="form-panel" onSubmit={(event) => void create(event)}>
          <div className="panel-title"><h2>新建商品草稿</h2></div>
          <FormField label="商品标题"><input required minLength={5} value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} /></FormField>
          <FormField label="商品描述"><textarea required minLength={10} rows={4} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></FormField>
          <div className="form-grid">
            <FormField label="分类"><select required value={form.categoryId ?? ""} onChange={(event) => setForm({ ...form, categoryId: Number(event.target.value) || null })}><option value="">请选择</option>{props.catalog.categories.map((item) => <option value={item.id} key={item.id}>{item.name}</option>)}</select></FormField>
            <FormField label="成色"><select value={form.conditionLevel} onChange={(event) => setForm({ ...form, conditionLevel: event.target.value })}>{Object.entries(conditionLabels).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></FormField>
            <FormField label="价格"><input required min="0.01" step="0.01" type="number" value={form.listPrice} onChange={(event) => setForm({ ...form, listPrice: event.target.value })} /></FormField>
            <FormField label="面交地点"><select value={form.tradePlaceId ?? ""} onChange={(event) => setForm({ ...form, tradePlaceId: Number(event.target.value) || null })}><option value="">暂不指定</option>{props.catalog.places.map((item) => <option value={item.id} key={item.id}>{item.campus} · {item.name}</option>)}</select></FormField>
          </div>
          <FormField label="可交易时间"><input value={form.availableTimeText} onChange={(event) => setForm({ ...form, availableTimeText: event.target.value })} placeholder="例如：工作日 18:00 后" /></FormField>
          <fieldset className="tag-fieldset">
            <legend>标签</legend>
            <div className="checkbox-grid">{props.catalog.tags.map((tag) => <label key={tag.id}><input type="checkbox" checked={form.tagIds.includes(tag.id)} onChange={(event) => setForm({ ...form, tagIds: event.target.checked ? [...form.tagIds, tag.id] : form.tagIds.filter((id) => id !== tag.id) })} /> {tag.name}</label>)}</div>
          </fieldset>
          <label className="upload-zone compact-zone">
            <FileUp size={20} />
            <span>添加商品图片</span>
            <input accept="image/jpeg,image/png,image/webp" type="file" onChange={(event) => void upload(event.target.files?.[0])} />
          </label>
          {files.length > 0 && <p className="form-hint">已上传 {files.length} 张图片</p>}
          <button className="primary-button full-width" disabled={busy} type="submit">创建草稿</button>
        </form>
        <aside className="side-panel seller-assist-panel">
          <div className="panel-title"><h2>AI 发布辅助</h2></div>
          <p className="form-hint">根据当前标题和描述生成分类、标签、风险和文案建议。</p>
          <button className="secondary-button full-width" disabled={aiBusy || busy} type="button" onClick={() => void requestAiAdvice()}>
            {aiBusy ? "生成中" : "生成优化建议"}
          </button>
          {aiAdvice && (
            <div className="ai-advice">
              <div><span>优化标题</span><strong>{aiAdvice.optimizedTitle}</strong></div>
              <p>{aiAdvice.optimizedDescription}</p>
              <div className="badge-row">
                <span className="badge neutral">{aiAdvice.suggestedCategoryCode}</span>
                <span className={`badge ${aiAdvice.riskLevel === "HIGH" ? "danger" : aiAdvice.riskLevel === "MEDIUM" ? "warning" : "success"}`}>风险 {aiAdvice.riskLevel}</span>
                <span className="badge neutral">{aiAdvice.assistSource === "LLM" ? "AI 模型生成" : "规则引擎"}</span>
              </div>
              <p className="recommendation-reason">推荐理由：{aiAdvice.recommendationReason}</p>
              <p className="form-hint">{aiAdvice.riskReasons.join(" / ")}</p>
              <p className="form-hint">{aiAdvice.auditReminder}</p>
              <button className="text-button" type="button" onClick={applyAiAdvice}>应用标题和描述</button>
            </div>
          )}
        </aside>
        <aside className="side-panel">
          <div className="panel-title"><h2>我的全部发布</h2><button className="icon-button subtle" aria-label="刷新商品" type="button" onClick={() => void load()}><RefreshCw size={17} /></button></div>
          <div className="seller-filter-tabs" aria-label="我的发布商品状态筛选">
            {sellerStatusFilters.map((filter) => (
              <button className={statusFilter === filter.value ? "active" : ""} type="button" key={filter.value || "ALL"} onClick={() => setStatusFilter(filter.value)}>
                {filter.label}
              </button>
            ))}
          </div>
          {items.length === 0 ? <EmptyBlock title="还没有发布记录" /> : items.map((item) => <div className="seller-item" key={item.id}>
            <div><strong>{item.title}</strong><small>#{item.id} · ¥{item.listPrice}</small></div>
            <div className="badge-row"><StatusBadge value={item.status} labels={goodsStatusLabels} /><StatusBadge value={item.auditStatus} labels={auditStatusLabels} /></div>
            {item.status === "DRAFT" && <button className="text-button" disabled={busy} type="button" onClick={() => void submit(item.id)}>提交审核 <ChevronRight size={16} /></button>}
          </div>)}
        </aside>
      </div>
    </section>
  );
}
