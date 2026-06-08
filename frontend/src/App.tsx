import {
  ArrowLeft,
  BadgeCheck,
  Bell,
  BookOpen,
  BarChart3,
  Check,
  ChevronRight,
  ClipboardList,
  ClipboardCheck,
  CreditCard,
  FileUp,
  Filter,
  Home,
  Heart,
  LogIn,
  LogOut,
  MessageSquareText,
  PackagePlus,
  RefreshCw,
  Search,
  ShieldCheck,
  ShieldAlert,
  ShoppingBag,
  Store,
  UserPlus,
  UserRound,
  X
} from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { ApiError } from "./api/client";
import { createConversation } from "./api/conversations";
import { addFavorite, followUser, getGovernanceOverview, removeFavorite } from "./api/governance";
import { assistGoods, type GoodsAssistResponse } from "./api/intelligence";
import {
  createGoodsDraft,
  getAdminGoods,
  getAdminVerifications,
  getCatalog,
  getCurrentUser,
  getGoodsDetail,
  getMyGoods,
  getPublicGoods,
  getVerification,
  login,
  logout,
  register,
  reviewGoods,
  reviewVerification,
  submitGoods,
  submitVerification,
  updateVerification,
  uploadAvatar,
  uploadFile
} from "./api/m1";
import { createOrder, getOrders } from "./api/orders";
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  type NotificationItem
} from "./api/notifications";
import type {
  CampusPlaceSummary,
  CampusVerification,
  CategorySummary,
  CurrentUser,
  GoodsSummary,
  GoodsUpsertRequest,
  OrderSummary,
  StoredFileSummary,
  TagSummary
} from "./api/types";
import { ReportButton } from "./components/ReportButton";
import { OrderDetailPage, OrdersPage } from "./pages/orders";
import { ConversationDetailPage, ConversationsPage } from "./pages/conversations";
import { AdminDashboardPage } from "./pages/admin-dashboard";
import { AdminAuditLogsPage } from "./pages/admin-audit-logs";
import { AdminFundsPage } from "./pages/admin-funds";
import { GovernancePage } from "./pages/governance";

const showDemoSwitcher = import.meta.env.DEV && import.meta.env.VITE_ENABLE_DEMO_SWITCHER === "true";

type Route =
  | { name: "market" }
  | { name: "goods"; id: number }
  | { name: "conversations" }
  | { name: "conversation"; id: number }
  | { name: "orders" }
  | { name: "order"; id: number }
  | { name: "profile" }
  | { name: "notifications" }
  | { name: "auth" }
  | { name: "verification" }
  | { name: "seller" }
  | { name: "governance" }
  | { name: "admin" };

type Catalog = {
  categories: CategorySummary[];
  tags: TagSummary[];
  places: CampusPlaceSummary[];
};

const emptyCatalog: Catalog = { categories: [], tags: [], places: [] };
const conditionLabels: Record<string, string> = {
  NEW: "全新",
  LIKE_NEW: "几乎全新",
  LIGHTLY_USED: "轻度使用",
  NOTICEABLY_USED: "明显使用"
};
const goodsStatusLabels: Record<string, string> = {
  DRAFT: "草稿",
  PENDING_REVIEW: "待审核",
  ON_SALE: "在售",
  RESERVED: "已预订",
  SOLD: "已售",
  OFF_SHELF: "已下架",
  DELETED: "已删除"
};
const auditStatusLabels: Record<string, string> = {
  NOT_SUBMITTED: "未提交",
  PENDING: "待审核",
  APPROVED: "已通过",
  REJECTED: "已驳回"
};
const verificationStatusLabels: Record<string, string> = {
  NONE: "未认证",
  DRAFT: "草稿",
  ACCUMULATING: "资料积累中",
  PENDING_REVIEW: "待审核",
  APPROVED: "已通过",
  REJECTED: "已驳回",
  INVALID: "已失效"
};
const marketSortLabels: Record<string, string> = {
  RECOMMENDED: "推荐优先",
  LATEST: "最新发布",
  PRICE_ASC: "价格从低到高",
  PRICE_DESC: "价格从高到低"
};
const sellerStatusFilters: Array<{ value: string; label: string }> = [
  { value: "", label: "全部" },
  { value: "DRAFT", label: "草稿" },
  { value: "PENDING_REVIEW", label: "待审核" },
  { value: "ON_SALE", label: "在售" },
  { value: "RESERVED", label: "已预订" },
  { value: "SOLD", label: "已售" },
  { value: "OFF_SHELF", label: "已下架" }
];
const sellerDraftStorageKey = "campus-resale:seller-draft";
const viewedGoodsStorageKey = "campus-resale:viewed-goods";

type ViewedGoods = {
  id: number;
  title: string;
  listPrice: string;
  viewedAt: string;
};

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

  const notify = useCallback((tone: "success" | "error", text: string) => {
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
        {route.name === "admin" && isAdmin(currentUser) && <AdminPage notify={notify} navigate={navigate} />}
      </main>
    </div>
  );
}

function MarketPage(props: {
  catalog: Catalog;
  goods: GoodsSummary[];
  total: number;
  loading: boolean;
  currentUser: CurrentUser | null;
  query: string;
  categoryId: string;
  minPrice: string;
  maxPrice: string;
  conditionLevel: string;
  placeId: string;
  sort: string;
  onQueryChange: (value: string) => void;
  onCategoryChange: (value: string) => void;
  onMinPriceChange: (value: string) => void;
  onMaxPriceChange: (value: string) => void;
  onConditionChange: (value: string) => void;
  onPlaceChange: (value: string) => void;
  onSortChange: (value: string) => void;
  onSearch: () => void;
  onOpen: (id: number) => void;
  notify: Notify;
}) {
  const [favoriteIds, setFavoriteIds] = useState<Set<number>>(new Set());

  useEffect(() => {
    if (!props.currentUser) {
      setFavoriteIds(new Set());
      return;
    }
    void getGovernanceOverview()
      .then((overview) => setFavoriteIds(new Set(overview.favorites.map((item) => item.goodsId))))
      .catch(() => setFavoriteIds(new Set()));
  }, [props.currentUser]);

  const runCardAction = async (action: () => Promise<unknown>, success: string) => {
    if (!props.currentUser) {
      props.notify("error", "请先登录后继续");
      return;
    }
    try {
      await action();
      props.notify("success", success);
    } catch (error) {
      props.notify("error", messageOf(error));
    }
  };

  const toggleFavorite = async (item: GoodsSummary) => {
    const favorited = favoriteIds.has(item.id);
    await runCardAction(
      async () => {
        if (favorited) {
          await removeFavorite(item.id);
          setFavoriteIds((current) => {
            const next = new Set(current);
            next.delete(item.id);
            return next;
          });
        } else {
          await addFavorite(item.id);
          setFavoriteIds((current) => new Set(current).add(item.id));
        }
      },
      favorited ? "已取消收藏" : "已收藏商品"
    );
  };

  return (
    <>
      <section className="page-heading market-heading">
        <div>
          <p className="eyebrow">校内闲置流转</p>
          <h1>商品发现</h1>
          <p>浏览审核通过的校内闲置，按分类和关键词快速筛选。</p>
        </div>
        <div className="market-stat">
          <strong>{props.total}</strong>
          <span>件在售商品</span>
        </div>
      </section>
      <section className="search-toolbar" aria-label="商品搜索">
        <label className="search-field">
          <Search size={18} />
          <input
            value={props.query}
            onChange={(event) => props.onQueryChange(event.target.value)}
            onKeyDown={(event) => event.key === "Enter" && props.onSearch()}
            placeholder="搜索教材、数码、生活用品"
          />
        </label>
        <label className="select-field">
          <Filter size={17} />
          <select aria-label="分类" value={props.categoryId} onChange={(event) => props.onCategoryChange(event.target.value)}>
            <option value="">全部分类</option>
            {props.catalog.categories.map((category) => <option value={category.id} key={category.id}>{category.name}</option>)}
          </select>
        </label>
        <button className="primary-button" type="button" onClick={props.onSearch}>搜索</button>
        <div className="market-filter-row">
          <label className="compact-filter-field">
            <span>最低价</span>
            <input min="0" step="0.01" type="number" value={props.minPrice} onChange={(event) => props.onMinPriceChange(event.target.value)} placeholder="不限" />
          </label>
          <label className="compact-filter-field">
            <span>最高价</span>
            <input min="0" step="0.01" type="number" value={props.maxPrice} onChange={(event) => props.onMaxPriceChange(event.target.value)} placeholder="不限" />
          </label>
          <label className="compact-filter-field">
            <span>成色</span>
            <select value={props.conditionLevel} onChange={(event) => props.onConditionChange(event.target.value)}>
              <option value="">全部成色</option>
              {Object.entries(conditionLabels).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
            </select>
          </label>
          <label className="compact-filter-field">
            <span>地点</span>
            <select value={props.placeId} onChange={(event) => props.onPlaceChange(event.target.value)}>
              <option value="">全部地点</option>
              {props.catalog.places.map((place) => <option value={place.id} key={place.id}>{place.campus} · {place.name}</option>)}
            </select>
          </label>
          <label className="compact-filter-field">
            <span>排序</span>
            <select value={props.sort} onChange={(event) => props.onSortChange(event.target.value)}>
              {Object.entries(marketSortLabels).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
            </select>
          </label>
        </div>
      </section>
      <section className="goods-grid" aria-label="商品列表">
        {props.loading ? <LoadingBlock /> : props.goods.length === 0 ? <EmptyBlock title="暂时没有匹配商品" /> : props.goods.map((item) => (
          <article className="goods-card" key={item.id}>
            <button className="goods-card-main" type="button" onClick={() => props.onOpen(item.id)}>
              <GoodsImage item={item} />
              <div className="goods-card-body">
                <div className="goods-card-topline">
                  <span>{item.category.name}</span>
                  <span>#{item.id} · {conditionLabels[item.conditionLevel] ?? item.conditionLevel}</span>
                </div>
                <h2>{item.title}</h2>
                <p>{item.description}</p>
                {item.recommendationReason && <p className="recommendation-reason">推荐理由：{item.recommendationReason}</p>}
                <div className="goods-card-footer">
                  <strong>¥{item.listPrice}</strong>
                  <span>{item.seller.nickname}</span>
                </div>
              </div>
            </button>
            <div className="goods-card-actions">
              <button
                className={`secondary-button compact favorite-button ${favoriteIds.has(item.id) ? "active" : ""}`}
                type="button"
                aria-label={`${favoriteIds.has(item.id) ? "取消收藏" : "收藏"} ${item.title}`}
                onClick={() => void toggleFavorite(item)}
              >
                <Heart size={16} fill={favoriteIds.has(item.id) ? "currentColor" : "none"} /> {favoriteIds.has(item.id) ? "已收藏" : "收藏"}
              </button>
              <button
                className="secondary-button compact"
                type="button"
                aria-label={`关注 ${item.seller.nickname}`}
                disabled={props.currentUser?.id === item.seller.id}
                onClick={() => void runCardAction(() => followUser(item.seller.id), "已关注卖家")}
              >
                <UserPlus size={16} /> 关注卖家
              </button>
              <ReportButton currentUser={props.currentUser} targetType="GOODS" targetId={item.id} notify={props.notify} />
            </div>
          </article>
        ))}
      </section>
    </>
  );
}

function GoodsDetailPage(props: { id: number; catalog: Catalog; currentUser: CurrentUser | null; navigate: (route: Route) => void; onBack: () => void; notify: Notify }) {
  const [item, setItem] = useState<GoodsSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [orderOpen, setOrderOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [orderForm, setOrderForm] = useState({
    tradePlaceId: "",
    tradePlaceDetail: "",
    meetupTime: "",
    note: ""
  });

  useEffect(() => {
    void getGoodsDetail(props.id)
      .then((next) => {
        setItem(next);
        if (props.currentUser) recordViewedGoods(next);
      })
      .catch((error) => props.notify("error", messageOf(error)))
      .finally(() => setLoading(false));
  }, [props.currentUser, props.id, props.notify]);

  if (loading) return <LoadingBlock />;
  if (!item) return <EmptyBlock title="商品不存在或暂不可见" />;

  const openOrder = () => {
    if (!props.currentUser) {
      props.notify("error", "请先登录后继续");
      props.navigate({ name: "auth" });
      return;
    }
    if (!props.currentUser.canTrade) {
      props.notify("error", "完成校园认证后才能下单");
      props.navigate({ name: "verification" });
      return;
    }
    if (props.currentUser.id === item.seller.id) {
      props.notify("error", "不能购买自己发布的商品");
      return;
    }
    setOrderOpen(true);
  };

  const openConversation = async () => {
    if (!props.currentUser) {
      props.notify("error", "请先登录后继续");
      props.navigate({ name: "auth" });
      return;
    }
    if (!props.currentUser.canTrade) {
      props.notify("error", "完成校园认证后才能联系卖家");
      props.navigate({ name: "verification" });
      return;
    }
    if (props.currentUser.id === item.seller.id) {
      props.notify("error", "不能和自己发布的商品建立会话");
      return;
    }
    setBusy(true);
    try {
      const detail = await createConversation(item.id);
      props.navigate({ name: "conversation", id: detail.conversation.id });
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  const submitOrder = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    try {
      const created = await createOrder({
        goodsId: item.id,
        acceptedBargainCardId: null,
        tradePlaceId: Number(orderForm.tradePlaceId) || null,
        tradePlaceDetail: orderForm.tradePlaceDetail,
        meetupTime: orderForm.meetupTime ? new Date(orderForm.meetupTime).toISOString() : null,
        note: orderForm.note
      });
      props.notify("success", "订单已提交，等待卖家确认");
      props.navigate({ name: "order", id: created.id });
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="detail-layout">
      <button className="text-button back-button" type="button" onClick={props.onBack}><ArrowLeft size={17} /> 返回商品列表</button>
      <div className="detail-grid">
        <GoodsImage item={item} large />
        <div className="detail-copy">
          <div className="badge-row">
            <StatusBadge value={item.status} labels={goodsStatusLabels} />
            <span className="badge neutral">{conditionLabels[item.conditionLevel] ?? item.conditionLevel}</span>
          </div>
          <h1>{item.title}</h1>
          <strong className="detail-price">¥{item.listPrice}</strong>
          <p>{item.description}</p>
          <dl className="detail-list">
            <div><dt>商品 ID</dt><dd>#{item.id}</dd></div>
            <div><dt>分类</dt><dd>{item.category.name}</dd></div>
            <div><dt>卖家</dt><dd>{item.seller.nickname}</dd></div>
            <div><dt>发布时间</dt><dd>{formatDate(item.publishedAt)}</dd></div>
          </dl>
          <div className="order-entry">
            <button className="primary-button" type="button" onClick={openOrder}>立即下单</button>
            <button className="secondary-button" disabled={busy} type="button" onClick={() => void openConversation()}><MessageSquareText size={17} /> 联系卖家</button>
            <button className="secondary-button" type="button" onClick={() => props.navigate({ name: "orders" })}>查看我的订单</button>
            <ReportButton currentUser={props.currentUser} targetType="GOODS" targetId={item.id} notify={props.notify} />
          </div>
          {orderOpen && (
            <form className="form-panel order-compose" onSubmit={(event) => void submitOrder(event)}>
              <div className="panel-title"><h2>确认交易约定</h2></div>
              <FormField label="交易地点">
                <select required value={orderForm.tradePlaceId} onChange={(event) => setOrderForm({ ...orderForm, tradePlaceId: event.target.value })}>
                  <option value="">请选择</option>
                  {props.catalog.places.map((place) => <option value={place.id} key={place.id}>{place.campus} · {place.name}</option>)}
                </select>
              </FormField>
              <FormField label="地点补充">
                <input value={orderForm.tradePlaceDetail} onChange={(event) => setOrderForm({ ...orderForm, tradePlaceDetail: event.target.value })} placeholder="例如：图书馆正门台阶旁" />
              </FormField>
              <FormField label="见面时间">
                <input required type="datetime-local" value={orderForm.meetupTime} onChange={(event) => setOrderForm({ ...orderForm, meetupTime: event.target.value })} />
              </FormField>
              <FormField label="给卖家的备注">
                <textarea rows={3} value={orderForm.note} onChange={(event) => setOrderForm({ ...orderForm, note: event.target.value })} placeholder="补充取货说明或联系方式" />
              </FormField>
              <div className="button-row">
                <button className="primary-button" disabled={busy} type="submit">提交订单</button>
                <button className="secondary-button" disabled={busy} type="button" onClick={() => setOrderOpen(false)}>取消</button>
              </div>
            </form>
          )}
        </div>
      </div>
    </section>
  );
}

function AuthPage(props: { currentUser: CurrentUser | null; onAuthenticated: (user: CurrentUser) => void; navigate: (route: Route) => void; notify: Notify }) {
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

function VerificationPage(props: { currentUser: CurrentUser; onUserChange: (user: CurrentUser) => void; notify: Notify }) {
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

function SellerPage(props: { catalog: Catalog; notify: Notify }) {
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

function NotificationsPage(props: { notify: Notify; onOpenOrder: (id: number) => void }) {
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [page, count] = await Promise.all([
        getNotifications({ unreadOnly, page: 1, pageSize: 20 }),
        getUnreadNotificationCount()
      ]);
      setItems(page.items);
      setUnreadCount(count.unreadCount);
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setLoading(false);
    }
  }, [props.notify, unreadOnly]);

  useEffect(() => { void load(); }, [load]);

  const markAllRead = async () => {
    try {
      await markAllNotificationsRead();
      setUnreadCount(0);
      setItems((current) => current.map((item) => ({ ...item, read: true, readAt: item.readAt ?? new Date().toISOString() })));
      props.notify("success", "通知已全部标为已读");
    } catch (error) {
      props.notify("error", messageOf(error));
    }
  };

  return (
    <section>
      <div className="order-toolbar">
        <PageHeading eyebrow="消息中心" title="站内通知" text="集中查看订单、支付、审核和结算状态变化。" />
        <div className="notification-actions">
          <span className="badge neutral">{unreadCount} 条未读</span>
          <button className={`secondary-button compact ${unreadOnly ? "active" : ""}`} type="button" onClick={() => setUnreadOnly((value) => !value)}>
            只看未读
          </button>
          <button className="primary-button compact" type="button" onClick={() => void markAllRead()} disabled={unreadCount === 0}>
            全部标为已读
          </button>
        </div>
      </div>

      {loading ? <LoadingBlock /> : items.length === 0 ? <EmptyBlock title="当前没有通知" /> : (
        <div className="notification-list">
          {items.map((item) => (
            <article className={`notification-row ${item.read ? "" : "unread"}`} key={item.id}>
              <div className="notification-main">
                <div className="notification-title-line">
                  <strong>{item.title}</strong>
                  {!item.read && <span className="badge warning">未读</span>}
                </div>
                <p>{item.content}</p>
                <small>{formatDate(item.createdAt)} · {notificationTypeLabel(item.type)}</small>
              </div>
              {item.relatedType === "ORDER" && item.relatedId !== null && (
                <button className="secondary-button compact" type="button" onClick={() => props.onOpenOrder(item.relatedId!)}>
                  查看订单
                </button>
              )}
            </article>
          ))}
        </div>
      )}
    </section>
  );
}

function AdminPage(props: { notify: Notify; navigate: (route: Route) => void }) {
  const [tab, setTab] = useState<"verification" | "goods" | "dashboard" | "audit" | "funds">("dashboard");
  const [verifications, setVerifications] = useState<CampusVerification[]>([]);
  const [goods, setGoods] = useState<GoodsSummary[]>([]);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [verificationPage, goodsPage] = await Promise.all([
        getAdminVerifications("PENDING_REVIEW"),
        getAdminGoods("PENDING_REVIEW", "PENDING")
      ]);
      setVerifications(verificationPage.items);
      setGoods(goodsPage.items);
    } catch (error) {
      props.notify("error", messageOf(error));
    }
  }, [props.notify]);

  useEffect(() => {
    // 审核队列只在进入 verification/goods tab 时加载，避免看板页不必要的请求
    if (tab === "verification" || tab === "goods") {
      void load();
    }
  }, [load, tab]);

  const review = async (kind: "verification" | "goods", id: number, action: "approve" | "reject") => {
    const reason = window.prompt(action === "approve" ? "填写审核备注（可选）" : "填写驳回原因", "") ?? "";
    setBusy(true);
    try {
      if (kind === "verification") await reviewVerification(id, action, reason);
      else await reviewGoods(id, action, reason);
      props.notify("success", action === "approve" ? "审核已通过" : "记录已驳回");
      await load();
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section>
      <PageHeading eyebrow="N2 后台验收" title="后台验收闭环" text="汇总统计看板、审核队列、审计日志和通知入口，演示关键状态变化可追溯。" />
      <div className="admin-demo-guide">
        <div>
          <p className="eyebrow">Demo flow</p>
          <h2>演示导航</h2>
        </div>
        <button className="secondary-button compact" type="button" onClick={() => setTab("dashboard")}>查看统计看板</button>
        <button className="secondary-button compact" type="button" onClick={() => setTab("verification")}>认证审核</button>
        <button className="secondary-button compact" type="button" onClick={() => setTab("goods")}>商品审核</button>
        <button className="secondary-button compact" type="button" onClick={() => setTab("audit")}>查看审计日志</button>
        <button className="secondary-button compact" type="button" onClick={() => setTab("funds")}>资金管理</button>
        <button className="primary-button compact" type="button" onClick={() => props.navigate({ name: "notifications" })}>通知列表</button>
      </div>
      {/* 顶部 Tab 导航 */}
      <div className="segmented-control admin-tabs admin-tabs--wide">
        <button
          className={tab === "dashboard" ? "active" : ""}
          type="button"
          onClick={() => setTab("dashboard")}
        >
          <BarChart3 size={15} /> 数据看板
        </button>
        <button
          className={tab === "verification" ? "active" : ""}
          type="button"
          onClick={() => setTab("verification")}
        >
          <ShieldCheck size={15} /> 校园认证
          {verifications.length > 0 && <b>{verifications.length}</b>}
        </button>
        <button
          className={tab === "goods" ? "active" : ""}
          type="button"
          onClick={() => setTab("goods")}
        >
          <ShieldCheck size={15} /> 商品审核
          {goods.length > 0 && <b>{goods.length}</b>}
        </button>
        <button
          className={tab === "audit" ? "active" : ""}
          type="button"
          onClick={() => setTab("audit")}
        >
          <ShieldAlert size={15} /> 审计日志
        </button>
        <button
          className={tab === "funds" ? "active" : ""}
          type="button"
          onClick={() => setTab("funds")}
        >
          <CreditCard size={15} /> 资金
        </button>
      </div>

      {/* 数据看板 */}
      {tab === "dashboard" && <AdminDashboardPage notify={props.notify} />}

      {/* 审计日志 */}
      {tab === "audit" && <AdminAuditLogsPage notify={props.notify} />}

      {tab === "funds" && <AdminFundsPage notify={props.notify} />}

      {/* 审核队列 */}
      {(tab === "verification" || tab === "goods") && (
        <div className="review-list">
          {tab === "verification" && (verifications.length === 0
            ? <EmptyBlock title="当前没有待审核的认证记录" />
            : verifications.map((item) => (
              <article className="review-row" key={item.id}>
                <div><strong>{item.realName} · {item.studentNo}</strong><p>{item.department} · {item.campusEmail}</p></div>
                <div className="review-meta"><span className="score">{item.score} 分</span><StatusBadge value={item.status} labels={verificationStatusLabels} /></div>
                <ReviewActions disabled={busy} onApprove={() => void review("verification", item.id!, "approve")} onReject={() => void review("verification", item.id!, "reject")} />
              </article>
            ))
          )}
          {tab === "goods" && (goods.length === 0
            ? <EmptyBlock title="当前没有待审核的商品" />
            : goods.map((item) => (
              <article className="review-row" key={item.id}>
                <div><strong>{item.title}</strong><p>{item.category.name} · {item.seller.nickname} · ¥{item.listPrice}</p></div>
                <div className="review-meta"><StatusBadge value={item.status} labels={goodsStatusLabels} /><StatusBadge value={item.auditStatus} labels={auditStatusLabels} /></div>
                <ReviewActions disabled={busy} onApprove={() => void review("goods", item.id, "approve")} onReject={() => void review("goods", item.id, "reject")} />
              </article>
            ))
          )}
        </div>
      )}
    </section>
  );
}

function ProfilePage(props: { currentUser: CurrentUser; onUserChange: (user: CurrentUser) => void; notify: Notify; navigate: (route: Route) => void }) {
  const [favorites, setFavorites] = useState<Awaited<ReturnType<typeof getGovernanceOverview>>["favorites"]>([]);
  const [follows, setFollows] = useState<Awaited<ReturnType<typeof getGovernanceOverview>>["follows"]>([]);
  const [viewedGoods, setViewedGoods] = useState<ViewedGoods[]>([]);
  const [myGoods, setMyGoods] = useState<GoodsSummary[]>([]);
  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [overview, goodsPage, orderPage] = await Promise.all([
        getGovernanceOverview(),
        getMyGoods({ pageSize: 50 }),
        getOrders({ pageSize: 50 })
      ]);
      setFavorites(overview.favorites);
      setFollows(overview.follows);
      setViewedGoods(loadViewedGoods());
      setMyGoods(goodsPage.items);
      setOrders(orderPage.items);
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setLoading(false);
    }
  }, [props.notify]);

  useEffect(() => { void load(); }, [load]);

  const upload = async (file?: File) => {
    if (!file) return;
    setBusy(true);
    try {
      const user = await uploadAvatar(file);
      props.onUserChange(user);
      props.notify("success", "头像已更新");
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  const bought = orders.filter((order) => order.buyer.id === props.currentUser.id);
  const sold = orders.filter((order) => order.seller.id === props.currentUser.id);

  return (
    <section>
      <section className="page-heading profile-heading">
        <div>
          <p className="eyebrow">个人中心</p>
          <h1>我的</h1>
          <p>查看个人资料、收藏关注、发布商品以及买卖订单。</p>
        </div>
        <button className="icon-button subtle" type="button" aria-label="刷新我的页" onClick={() => void load()}><RefreshCw size={17} /></button>
      </section>
      <section className="profile-overview">
        <div className="profile-identity">
          <Avatar user={props.currentUser} large />
          <div>
            <h2>{props.currentUser.nickname}</h2>
            <p>{props.currentUser.username} · {props.currentUser.canTrade ? "认证学生" : verificationStatusLabels[props.currentUser.verificationStatus] ?? props.currentUser.verificationStatus}</p>
            <label className="secondary-button compact avatar-upload">
              上传头像
              <input accept="image/jpeg,image/png,image/webp" disabled={busy} type="file" onChange={(event) => void upload(event.target.files?.[0])} />
            </label>
          </div>
        </div>
        <div className="profile-stats">
          <MetricTile label="收藏" value={favorites.length} />
          <MetricTile label="浏览" value={viewedGoods.length} />
          <MetricTile label="关注" value={follows.length} />
          <MetricTile label="发布" value={myGoods.length} />
          <MetricTile label="买到 / 卖出" value={`${bought.length} / ${sold.length}`} />
        </div>
      </section>
      {loading ? <LoadingBlock /> : (
        <div className="profile-grid">
          <ProfileColumn title="我的收藏" empty="暂无收藏商品">
            {favorites.map((item) => (
              <button className="profile-row" type="button" key={item.id} onClick={() => props.navigate({ name: "goods", id: item.goodsId })}>
                <span>{item.goodsTitle}</span>
                <strong>¥{item.goodsPrice}</strong>
              </button>
            ))}
          </ProfileColumn>
          <ProfileColumn title="我的关注" empty="暂无关注用户">
            {follows.map((item) => (
              <div className="profile-row" key={item.id}>
                <span>{item.followedUser.nickname}</span>
                <strong>#{item.followedUser.id}</strong>
              </div>
            ))}
          </ProfileColumn>
          <ProfileColumn title="最近浏览" empty="暂无浏览记录">
            {viewedGoods.map((item) => (
              <button className="profile-row" type="button" key={`${item.id}-${item.viewedAt}`} onClick={() => props.navigate({ name: "goods", id: item.id })}>
                <span>{item.title}</span>
                <strong>¥{item.listPrice}</strong>
              </button>
            ))}
          </ProfileColumn>
          <ProfileColumn title="我发布的" empty="暂无发布商品">
            {myGoods.map((item) => (
              <button className="profile-row" type="button" key={item.id} onClick={() => props.navigate({ name: "goods", id: item.id })}>
                <span>{item.title}</span>
                <strong>{goodsStatusLabels[item.status] ?? item.status}</strong>
              </button>
            ))}
          </ProfileColumn>
          <ProfileColumn title="我买到的" empty="暂无买入订单">
            {bought.map((item) => (
              <button className="profile-row" type="button" key={item.id} onClick={() => props.navigate({ name: "order", id: item.id })}>
                <span>{item.goodsTitle}</span>
                <strong>¥{item.frozenAmount}</strong>
              </button>
            ))}
          </ProfileColumn>
          <ProfileColumn title="我卖出的" empty="暂无卖出订单">
            {sold.map((item) => (
              <button className="profile-row" type="button" key={item.id} onClick={() => props.navigate({ name: "order", id: item.id })}>
                <span>{item.goodsTitle}</span>
                <strong>¥{item.frozenAmount}</strong>
              </button>
            ))}
          </ProfileColumn>
        </div>
      )}
    </section>
  );
}

function MetricTile(props: { label: string; value: string | number }) {
  return <div className="profile-stat"><span>{props.label}</span><strong>{props.value}</strong></div>;
}

function ProfileColumn(props: { title: string; empty: string; children: React.ReactNode }) {
  const items = Array.isArray(props.children) ? props.children.filter(Boolean) : props.children ? [props.children] : [];
  return <section className="profile-column"><h2>{props.title}</h2>{items.length === 0 ? <p className="empty-line">{props.empty}</p> : props.children}</section>;
}

function NavButton(props: { active: boolean; icon: React.ReactNode; label: string; onClick: () => void }) {
  return <button className={`nav-button ${props.active ? "active" : ""}`} type="button" onClick={props.onClick}>{props.icon}<span>{props.label}</span></button>;
}

function Avatar({ user, large = false }: { user: CurrentUser; large?: boolean }) {
  const initial = user.nickname.trim().slice(0, 1).toUpperCase() || user.username.slice(0, 1).toUpperCase();
  return user.avatarUrl
    ? <img className={`avatar ${large ? "large" : ""}`} src={user.avatarUrl} alt={`${user.nickname}头像`} />
    : <span className={`avatar fallback ${large ? "large" : ""}`}>{initial}</span>;
}

function PageHeading(props: { eyebrow: string; title: string; text: string }) {
  return <section className="page-heading"><div><p className="eyebrow">{props.eyebrow}</p><h1>{props.title}</h1><p>{props.text}</p></div></section>;
}

function FormField(props: { label: string; children: React.ReactNode }) {
  return <label className="form-field"><span>{props.label}</span>{props.children}</label>;
}

function GoodsImage({ item, large = false }: { item: GoodsSummary; large?: boolean }) {
  return item.primaryImage
    ? <img className={`goods-image ${large ? "large" : ""}`} src={item.primaryImage.url} alt={item.title} />
    : <div className={`goods-image placeholder ${large ? "large" : ""}`}><ShoppingBag size={large ? 52 : 36} /></div>;
}

function StatusBadge({ value, labels }: { value: string; labels: Record<string, string> }) {
  const tone = ["APPROVED", "ON_SALE"].includes(value) ? "success" : ["REJECTED", "INVALID"].includes(value) ? "danger" : "warning";
  return <span className={`badge ${tone}`}>{labels[value] ?? value}</span>;
}

function ReviewActions(props: { disabled: boolean; onApprove: () => void; onReject: () => void }) {
  return <div className="review-actions"><button className="approve-button" disabled={props.disabled} aria-label="通过" title="通过" type="button" onClick={props.onApprove}><Check size={17} /></button><button className="reject-button" disabled={props.disabled} aria-label="驳回" title="驳回" type="button" onClick={props.onReject}><X size={17} /></button></div>;
}

function LoadingBlock() {
  return (
    <div className="state-block loading-block">
      <RefreshCw className="spin" size={24} />
      <strong>加载中</strong>
      <div className="loading-skeleton" aria-hidden="true"><span /><span /><span /></div>
    </div>
  );
}

function EmptyBlock({ title }: { title: string }) {
  return <div className="state-block"><ClipboardCheck size={24} /><strong>{title}</strong></div>;
}

function parseRoute(): Route {
  const value = window.location.hash.replace(/^#\/?/, "") || "market";
  if (value.startsWith("goods/")) return { name: "goods", id: Number(value.split("/")[1]) };
  if (value.startsWith("conversations/")) return { name: "conversation", id: Number(value.split("/")[1]) };
  if (value.startsWith("orders/")) return { name: "order", id: Number(value.split("/")[1]) };
  if (["market", "conversations", "orders", "profile", "notifications", "auth", "verification", "seller", "governance", "admin"].includes(value)) return { name: value as Route["name"] } as Route;
  return { name: "market" };
}

function routeHash(route: Route) {
  if (route.name === "goods") return `#/goods/${route.id}`;
  if (route.name === "conversation") return `#/conversations/${route.id}`;
  if (route.name === "order") return `#/orders/${route.id}`;
  return `#/${route.name}`;
}

function isAdmin(user: CurrentUser | null) {
  return Boolean(user?.roles.some((role) => ["CONTENT_ADMIN", "SUPER_ADMIN"].includes(role)));
}

function messageOf(error: unknown) {
  return error instanceof Error ? error.message : "请求失败，请稍后重试";
}

function factorLabel(value: string) {
  return ({ NAME_STUDENT_NO: "姓名与学号", DEPARTMENT: "院系信息", CAMPUS_EMAIL: "校园邮箱", STUDENT_CARD: "学生证", CAMPUS_CARD: "校园卡" } as Record<string, string>)[value] ?? value;
}

function notificationTypeLabel(value: string) {
  return ({
    ORDER_CREATED: "订单创建",
    ORDER_SELLER_CONFIRMED: "卖家确认",
    PAYMENT_ESCROWED: "支付托管",
    COMPLETION_REQUESTED: "完成确认",
    ORDER_COMPLETED: "订单完成",
    SETTLEMENT_STATUS_CHANGED: "结算状态",
    MESSAGE_RECEIVED: "私信消息",
    BARGAIN_OFFERED: "收到议价",
    BARGAIN_ACCEPTED: "议价接受",
    BARGAIN_REJECTED: "议价拒绝",
    AI_REVIEW_REMINDER: "AI 风险提醒"
  } as Record<string, string>)[value] ?? value;
}

function formatDate(value?: string | null) {
  return value ? new Date(value).toLocaleString("zh-CN") : "尚未发布";
}

function formatBytes(value: number) {
  return value < 1024 * 1024 ? `${Math.ceil(value / 1024)} KB` : `${(value / 1024 / 1024).toFixed(1)} MB`;
}

function emptyGoodsDraft(): GoodsUpsertRequest {
  return {
    title: "",
    description: "",
    categoryId: null,
    conditionLevel: "LIKE_NEW",
    listPrice: "",
    tradePlaceId: null,
    tradePlaceDetail: "",
    availableTimeText: "",
    imageFileIds: [],
    tagIds: []
  };
}

function loadSellerDraft(): GoodsUpsertRequest {
  try {
    const raw = window.localStorage.getItem(sellerDraftStorageKey);
    return raw ? { ...emptyGoodsDraft(), ...JSON.parse(raw) } : emptyGoodsDraft();
  } catch {
    return emptyGoodsDraft();
  }
}

function saveSellerDraft(form: GoodsUpsertRequest) {
  try {
    window.localStorage.setItem(sellerDraftStorageKey, JSON.stringify(form));
  } catch {
    // 浏览器隐私模式或存储配额异常时，表单仍可正常提交。
  }
}

function clearSellerDraft() {
  try {
    window.localStorage.removeItem(sellerDraftStorageKey);
  } catch {
    // 清理失败不影响草稿创建结果。
  }
}

function loadViewedGoods(): ViewedGoods[] {
  try {
    const raw = window.localStorage.getItem(viewedGoodsStorageKey);
    return raw ? JSON.parse(raw) as ViewedGoods[] : [];
  } catch {
    return [];
  }
}

function recordViewedGoods(item: GoodsSummary) {
  try {
    const current = loadViewedGoods().filter((entry) => entry.id !== item.id);
    const next = [{ id: item.id, title: item.title, listPrice: item.listPrice, viewedAt: new Date().toISOString() }, ...current].slice(0, 20);
    window.localStorage.setItem(viewedGoodsStorageKey, JSON.stringify(next));
  } catch {
    // 最近浏览只是体验增强，存储失败不影响商品详情查看。
  }
}

type Notify = (tone: "success" | "error", text: string) => void;
