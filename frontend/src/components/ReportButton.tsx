// 文件功能：提供统一举报按钮，商品、订单和消息页面共用同一套提交逻辑。
import { Flag } from "lucide-react";
import { submitReport } from "../api/governance";
import type { CurrentUser } from "../api/types";

type Notify = (tone: "success" | "error", text: string) => void;

/**
 * 统一举报入口；通过 prompt 收集简短说明，避免各页面重复维护表单状态。
 */
export function ReportButton(props: {
  currentUser: CurrentUser | null;
  targetType: "GOODS" | "ORDER" | "USER";
  targetId: number;
  notify: Notify;
}) {
  const submit = async () => {
    if (!props.currentUser) {
      props.notify("error", "请先登录后继续");
      return;
    }
    const description = window.prompt("请简要说明举报原因", "");
    if (!description?.trim()) {
      return;
    }
    try {
      await submitReport({
        targetType: props.targetType,
        targetId: props.targetId,
        reasonType: "SAFETY",
        description: description.trim(),
        evidenceFileIds: []
      });
      props.notify("success", "举报已提交");
    } catch (error) {
      props.notify("error", error instanceof Error ? error.message : "举报提交失败，请稍后重试");
    }
  };

  return (
    <button className="secondary-button compact danger-action" type="button" onClick={() => void submit()}>
      <Flag size={15} /> 举报
    </button>
  );
}
