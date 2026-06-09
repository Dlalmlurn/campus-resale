// 文件功能：超级管理员账号管理页（查询、启用/锁定/禁用、授予/撤销角色）。对应后端 AdminUserController。
import { History, RefreshCw, ShieldCheck, UserCog, X } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { assignAdminUserRole, getAdminUsers, removeAdminUserRole, updateAdminUserStatus, type AdminUser } from "../api/admin-users";
import { EmptyBlock, LoadingBlock } from "../components/ui";
import { formatDate, messageOf, type Notify } from "../shared/app-shared";

const accountStatusLabels: Record<string, string> = {
  ACTIVE: "正常",
  LOCKED: "已锁定",
  DISABLED: "已禁用"
};

const statusActions: Array<{ value: string; label: string }> = [
  { value: "ACTIVE", label: "启用" },
  { value: "LOCKED", label: "锁定" },
  { value: "DISABLED", label: "禁用" }
];

const assignableRoles: Array<{ value: string; label: string }> = [
  { value: "CONTENT_ADMIN", label: "内容管理员" },
  { value: "SUPER_ADMIN", label: "超级管理员" },
  { value: "VERIFIED_STUDENT", label: "认证学生" },
  { value: "REGISTERED_USER", label: "注册用户" }
];

const roleLabels: Record<string, string> = Object.fromEntries(assignableRoles.map((role) => [role.value, role.label]));

export function AdminUsersPage(props: { notify: Notify; onTrace?: (userId: number) => void }) {
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [keyword, setKeyword] = useState("");
  const [accountStatus, setAccountStatus] = useState("");
  const [roleCode, setRoleCode] = useState("");
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await getAdminUsers({ keyword, accountStatus, roleCode, pageSize: 50 });
      setUsers(page.items);
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setLoading(false);
    }
  }, [accountStatus, keyword, props.notify, roleCode]);

  useEffect(() => { void load(); }, [load]);

  const replaceUser = (updated: AdminUser) => setUsers((current) => current.map((item) => (item.id === updated.id ? updated : item)));

  const changeStatus = async (user: AdminUser, status: string) => {
    if (status === user.accountStatus) return;
    const reason = window.prompt(`将 ${user.nickname} 的账号状态调整为「${accountStatusLabels[status]}」的原因（可选）`, "") ?? undefined;
    setBusyId(user.id);
    try {
      replaceUser(await updateAdminUserStatus(user.id, status, reason));
      props.notify("success", `已将 ${user.nickname} 调整为${accountStatusLabels[status]}`);
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusyId(null);
    }
  };

  const addRole = async (user: AdminUser, role: string) => {
    if (!role || user.roles.includes(role)) return;
    setBusyId(user.id);
    try {
      replaceUser(await assignAdminUserRole(user.id, role));
      props.notify("success", `已为 ${user.nickname} 授予${roleLabels[role] ?? role}`);
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusyId(null);
    }
  };

  const dropRole = async (user: AdminUser, role: string) => {
    setBusyId(user.id);
    try {
      replaceUser(await removeAdminUserRole(user.id, role));
      props.notify("success", `已撤销 ${user.nickname} 的${roleLabels[role] ?? role}`);
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <section className="admin-users">
      <div className="order-toolbar">
        <div>
          <p className="eyebrow">超管专属</p>
          <h2><UserCog size={18} /> 账号管理</h2>
        </div>
        <button className="icon-button subtle" type="button" aria-label="刷新账号列表" onClick={() => void load()}><RefreshCw size={17} /></button>
      </div>
      <div className="log-filter-bar">
        <label className="search-field">
          <input value={keyword} onChange={(event) => setKeyword(event.target.value)} onKeyDown={(event) => event.key === "Enter" && void load()} placeholder="用户名 / 昵称 / 邮箱" />
        </label>
        <select aria-label="账号状态筛选" value={accountStatus} onChange={(event) => setAccountStatus(event.target.value)}>
          <option value="">全部状态</option>
          {statusActions.map((item) => <option value={item.value} key={item.value}>{accountStatusLabels[item.value]}</option>)}
        </select>
        <select aria-label="角色筛选" value={roleCode} onChange={(event) => setRoleCode(event.target.value)}>
          <option value="">全部角色</option>
          {assignableRoles.map((role) => <option value={role.value} key={role.value}>{role.label}</option>)}
        </select>
        <button className="primary-button compact" type="button" onClick={() => void load()}>查询</button>
      </div>

      {loading ? <LoadingBlock /> : users.length === 0 ? <EmptyBlock title="没有匹配的账号" /> : (
        <div className="admin-user-list">
          {users.map((user) => (
            <article className="admin-user-row" key={user.id}>
              <div className="admin-user-identity">
                <strong>{user.nickname} <small>@{user.username} · #{user.id}</small></strong>
                <small>{user.personalEmail ?? "未填写邮箱"} · 注册于 {formatDate(user.createdAt)}</small>
                <div className="badge-row">
                  <span className={`badge ${user.accountStatus === "ACTIVE" ? "success" : user.accountStatus === "DISABLED" ? "danger" : "warning"}`}>{accountStatusLabels[user.accountStatus] ?? user.accountStatus}</span>
                  {user.roles.map((role) => (
                    <span className="badge neutral role-chip" key={role}>
                      {role === "SUPER_ADMIN" && <ShieldCheck size={13} />} {roleLabels[role] ?? role}
                      <button className="role-chip-remove" type="button" aria-label={`撤销${roleLabels[role] ?? role}`} disabled={busyId === user.id} onClick={() => void dropRole(user, role)}><X size={12} /></button>
                    </span>
                  ))}
                </div>
              </div>
              <div className="admin-user-actions">
                <div className="button-row">
                  {statusActions.map((action) => (
                    <button
                      className={`secondary-button compact ${user.accountStatus === action.value ? "active" : ""}`}
                      type="button"
                      key={action.value}
                      disabled={busyId === user.id || user.accountStatus === action.value}
                      onClick={() => void changeStatus(user, action.value)}
                    >
                      {action.label}
                    </button>
                  ))}
                </div>
                <label className="select-field compact">
                  <select aria-label={`为 ${user.nickname} 授予角色`} value="" disabled={busyId === user.id} onChange={(event) => void addRole(user, event.target.value)}>
                    <option value="">+ 授予角色</option>
                    {assignableRoles.filter((role) => !user.roles.includes(role.value)).map((role) => <option value={role.value} key={role.value}>{role.label}</option>)}
                  </select>
                </label>
                {props.onTrace && (
                  <button className="text-button" type="button" onClick={() => props.onTrace?.(user.id)}><History size={14} /> 治理追踪</button>
                )}
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
