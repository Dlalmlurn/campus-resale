// 文件功能：商品详情页（下单、联系卖家、举报入口、最近浏览记录）。原内联于 App.tsx。
import { ArrowLeft, MessageSquareText } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { createConversation } from "../api/conversations";
import { getGoodsDetail } from "../api/m1";
import { createOrder } from "../api/orders";
import type { CurrentUser, GoodsSummary } from "../api/types";
import { ReportButton } from "../components/ReportButton";
import { EmptyBlock, FormField, GoodsImage, LoadingBlock, StatusBadge } from "../components/ui";
import { conditionLabels, formatDate, goodsStatusLabels, messageOf, recordViewedGoods, type Catalog, type Notify, type Route } from "../shared/app-shared";

export function GoodsDetailPage(props: { id: number; catalog: Catalog; currentUser: CurrentUser | null; navigate: (route: Route) => void; onBack: () => void; notify: Notify }) {
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
