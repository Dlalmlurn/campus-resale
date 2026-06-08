// 文件功能："我的"个人中心（头像、收藏、浏览、关注、我发布/买到/卖出）。原内联于 App.tsx。
import { KeyRound, RefreshCw, UserMinus } from "lucide-react";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { getGovernanceOverview, unfollowUser } from "../api/governance";
import { changePassword, getMyGoods, uploadAvatar } from "../api/m1";
import { getOrders } from "../api/orders";
import type { CurrentUser, GoodsSummary, OrderSummary } from "../api/types";
import { Avatar, LoadingBlock, MetricTile, ProfileColumn } from "../components/ui";
import { goodsStatusLabels, loadViewedGoods, messageOf, verificationStatusLabels, type Notify, type Route, type ViewedGoods } from "../shared/app-shared";

export function ProfilePage(props: { currentUser: CurrentUser; onUserChange: (user: CurrentUser) => void; notify: Notify; navigate: (route: Route) => void }) {
  const [favorites, setFavorites] = useState<Awaited<ReturnType<typeof getGovernanceOverview>>["favorites"]>([]);
  const [follows, setFollows] = useState<Awaited<ReturnType<typeof getGovernanceOverview>>["follows"]>([]);
  const [viewedGoods, setViewedGoods] = useState<ViewedGoods[]>([]);
  const [myGoods, setMyGoods] = useState<GoodsSummary[]>([]);
  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [pwForm, setPwForm] = useState({ current: "", next: "", confirm: "" });
  const [pwBusy, setPwBusy] = useState(false);

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

  const submitPassword = async (event: FormEvent) => {
    event.preventDefault();
    if (pwForm.next !== pwForm.confirm) {
      props.notify("error", "两次输入的新密码不一致");
      return;
    }
    setPwBusy(true);
    try {
      const user = await changePassword(pwForm.current, pwForm.next);
      props.onUserChange(user);
      setPwForm({ current: "", next: "", confirm: "" });
      props.notify("success", "密码已更新");
    } catch (error) {
      props.notify("error", messageOf(error));
    } finally {
      setPwBusy(false);
    }
  };

  const unfollow = async (userId: number, nickname: string) => {
    setBusy(true);
    try {
      await unfollowUser(userId);
      setFollows((current) => current.filter((item) => item.followedUser.id !== userId));
      props.notify("success", `已取消关注 ${nickname}`);
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
      <form className="form-panel account-security" onSubmit={(event) => void submitPassword(event)}>
        <div className="panel-title"><h2><KeyRound size={17} /> 修改密码</h2></div>
        <div className="form-grid">
          <label className="form-field"><span>当前密码</span><input required type="password" autoComplete="current-password" value={pwForm.current} onChange={(event) => setPwForm({ ...pwForm, current: event.target.value })} /></label>
          <label className="form-field"><span>新密码</span><input required minLength={8} type="password" autoComplete="new-password" value={pwForm.next} onChange={(event) => setPwForm({ ...pwForm, next: event.target.value })} placeholder="至少 8 位" /></label>
          <label className="form-field"><span>确认新密码</span><input required minLength={8} type="password" autoComplete="new-password" value={pwForm.confirm} onChange={(event) => setPwForm({ ...pwForm, confirm: event.target.value })} /></label>
        </div>
        <p className="form-hint">修改成功后会退出其它设备的登录，当前设备保持登录。</p>
        <button className="secondary-button" disabled={pwBusy} type="submit">{pwBusy ? "提交中…" : "更新密码"}</button>
      </form>
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
              <div className="profile-row profile-row--with-action" key={item.id}>
                <span>{item.followedUser.nickname}</span>
                <button className="text-button danger-action" type="button" disabled={busy} onClick={() => void unfollow(item.followedUser.id, item.followedUser.nickname)}>
                  <UserMinus size={15} /> 取消关注
                </button>
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
