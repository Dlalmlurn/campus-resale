// 文件功能：应用外壳 + 路由分发。各页面已拆分到 pages/*，本文件只保留头部导航、全局状态与路由切换。
import {
  BadgeCheck,
  Bell,
  ClipboardList,
  Home,
  LogIn,
  LogOut,
  MessageSquareText,
  PackagePlus,
  ShieldAlert,
  ShieldCheck,
  Store,
  UserRound
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { ApiError } from "./api/client";
import { getCatalog, getCurrentUser, getPublicGoods, login, logout } from "./api/m1";
import type { CurrentUser, GoodsSummary } from "./api/types";
import { Avatar, NavButton } from "./components/ui";
import {
  emptyCatalog,
  isAdmin,
  isSuperAdmin,
  messageOf,
  parseRoute,
  routeHash,
  verificationStatusLabels,
  type Catalog,
  type Notify,
  type Route
} from "./shared/app-shared";
import { AdminPage } from "./pages/admin";
import { AuthPage } from "./pages/auth";
import { ConversationDetailPage, ConversationsPage } from "./pages/conversations";
import { GoodsDetailPage } from "./pages/goods-detail";
import { GovernancePage } from "./pages/governance";
import { MarketPage } from "./pages/market";
import { NotificationsPage } from "./pages/notifications";
import { OrderDetailPage, OrdersPage } from "./pages/orders";
import { ProfilePage } from "./pages/profile";
import { SellerPage } from "./pages/seller";
import { VerificationPage } from "./pages/verification";

const showDemoSwitcher = import.meta.env.DEV && import.meta.env.VITE_ENABLE_DEMO_SWITCHER === "true";

export function App() {
  const [route, setRoute] = useState<Route>(() => parseRoute());
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [authChecked, setAuthChecked] = useState(false);
  const [catalog, setCatalog] = useState<Catalog>(emptyCatalog);
  const [publicGoods, setPublicGoods] = useState<GoodsSummary[]>([]);
  const [goodsTotal, setGoodsTotal] = useState(0);
  const [query, setQuery] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [minPrice, setMinPrice] = useState("");
  const [maxPrice, setMaxPrice] = useState("");
  const [conditionLevel, setConditionLevel] = useState("");
  const [placeId, setPlaceId] = useState("");
  const [marketSort, setMarketSort] = useState("RECOMMENDED");
  const [loadingGoods, setLoadingGoods] = useState(true);
  const [notice, setNotice] = useState<{ tone: "success" | "error"; text: string } | null>(null);

  const navigate = useCallback((next: Route) => {
    const hash = routeHash(next);
    if (window.location.hash !== hash) {
      window.location.hash = hash;
    } else {
      setRoute(next);
    }
  }, []);

  const notify = useCallback<Notify>((tone, text) => {
    setNotice({ tone, text });
    window.setTimeout(() => setNotice(null), 4200);
  }, []);

  const refreshGoods = useCallback(async () => {
    setLoadingGoods(true);
    try {
      const response = await getPublicGoods({
        keyword: query,
        categoryId,
        minPrice,
        maxPrice,
        conditionLevel,
        placeId,
        sort: marketSort
      });
      setPublicGoods(response.items);
      setGoodsTotal(response.total);
    } catch (error) {
      notify("error", messageOf(error));
    } finally {
      setLoadingGoods(false);
    }
  }, [categoryId, conditionLevel, marketSort, maxPrice, minPrice, notify, placeId, query]);

  useEffect(() => {
    const onHashChange = () => setRoute(parseRoute());
    window.addEventListener("hashchange", onHashChange);
    if (!window.location.hash) navigate({ name: "market" });
    return () => window.removeEventListener("hashchange", onHashChange);
  }, [navigate]);

  useEffect(() => {
    void getCurrentUser()
      .then(setCurrentUser)
      .catch((error) => {
        if (!(error instanceof ApiError) || error.code !== "AUTH_REQUIRED") {
          notify("error", messageOf(error));
        }
      })
      .finally(() => setAuthChecked(true));
    void getCatalog()
      .then(setCatalog)
      .catch((error) => notify("error", messageOf(error)));
  }, [notify]);

  useEffect(() => {
    void refreshGoods();
  }, [refreshGoods]);

  useEffect(() => {
    if ((route.name === "orders" || route.name === "order" || route.name === "conversations" || route.name === "conversation" || route.name === "profile" || route.name === "notifications" || route.name === "governance") && authChecked && currentUser === null) {
      notify("error", "请先登录后继续");
      navigate({ name: "auth" });
      return;
    }
    if (route.name === "seller" && currentUser && !currentUser.canTrade) {
      notify("error", "完成校园认证后才能发布商品");
      navigate({ name: "verification" });
    }
  }, [authChecked, currentUser, navigate, notify, route.name]);

  const guardedNavigate = (next: Route) => {
    if (!currentUser && (next.name === "verification" || next.name === "seller" || next.name === "admin" || next.name === "orders" || next.name === "order" || next.name === "conversations" || next.name === "conversation" || next.name === "profile" || next.name === "notifications" || next.name === "governance")) {
      notify("error", "请先登录后继续");
      navigate({ name: "auth" });
      return;
    }
    if (next.name === "seller" && currentUser && !currentUser.canTrade) {
      notify("error", "完成校园认证后才能发布商品");
      navigate({ name: "verification" });
      return;
    }
    if (next.name === "admin" && !isAdmin(currentUser)) {
      notify("error", "当前账号没有后台审核权限");
      return;
    }
    navigate(next);
  };

  const handleLogout = async () => {
    try {
      await logout();
      setCurrentUser(null);
      notify("success", "已退出登录");
      navigate({ name: "market" });
    } catch (error) {
      notify("error", messageOf(error));
    }
  };

  const handleDemoLogin = async (username: string) => {
    try {
      // 演示账号统一密码（见 V4/V21 种子数据）。
      const user = await login(username, "520zikejiang");
      setCurrentUser(user);
      notify("success", `已切换到${user.nickname}`);
    } catch (error) {
      notify("error", messageOf(error));
    }
  };

  return (
    <div className="app-shell">
      <header className="app-header">
        <button className="brand" type="button" onClick={() => navigate({ name: "market" })}>
          <span className="brand-mark"><Store size={20} /></span>
          <span>
            <strong>校园二手集市</strong>
            <small>Campus Resale</small>
          </span>
        </button>
        <nav className="main-nav" aria-label="主要导航">
          <NavButton active={route.name === "market"} icon={<Home size={17} />} label="商品" onClick={() => navigate({ name: "market" })} />
          <NavButton active={route.name === "conversations" || route.name === "conversation"} icon={<MessageSquareText size={17} />} label="消息" onClick={() => guardedNavigate({ name: "conversations" })} />
          <NavButton active={route.name === "orders" || route.name === "order"} icon={<ClipboardList size={17} />} label="订单" onClick={() => guardedNavigate({ name: "orders" })} />
          <NavButton active={route.name === "profile"} icon={<UserRound size={17} />} label="我的" onClick={() => guardedNavigate({ name: "profile" })} />
          {currentUser && (
            <NavButton active={route.name === "notifications"} icon={<Bell size={17} />} label="通知" onClick={() => guardedNavigate({ name: "notifications" })} />
          )}
          <NavButton active={route.name === "seller"} icon={<PackagePlus size={17} />} label="发布" onClick={() => guardedNavigate({ name: "seller" })} />
          <NavButton active={route.name === "verification"} icon={<BadgeCheck size={17} />} label="认证" onClick={() => guardedNavigate({ name: "verification" })} />
          <NavButton active={route.name === "governance"} icon={<ShieldAlert size={17} />} label="治理" onClick={() => guardedNavigate({ name: "governance" })} />
          {isAdmin(currentUser) && (
            <NavButton active={route.name === "admin"} icon={<ShieldCheck size={17} />} label="后台" onClick={() => guardedNavigate({ name: "admin" })} />
          )}
        </nav>
        <div className="account-area">
          {showDemoSwitcher && (
            <div className="demo-switcher" aria-label="演示账号切换">
              <button type="button" onClick={() => void handleDemoLogin("buyer_demo")}>买家</button>
              <button type="button" onClick={() => void handleDemoLogin("seller_demo")}>卖家</button>
              <button type="button" onClick={() => void handleDemoLogin("content_admin")}>管理员</button>
            </div>
          )}
          {currentUser ? (
            <>
              <Avatar user={currentUser} />
              <div className="account-copy">
                <strong>{currentUser.nickname}</strong>
                <small>{currentUser.canTrade ? "认证学生" : verificationStatusLabels[currentUser.verificationStatus] ?? currentUser.verificationStatus}</small>
              </div>
              <button className="icon-button" type="button" title="退出登录" aria-label="退出登录" onClick={() => void handleLogout()}>
                <LogOut size={18} />
              </button>
            </>
          ) : (
            <button className="primary-button compact" type="button" onClick={() => navigate({ name: "auth" })}>
              <LogIn size={17} /> 登录
            </button>
          )}
        </div>
      </header>

      {notice && <div className={`toast ${notice.tone}`}>{notice.text}</div>}

      <main>
        {route.name === "market" && (
          <MarketPage
            catalog={catalog}
            goods={publicGoods}
            total={goodsTotal}
            loading={loadingGoods}
            currentUser={currentUser}
            query={query}
            categoryId={categoryId}
            minPrice={minPrice}
            maxPrice={maxPrice}
            conditionLevel={conditionLevel}
            placeId={placeId}
            sort={marketSort}
            onQueryChange={setQuery}
            onCategoryChange={setCategoryId}
            onMinPriceChange={setMinPrice}
            onMaxPriceChange={setMaxPrice}
            onConditionChange={setConditionLevel}
            onPlaceChange={setPlaceId}
            onSortChange={setMarketSort}
            onSearch={() => void refreshGoods()}
            onOpen={(id) => navigate({ name: "goods", id })}
            notify={notify}
          />
        )}
        {route.name === "goods" && <GoodsDetailPage id={route.id} catalog={catalog} currentUser={currentUser} navigate={navigate} onBack={() => navigate({ name: "market" })} notify={notify} />}
        {route.name === "conversations" && currentUser && <ConversationsPage currentUser={currentUser} notify={notify} onOpenConversation={(id) => navigate({ name: "conversation", id })} />}
        {route.name === "conversation" && currentUser && <ConversationDetailPage id={route.id} currentUser={currentUser} places={catalog.places} notify={notify} onBack={() => navigate({ name: "conversations" })} onOpenOrder={(id) => navigate({ name: "order", id })} />}
        {route.name === "orders" && currentUser && <OrdersPage currentUser={currentUser} notify={notify} onOpenOrder={(id) => navigate({ name: "order", id })} />}
        {route.name === "order" && currentUser && <OrderDetailPage id={route.id} currentUser={currentUser} notify={notify} onBack={() => navigate({ name: "orders" })} />}
        {route.name === "profile" && currentUser && <ProfilePage currentUser={currentUser} onUserChange={setCurrentUser} notify={notify} navigate={navigate} />}
        {route.name === "notifications" && currentUser && <NotificationsPage notify={notify} onOpenOrder={(id) => navigate({ name: "order", id })} />}
        {route.name === "auth" && <AuthPage currentUser={currentUser} onAuthenticated={setCurrentUser} navigate={navigate} notify={notify} />}
        {route.name === "verification" && currentUser && <VerificationPage currentUser={currentUser} onUserChange={setCurrentUser} notify={notify} />}
        {route.name === "seller" && currentUser?.canTrade && <SellerPage catalog={catalog} notify={notify} />}
        {route.name === "governance" && currentUser && <GovernancePage currentUser={currentUser} notify={notify} />}
        {route.name === "admin" && isAdmin(currentUser) && <AdminPage notify={notify} navigate={navigate} isSuperAdmin={isSuperAdmin(currentUser)} />}
      </main>
    </div>
  );
}
