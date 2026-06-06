import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";
import type { CurrentUser } from "./api/types";

const registeredUser: CurrentUser = {
  id: 21,
  username: "user_demo",
  nickname: "普通同学",
  roles: ["REGISTERED_USER"],
  verificationStatus: "NONE",
  canTrade: false
};

const verifiedBuyer: CurrentUser = {
  id: 31,
  username: "buyer_demo",
  nickname: "买家同学",
  roles: ["REGISTERED_USER", "VERIFIED_STUDENT"],
  verificationStatus: "APPROVED",
  canTrade: true
};

const verifiedSeller: CurrentUser = {
  id: 11,
  username: "seller_demo",
  nickname: "小林同学",
  roles: ["REGISTERED_USER", "VERIFIED_STUDENT"],
  verificationStatus: "APPROVED",
  canTrade: true
};

const contentAdmin: CurrentUser = {
  id: 1,
  username: "content_admin",
  nickname: "内容管理员",
  roles: ["CONTENT_ADMIN"],
  verificationStatus: "NONE",
  canTrade: false
};

describe("App", () => {
  beforeEach(() => {
    window.location.hash = "#/market";
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("renders the public market from backend responses", async () => {
    stubBackend();
    render(<App />);

    await waitFor(() => expect(screen.getByText("数据库原理教材")).toBeInTheDocument());
    expect(screen.getByText("1")).toBeInTheDocument();
    expect(screen.getByText("件在售商品")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "登录" })).toBeInTheDocument();
  });

  it("guards seller publishing behind login", async () => {
    stubBackend();
    render(<App />);
    fireEvent.click(screen.getByRole("button", { name: "发布" }));
    await waitFor(() => expect(screen.getByText("登录账号")).toBeInTheDocument());
    expect(screen.getByText("请先登录后继续")).toBeInTheDocument();
  });

  it("sends authenticated trade-ineligible users to campus verification before publishing", async () => {
    stubBackend(registeredUser);
    render(<App />);
    await waitFor(() => expect(screen.getByText("普通同学")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "发布" }));
    await waitFor(() => expect(screen.getByText("校园认证")).toBeInTheDocument());
    expect(screen.getByText("完成校园认证后才能发布商品")).toBeInTheDocument();
  });

  it("redirects trade-ineligible users who directly open the seller workspace", async () => {
    window.location.hash = "#/seller";
    stubBackend(registeredUser);
    render(<App />);
    await waitFor(() => expect(screen.getByText("校园认证")).toBeInTheDocument());
    expect(screen.queryByText("发布商品")).not.toBeInTheDocument();
    expect(screen.getByText("完成校园认证后才能发布商品")).toBeInTheDocument();
  });

  it("shows an orders workspace for authenticated trade users", async () => {
    stubBackend(verifiedBuyer);
    render(<App />);

    await waitFor(() => expect(screen.getByText("买家同学")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "订单" }));

    await waitFor(() => expect(screen.getByText("我的订单")).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "我买到的" })).toBeInTheDocument();
    expect(screen.getByText("机械键盘 87 键茶轴")).toBeInTheDocument();
    expect(screen.getByText("我是买家")).toBeInTheDocument();
  });

  it("creates an order from goods detail and opens the order detail", async () => {
    stubBackend(verifiedBuyer);
    render(<App />);

    await waitFor(() => expect(screen.getByText("数据库原理教材")).toBeInTheDocument());
    fireEvent.click(screen.getByText("数据库原理教材"));
    await waitFor(() => expect(screen.getByRole("button", { name: "立即下单" })).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: "立即下单" }));
    fireEvent.change(screen.getByLabelText("交易地点"), { target: { value: "1" } });
    fireEvent.change(screen.getByLabelText("见面时间"), { target: { value: "2026-06-10T18:30" } });
    fireEvent.change(screen.getByLabelText("给卖家的备注"), { target: { value: "图书馆门口见" } });
    fireEvent.click(screen.getByRole("button", { name: "提交订单" }));

    await waitFor(() => expect(window.location.hash).toBe("#/orders/77"));
    expect(screen.getAllByText("等待卖家确认").length).toBeGreaterThan(0);
  });

  it("lets sellers confirm pending orders from the order detail action panel", async () => {
    window.location.hash = "#/orders/77";
    stubBackend(verifiedSeller);
    render(<App />);

    await waitFor(() => expect(screen.getAllByText("等待卖家确认").length).toBeGreaterThan(0));
    fireEvent.click(screen.getByRole("button", { name: "确认接单" }));

    await waitFor(() => expect(screen.getByText("等待买家支付")).toBeInTheDocument());
    expect(screen.queryByRole("button", { name: "确认接单" })).not.toBeInTheDocument();
  });

  it("opens the N3 governance center with credit and admin queues", async () => {
    stubBackend(contentAdmin);
    render(<App />);

    await waitFor(() => expect(screen.getByText("内容管理员")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "治理" }));

    await waitFor(() => expect(screen.getByText("治理与信用中心")).toBeInTheDocument());
    expect(screen.getByText("信用等级")).toBeInTheDocument();
    expect(screen.getByText("我的举报")).toBeInTheDocument();
    expect(screen.getByText("管理员待办")).toBeInTheDocument();
    expect(screen.getAllByText("商品描述与实际成色不一致，需要管理员核实。").length).toBeGreaterThan(0);
  });
});

function stubBackend(currentUser?: CurrentUser) {
  vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    const path = url.replace(/^http:\/\/localhost/, "");
    if (url === "/api/auth/me") {
      return currentUser
        ? response(currentUser)
        : response({ code: "AUTH_REQUIRED", message: "请先登录", details: {} }, 401);
    }
    if (url === "/api/categories") return response([{ id: 1, code: "BOOKS", name: "教材资料", parentId: null }]);
    if (url === "/api/tags") return response([{ id: 1, name: "可议价", description: "接受合理议价" }]);
    if (url === "/api/campus-places") return response([{ id: 1, campus: "主校区", name: "图书馆正门", detail: "入口处" }]);
    if (url === "/api/verifications/me") {
      return response({ id: null, realName: null, studentNo: null, department: null, campusEmail: null, score: 0, status: "NONE", factors: [] });
    }
    if (url === "/api/goods/10") {
      return response({
        id: 10,
        title: "数据库原理教材",
        description: "少量笔记，适合复习。",
        conditionLevel: "LIGHTLY_USED",
        listPrice: "28.00",
        status: "ON_SALE",
        auditStatus: "APPROVED",
        seller: { id: 11, nickname: "小林同学" },
        category: { id: 1, code: "BOOKS", name: "教材资料" },
        primaryImage: null,
        publishedAt: "2026-05-25T00:00:00Z"
      });
    }
    if (url === "/api/orders?page=1&pageSize=20") return response({ items: [pendingOrder()], page: 1, pageSize: 20, total: 1 });
    if (url === "/api/orders/77") return response(pendingOrder());
    if (url === "/api/orders/77/reviews") return response([]);
    if (url === "/api/orders/77/payment") return response({
      id: 501,
      paymentNo: "PAY202606050001",
      orderId: 77,
      amount: "129.00",
      status: "PENDING",
      provider: "SIMULATED_ESCROW",
      createdAt: "2026-06-05T10:00:00Z",
      paidAt: null,
      closedAt: null
    });
    if (url === "/api/orders/77/seller-confirm") return response({ ...pendingOrder(), status: "PENDING_PAYMENT" });
    if (url === "/api/n3/governance-overview") {
      return response({
        reports: [{
          id: 39,
          reporter: { id: 31, nickname: "买家同学" },
          targetType: "GOODS",
          targetId: 10,
          reasonType: "FAKE_GOODS",
          description: "商品描述与实际成色不一致，需要管理员核实。",
          status: "PENDING",
          priority: "NORMAL",
          handledByAdminId: null,
          handledAt: null,
          handlingNote: null,
          evidenceFileIds: [],
          createdAt: "2026-06-05T11:20:00Z"
        }],
        appeals: [],
        refunds: [],
        favorites: [],
        follows: [],
        credit: {
          userId: currentUser?.id ?? 1,
          fulfillmentCount: 1,
          onTimeMeetupCount: 1,
          positiveReviewCount: 0,
          negativeEventCount: 0,
          publicTags: ["有完成交易记录", "暂无有效处罚"],
          internalScore: 82,
          internalLevel: "B",
          recentRecords: [],
          updatedAt: "2026-06-05T12:00:00Z"
        },
        adminQueue: currentUser?.roles.includes("CONTENT_ADMIN") ? {
          pendingReports: [{
            id: 39,
            reporter: { id: 31, nickname: "买家同学" },
            targetType: "GOODS",
            targetId: 10,
            reasonType: "FAKE_GOODS",
            description: "商品描述与实际成色不一致，需要管理员核实。",
            status: "PENDING",
            priority: "NORMAL",
            handledByAdminId: null,
            handledAt: null,
            handlingNote: null,
            evidenceFileIds: [],
            createdAt: "2026-06-05T11:20:00Z"
          }],
          pendingAppeals: [],
          pendingRefunds: [],
          activePenalties: []
        } : null
      });
    }
    if (url === "/api/orders" || path === "/api/orders") return response(pendingOrder());
    if (url.startsWith("/api/goods?")) {
      return response({
        items: [{
          id: 10,
          title: "数据库原理教材",
          description: "少量笔记，适合复习。",
          conditionLevel: "LIGHTLY_USED",
          listPrice: "28.00",
          status: "ON_SALE",
          auditStatus: "APPROVED",
          seller: { id: 3, nickname: "晨晨" },
          category: { id: 1, code: "BOOKS", name: "教材资料" },
          primaryImage: null,
          publishedAt: "2026-05-25T00:00:00Z"
        }],
        page: 1,
        pageSize: 20,
        total: 1
      });
    }
    return response({ code: "NOT_FOUND", message: "未找到", details: {} }, 404);
  }));
}

function pendingOrder() {
  return {
    id: 77,
    orderNo: "O202606050001",
    goodsId: 10,
    goodsTitle: "机械键盘 87 键茶轴",
    primaryImageFileId: null,
    buyer: { id: 31, nickname: "买家同学" },
    seller: { id: 11, nickname: "小林同学" },
    frozenAmount: "129.00",
    status: "PENDING_SELLER_CONFIRM",
    tradePlaceId: 1,
    tradePlaceName: "图书馆正门",
    tradePlaceDetail: "入口处",
    meetupTime: "2026-06-10T18:30:00Z",
    buyerNote: "图书馆门口见",
    createdAt: "2026-06-05T10:00:00Z",
    updatedAt: "2026-06-05T10:00:00Z",
    closedAt: null
  };
}

function response(body: unknown, status = 200) {
  return Promise.resolve({
    ok: status >= 200 && status < 300,
    status,
    json: async () => body
  } as Response);
}
