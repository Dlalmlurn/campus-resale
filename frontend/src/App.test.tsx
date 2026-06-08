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
  roles: ["REGISTERED_USER", "VERIFIED_STUDENT", "CONTENT_ADMIN"],
  verificationStatus: "APPROVED",
  canTrade: true
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

  it("guards governance behind login", async () => {
    stubBackend();
    render(<App />);
    fireEvent.click(screen.getByRole("button", { name: "治理" }));
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

  it("lets users switch between active and archived conversations", async () => {
    stubBackend(verifiedBuyer);
    render(<App />);

    await waitFor(() => expect(screen.getByText("买家同学")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "消息" }));

    await waitFor(() => expect(screen.getByText("我的消息")).toBeInTheDocument());
    expect(screen.getByText("机械键盘 87 键茶轴")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "已归档" }));

    await waitFor(() => expect(screen.getByText("数据库原理教材")).toBeInTheDocument());
    expect(screen.getAllByText("已归档").length).toBeGreaterThan(0);
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

  it("shows the N2 admin acceptance workspace with demo navigation", async () => {
    window.location.hash = "#/admin";
    stubBackend(contentAdmin);
    render(<App />);

    await waitFor(() => expect(screen.getByText("后台验收闭环")).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "后台" })).toBeInTheDocument();
    expect(screen.getByText("演示导航")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "查看审计日志" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "通知列表" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "查看审计日志" }));
    await waitFor(() => expect(screen.getByText("操作日志")).toBeInTheDocument());
  });

  it("loads notifications and can mark all as read", async () => {
    window.location.hash = "#/notifications";
    stubBackend(verifiedBuyer);
    render(<App />);

    await waitFor(() => expect(screen.getByText("站内通知")).toBeInTheDocument());
    expect(screen.getByText("订单已进入托管")).toBeInTheDocument();
    expect(screen.getByText("1 条未读")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "全部标为已读" }));

    await waitFor(() => expect(screen.getByText("0 条未读")).toBeInTheDocument());
  });

  it("opens the polished N3 governance workspace from the main navigation", async () => {
    stubBackend(contentAdmin);
    render(<App />);

    await waitFor(() => expect(screen.getByText("内容管理员")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "治理" }));

    await waitFor(() => expect(screen.getByText("治理与信用中心")).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "举报处理" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "申诉复核" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "处罚处理" })).toBeInTheDocument();
    expect(screen.getByText("信用画像")).toBeInTheDocument();
    expect(screen.getByText("商品描述与实际成色不一致，需要管理员核实。")).toBeInTheDocument();
  });

  it("shows AI publishing assistance and discovery reasons", async () => {
    stubBackend(verifiedSeller);
    render(<App />);

    await waitFor(() => expect(screen.getByText("数据库原理教材")).toBeInTheDocument());
    expect(screen.getByText("推荐理由：教材资料匹配你的浏览偏好")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "发布" }));
    await waitFor(() => expect(screen.getByText("AI 发布辅助")).toBeInTheDocument());
    fireEvent.change(screen.getByLabelText("商品标题"), { target: { value: "旧书" } });
    fireEvent.change(screen.getByLabelText("商品描述"), { target: { value: "数据库课程复习资料，包含笔记。" } });
    fireEvent.click(screen.getByRole("button", { name: "生成优化建议" }));

    await waitFor(() => expect(screen.getByText("数据库课程复习资料")).toBeInTheDocument());
    expect(screen.getByText("AI 仅提供辅助建议，不会自动审核、下架或处罚。")).toBeInTheDocument();
  });

  it("lets buyers favorite goods and follow sellers from discovery cards", async () => {
    stubBackend(verifiedBuyer);
    render(<App />);

    await waitFor(() => expect(screen.getByText("数据库原理教材")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "收藏 数据库原理教材" }));
    await waitFor(() => expect(screen.getByText("已收藏商品")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: "关注 晨晨" }));

    await waitFor(() => expect(screen.getByText("已关注卖家")).toBeInTheDocument());
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
    if (url === "/api/conversations") return response([conversationSummary(false)]);
    if (url === "/api/conversations?archivedOnly=true") return response([conversationSummary(true)]);
    if (url === "/api/admin/stats/dashboard") return response(adminDashboard());
    if (url === "/api/admin/stats/order-trend") return response([
      { statDate: "2026-06-05", totalCreated: 3, completedCount: 1, cancelledCount: 0 }
    ]);
    if (url.startsWith("/api/admin/operation-logs")) return response({
      items: [{
        id: 1,
        adminId: 1,
        action: "GOODS_APPROVE",
        targetType: "GOODS",
        targetId: 10,
        ipAddress: "127.0.0.1",
        userAgent: "vitest",
        requestPath: "/api/admin/goods/10/approve",
        httpMethod: "POST",
        result: "SUCCESS",
        operatorType: "ADMIN",
        createdAt: "2026-06-05T10:00:00Z"
      }],
      page: 1,
      pageSize: 20,
      total: 1
    });
    if (url.startsWith("/api/admin/sensitive-access-logs")) return response({ items: [], page: 1, pageSize: 20, total: 0 });
    if (url.startsWith("/api/admin/verifications")) return response({ items: [], page: 1, pageSize: 20, total: 0 });
    if (url.startsWith("/api/admin/goods")) return response({ items: [], page: 1, pageSize: 20, total: 0 });
    if (url === "/api/notifications/unread-count") return response({ unreadCount: 1 });
    if (url.startsWith("/api/notifications?")) return response({
      items: [{
        id: 9,
        type: "PAYMENT_ESCROWED",
        title: "订单已进入托管",
        content: "订单 O202606050001 已支付成功，等待线下面交。",
        relatedType: "ORDER",
        relatedId: 77,
        read: false,
        readAt: null,
        createdAt: "2026-06-05T10:05:00Z"
      }],
      page: 1,
      pageSize: 20,
      total: 1
    });
    if (url === "/api/notifications/read-all") return response({ updatedCount: 1 });
    if (url === "/api/n3/favorites/10") return response({ active: true });
    if (url === "/api/n3/follows/3") return response({ active: true });
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
    if (url === "/api/intelligence/goods-assist") return response({
      optimizedTitle: "数据库课程复习资料",
      optimizedDescription: "适合数据库原理期末复习，包含重点笔记，建议补充版本和新旧程度。",
      suggestedCategoryCode: "BOOKS",
      suggestedTags: ["教材资料", "期末复习"],
      riskLevel: "LOW",
      riskReasons: ["未发现明显禁售词"],
      recommendationReason: "根据标题和描述判断更适合教材资料分类",
      auditReminder: "AI 仅提供辅助建议，不会自动审核、下架或处罚。",
      assistSource: "RULES",
      requestId: 501
    });
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
          recentRecords: [{ id: 1, sourceType: "ORDER", sourceId: 77, reason: "完成交易", internalDeltaValue: 2, publicLabel: "履约记录", createdAt: "2026-06-05T12:00:00Z" }],
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
          pendingAppeals: [{
            id: 41,
            reportId: 39,
            appellant: { id: 11, nickname: "小林同学" },
            description: "商品成色说明已补充，申请复核。",
            status: "PENDING_REVIEW",
            reviewedByAdminId: null,
            reviewedAt: null,
            reviewNote: null,
            evidenceFileIds: [],
            createdAt: "2026-06-05T12:00:00Z"
          }],
          pendingRefunds: [],
          activePenalties: [{
            id: 5,
            user: { id: 11, nickname: "小林同学" },
            reportId: 39,
            appealId: null,
            penaltyType: "TRADE_RESTRICT",
            reason: "举报成立后临时限制交易",
            status: "ACTIVE",
            createdByAdminId: 1,
            liftedByAdminId: null,
            liftedAt: null,
            createdAt: "2026-06-05T12:30:00Z"
          }]
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
          publishedAt: "2026-05-25T00:00:00Z",
          recommendationReason: "教材资料匹配你的浏览偏好"
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

function conversationSummary(archived: boolean) {
  return {
    id: archived ? 81 : 80,
    goodsId: archived ? 10 : 12,
    goodsTitle: archived ? "数据库原理教材" : "机械键盘 87 键茶轴",
    primaryImageFileId: null,
    buyer: { id: 31, nickname: "买家同学" },
    seller: { id: 11, nickname: "小林同学" },
    status: "NORMAL",
    lastMessageId: archived ? 91 : 90,
    lastMessageText: archived ? "已归档的议价记录" : "今晚可以自取",
    lastMessageAt: "2026-06-05T10:10:00Z",
    unreadCount: archived ? 0 : 2,
    archived,
    createdAt: "2026-06-05T10:00:00Z",
    updatedAt: "2026-06-05T10:10:00Z"
  };
}

function response(body: unknown, status = 200) {
  return Promise.resolve({
    ok: status >= 200 && status < 300,
    status,
    json: async () => body
  } as Response);
}

function adminDashboard() {
  return {
    orders: {
      totalOrders: 8,
      pendingSellerConfirm: 1,
      pendingPayment: 1,
      paidPendingMeetup: 2,
      completedPendingSettlement: 1,
      completed: 3,
      cancelled: 0,
      closed: 0,
      disputeProcessing: 0,
      refundProcessing: 0,
      activeFrozenAmount: "258.00",
      completedAmount: "386.00"
    },
    payments: {
      totalPayments: 5,
      pending: 0,
      processing: 0,
      escrowed: 2,
      failed: 0,
      closed: 0,
      escrowedAmount: "258.00",
      totalProcessedAmount: "644.00"
    },
    settlements: {
      totalSettlements: 3,
      pending: 1,
      processing: 0,
      settled: 2,
      failed: 0,
      closed: 0,
      totalSettledAmount: "386.00",
      pendingSettlementAmount: "129.00"
    },
    goods: {
      totalGoods: 12,
      draft: 1,
      pendingReview: 2,
      onSale: 8,
      reserved: 1,
      sold: 2,
      offShelf: 0,
      deleted: 0,
      auditPending: 2
    },
    reviews: {
      totalReviews: 4,
      submitted: 4,
      visible: 4,
      hidden: 0,
      excluded: 0,
      avgRating: 4.8,
      fiveStar: 3,
      fourStar: 1,
      threeStar: 0,
      lowRating: 0
    },
    users: {
      totalUsers: 24,
      activeUsers: 21,
      lockedUsers: 0,
      disabledUsers: 0,
      newThisMonth: 6,
      newToday: 1
    },
    campusAuths: {
      totalVerifications: 15,
      draft: 1,
      accumulating: 1,
      pendingReview: 2,
      approved: 10,
      rejected: 1,
      invalid: 0
    },
    operationLogs: {
      totalLogs: 18,
      successCount: 16,
      failureCount: 1,
      partialCount: 1,
      todayCount: 3,
      thisMonthCount: 18
    }
  };
}
