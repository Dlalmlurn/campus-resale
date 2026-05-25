import {
  BadgeCheck,
  Boxes,
  Database,
  RefreshCw,
  ShieldCheck,
  Store,
  TerminalSquare
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { getHealth, type HealthResponse, type ModuleDescriptor } from "./api/health";

const workspaces = [
  { name: "学生前台", path: "/student", icon: Store },
  { name: "卖家工作台", path: "/seller", icon: Boxes },
  { name: "管理后台", path: "/admin", icon: ShieldCheck }
];

const foundationItems = [
  "Spring Boot",
  "React",
  "PostgreSQL",
  "Flyway",
  "MinIO",
  "Docker Compose"
];

export function App() {
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const nextHealth = await getHealth();
      setHealth(nextHealth);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "API health check failed");
      setHealth(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const moduleGroups = useMemo(() => groupModules(health?.modules ?? []), [health]);

  return (
    <main className="app-shell">
      <section className="status-band" aria-labelledby="app-title">
        <div>
          <p className="eyebrow">Campus Resale</p>
          <h1 id="app-title">校园二手交易平台</h1>
        </div>
        <div className="status-panel" aria-live="polite">
          <div className={health?.status === "UP" ? "status-pill up" : "status-pill down"}>
            <BadgeCheck size={18} aria-hidden="true" />
            <span>{loading ? "Checking" : health?.status ?? "Offline"}</span>
          </div>
          <button className="icon-button" type="button" onClick={refresh} aria-label="刷新服务状态">
            <RefreshCw size={18} aria-hidden="true" />
          </button>
        </div>
      </section>

      <section className="workspace-grid" aria-label="产品入口">
        {workspaces.map((workspace) => {
          const Icon = workspace.icon;
          return (
            <a className="workspace-card" href={workspace.path} key={workspace.path}>
              <Icon size={22} aria-hidden="true" />
              <span>{workspace.name}</span>
            </a>
          );
        })}
      </section>

      <section className="foundation-layout">
        <div className="foundation-main">
          <div className="section-heading">
            <TerminalSquare size={20} aria-hidden="true" />
            <h2>M0 工程底座</h2>
          </div>
          <div className="foundation-list">
            {foundationItems.map((item) => (
              <span key={item}>{item}</span>
            ))}
          </div>
        </div>

        <aside className="api-card" aria-label="后端状态">
          <div className="section-heading">
            <Database size={20} aria-hidden="true" />
            <h2>API</h2>
          </div>
          {error ? (
            <p className="api-error">{error}</p>
          ) : (
            <dl>
              <div>
                <dt>Service</dt>
                <dd>{health?.service ?? "campus-resale-api"}</dd>
              </div>
              <div>
                <dt>Checked</dt>
                <dd>{health?.checkedAt ? new Date(health.checkedAt).toLocaleString() : "Pending"}</dd>
              </div>
            </dl>
          )}
        </aside>
      </section>

      <section className="module-board" aria-label="模块地图">
        {moduleGroups.map((group) => (
          <div className="module-row" key={group.status}>
            <span className="module-status">{group.status}</span>
            <div className="module-cells">
              {group.modules.map((module) => (
                <article className="module-cell" key={module.code}>
                  <strong>{module.code}</strong>
                  <span>{module.name}</span>
                </article>
              ))}
            </div>
          </div>
        ))}
      </section>
    </main>
  );
}

function groupModules(modules: ModuleDescriptor[]) {
  const fallback = modules.length > 0 ? modules : [];
  const groups = new Map<string, ModuleDescriptor[]>();
  fallback.forEach((module) => {
    groups.set(module.status, [...(groups.get(module.status) ?? []), module]);
  });

  return Array.from(groups.entries()).map(([status, groupedModules]) => ({
    status,
    modules: groupedModules
  }));
}
