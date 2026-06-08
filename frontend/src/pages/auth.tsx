// 文件功能：登录 / 注册页。原内联于 App.tsx。
import { BadgeCheck } from "lucide-react";
import { FormEvent, useState } from "react";
import { login, register } from "../api/m1";
import type { CurrentUser } from "../api/types";
import { FormField } from "../components/ui";
import { messageOf, type Notify, type Route } from "../shared/app-shared";

export function AuthPage(props: { currentUser: CurrentUser | null; onAuthenticated: (user: CurrentUser) => void; navigate: (route: Route) => void; notify: Notify }) {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({ username: "", password: "", nickname: "", personalEmail: "" });

  if (props.currentUser) {
    return (
      <section className="center-panel">
        <BadgeCheck size={30} />
        <h1>已登录</h1>
        <p>{props.currentUser.nickname}，欢迎回来。</p>
        <button className="primary-button" type="button" onClick={() => props.navigate({ name: "market" })}>进入商品列表</button>
      </section>
    );
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    try {
      const user = mode === "login"
        ? await login(form.username, form.password)
        : await register(form.username, form.password, form.nickname, form.personalEmail);
      props.onAuthenticated(user);
      props.notify("success", mode === "login" ? "登录成功" : "注册成功");
      props.navigate({ name: "market" });
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="auth-layout">
      <div className="auth-intro">
        <p className="eyebrow">校内可信交易</p>
        <h1>{mode === "login" ? "登录账号" : "注册账号"}</h1>
        <p>登录后可以提交校园认证、发布商品并查看审核进度。</p>
      </div>
      <form className="form-panel" onSubmit={(event) => void submit(event)}>
        <div className="segmented-control">
          <button className={mode === "login" ? "active" : ""} type="button" onClick={() => setMode("login")}>登录</button>
          <button className={mode === "register" ? "active" : ""} type="button" onClick={() => setMode("register")}>注册</button>
        </div>
        <FormField label="用户名"><input required value={form.username} onChange={(event) => setForm({ ...form, username: event.target.value })} placeholder="3-20 位字母、数字或下划线" /></FormField>
        <FormField label="密码"><input required minLength={8} type="password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} placeholder="至少 8 位" /></FormField>
        {mode === "register" && <>
          <FormField label="昵称"><input required value={form.nickname} onChange={(event) => setForm({ ...form, nickname: event.target.value })} placeholder="公开展示名称" /></FormField>
          <FormField label="个人邮箱"><input type="email" value={form.personalEmail} onChange={(event) => setForm({ ...form, personalEmail: event.target.value })} placeholder="可选" /></FormField>
        </>}
        <button className="primary-button full-width" disabled={busy} type="submit">{busy ? "处理中..." : mode === "login" ? "登录" : "注册并登录"}</button>
      </form>
    </section>
  );
}
