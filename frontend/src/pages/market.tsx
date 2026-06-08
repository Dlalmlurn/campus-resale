// 文件功能：商品发现页（搜索、筛选、收藏、关注、举报入口）。原内联于 App.tsx。
import { Filter, Heart, Search, UserPlus } from "lucide-react";
import { useEffect, useState } from "react";
import { addFavorite, followUser, getGovernanceOverview, removeFavorite } from "../api/governance";
import type { CurrentUser, GoodsSummary } from "../api/types";
import { ReportButton } from "../components/ReportButton";
import { EmptyBlock, GoodsImage, LoadingBlock } from "../components/ui";
import { conditionLabels, marketSortLabels, messageOf, type Catalog, type Notify } from "../shared/app-shared";

export function MarketPage(props: {
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
