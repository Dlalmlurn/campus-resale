// 文件功能：校园认证页（资料填写、材料上传、提交审核）。原内联于 App.tsx。
import { BookOpen, FileUp } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { getCurrentUser, getVerification, submitVerification, updateVerification, uploadFile } from "../api/m1";
import type { CampusVerification, CurrentUser, StoredFileSummary } from "../api/types";
import { FormField, PageHeading, StatusBadge } from "../components/ui";
import { factorLabel, formatBytes, messageOf, verificationStatusLabels, type Notify } from "../shared/app-shared";

export function VerificationPage(props: { currentUser: CurrentUser; onUserChange: (user: CurrentUser) => void; notify: Notify }) {
  const [verification, setVerification] = useState<CampusVerification | null>(null);
  const [form, setForm] = useState({ realName: "", studentNo: "", department: "", campusEmail: "", documentType: "STUDENT_CARD" });
  const [documentFiles, setDocumentFiles] = useState<StoredFileSummary[]>([]);
  const [documentFileIds, setDocumentFileIds] = useState<number[]>([]);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const next = await getVerification();
      setVerification(next);
      setForm({
        realName: next.realName ?? "",
        studentNo: next.studentNo ?? "",
        department: next.department ?? "",
        campusEmail: next.campusEmail ?? "",
        documentType: next.factors.find((factor) => ["STUDENT_CARD", "CAMPUS_CARD"].includes(factor.factorType))?.factorType ?? "STUDENT_CARD"
      });
      setDocumentFileIds([...new Set(next.factors.flatMap((factor) => factor.fileIds ?? []))]);
    } catch (error) {
      props.notify("error", messageOf(error));
    }
  }, [props.notify]);

  useEffect(() => { void load(); }, [load]);

  const upload = async (file?: File) => {
    if (!file) return;
    setBusy(true);
    try {
      const summary = await uploadFile(file, "CAMPUS_AUTH_MATERIAL");
      setDocumentFiles((current) => [...current, summary]);
      setDocumentFileIds((current) => [...new Set([...current, summary.id])]);
      props.notify("success", "认证材料已上传");
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  const save = async () => {
    setBusy(true);
    try {
      const next = await updateVerification({ ...form, documentFileIds });
      setVerification(next);
      props.notify("success", "认证资料已保存");
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  const submit = async () => {
    setBusy(true);
    try {
      const next = await submitVerification();
      setVerification(next);
      props.onUserChange(await getCurrentUser());
      props.notify("success", "认证资料已提交审核");
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section>
      <PageHeading eyebrow="校园身份" title="校园认证" text="完善资料并提交审核，认证通过后可以发布商品。" />
      <div className="two-column-layout">
        <form className="form-panel" onSubmit={(event) => { event.preventDefault(); void save(); }}>
          <div className="panel-title"><h2>认证资料</h2><StatusBadge value={verification?.status ?? "NONE"} labels={verificationStatusLabels} /></div>
          <FormField label="姓名"><input required value={form.realName} onChange={(event) => setForm({ ...form, realName: event.target.value })} /></FormField>
          <FormField label="学号"><input required value={form.studentNo} onChange={(event) => setForm({ ...form, studentNo: event.target.value })} /></FormField>
          <FormField label="院系"><input required value={form.department} onChange={(event) => setForm({ ...form, department: event.target.value })} /></FormField>
          <FormField label="校园邮箱"><input required type="email" value={form.campusEmail} onChange={(event) => setForm({ ...form, campusEmail: event.target.value })} /></FormField>
          <FormField label="证件类型">
            <select value={form.documentType} onChange={(event) => setForm({ ...form, documentType: event.target.value })}>
              <option value="STUDENT_CARD">学生证</option>
              <option value="CAMPUS_CARD">校园卡</option>
            </select>
          </FormField>
          <div className="button-row">
            <button className="secondary-button" disabled={busy} type="submit">保存资料</button>
            <button className="primary-button" disabled={busy || documentFileIds.length === 0} type="button" onClick={() => void submit()}>提交审核</button>
          </div>
        </form>
        <aside className="side-panel">
          <div className="panel-title"><h2>材料与进度</h2><strong>{verification?.score ?? 0} 分</strong></div>
          <label className="upload-zone">
            <FileUp size={22} />
            <span>上传学生证或校园卡图片</span>
            <input accept="image/jpeg,image/png,image/webp" type="file" onChange={(event) => void upload(event.target.files?.[0])} />
          </label>
          {documentFileIds.filter((id) => !documentFiles.some((file) => file.id === id)).map((id) => <div className="file-row" key={id}><BookOpen size={16} /><span>已关联认证材料</span><small>#{id}</small></div>)}
          {documentFiles.map((file) => <div className="file-row" key={file.id}><BookOpen size={16} /><span>{file.originalName}</span><small>{formatBytes(file.byteSize)}</small></div>)}
          <div className="factor-list">
            {(verification?.factors ?? []).map((factor) => <div className="row-item" key={factor.factorType}><span>{factorLabel(factor.factorType)}</span><strong>{factor.scoreValue} 分</strong></div>)}
          </div>
        </aside>
      </div>
    </section>
  );
}
