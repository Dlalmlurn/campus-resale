const http = require("node:http");

const port = 8080;
let currentUser = null;
let nextFileId = 900;
let nextGoodsId = 30;
let nextOrderId = 77;
let nextCompletionId = 300;
let nextReviewId = 700;

const categories = [
  { id: 1, code: "DIGITAL", name: "数码设备" },
  { id: 2, code: "BOOKS", name: "教材书籍" },
  { id: 3, code: "LIFE", name: "生活用品" }
];

const tags = [
  { id: 1, name: "考研" },
  { id: 2, name: "宿舍好物" },
  { id: 3, name: "可小刀" }
];

const places = [
  { id: 1, campus: "南校区", name: "图书馆门口", detail: "正门台阶附近" },
  { id: 2, campus: "北校区", name: "食堂一楼", detail: "服务台旁" }
];

const goods = [
  {
    id: 1,
    title: "机械键盘 87 键茶轴",
    description: "自用键盘，按键回弹正常，附送数据线。宿舍学习和写代码都很顺手。",
    conditionLevel: "LIKE_NEW",
    listPrice: "129.00",
    status: "ON_SALE",
    auditStatus: "APPROVED",
    seller: { id: 11, nickname: "小林同学" },
    category: categories[0],
    primaryImage: { id: 101, url: "https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=900&q=80" },
    publishedAt: "2026-05-30T10:15:00"
  },
  {
    id: 2,
    title: "数据库系统概论 第六版",
    description: "课程使用教材，有少量重点标注，适合期末复习。",
    conditionLevel: "LIGHTLY_USED",
    listPrice: "24.00",
    status: "ON_SALE",
    auditStatus: "APPROVED",
    seller: { id: 12, nickname: "晨风" },
    category: categories[1],
    primaryImage: { id: 102, url: "https://images.unsplash.com/photo-1544947950-fa07a98d237f?auto=format&fit=crop&w=900&q=80" },
    publishedAt: "2026-05-29T08:20:00"
  },
  {
    id: 3,
    title: "桌面收纳架与台灯",
    description: "搬宿舍闲置，台灯亮度可调，收纳架无磕碰。",
    conditionLevel: "LIGHTLY_USED",
    listPrice: "46.00",
    status: "ON_SALE",
    auditStatus: "APPROVED",
    seller: { id: 13, nickname: "南区 3 栋" },
    category: categories[2],
    primaryImage: { id: 103, url: "https://images.unsplash.com/photo-1507473885765-e6ed057f782c?auto=format&fit=crop&w=900&q=80" },
    publishedAt: "2026-05-28T18:42:00"
  },
  {
    id: 4,
    title: "头戴式蓝牙耳机",
    description: "耳罩干净，续航正常，附带收纳袋。提交审核后等待管理员确认。",
    conditionLevel: "LIGHTLY_USED",
    listPrice: "86.00",
    status: "PENDING_REVIEW",
    auditStatus: "PENDING",
    seller: { id: 14, nickname: "北区小周" },
    category: categories[0],
    primaryImage: { id: 104, url: "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=80" },
    publishedAt: null
  }
];

let verification = {
  id: 18,
  realName: "示例同学",
  studentNo: "20230001",
  department: "计算机学院",
  campusEmail: "demo@campus.example.edu",
  score: 80,
  status: "PENDING_REVIEW",
  factors: [{ factorType: "STUDENT_CARD", status: "VERIFIED", scoreValue: 80, fileIds: [800] }]
};

const users = {
  content_admin: {
    id: 1,
    username: "content_admin",
    nickname: "内容管理员",
    roles: ["CONTENT_ADMIN"],
    verificationStatus: "NONE",
    canTrade: false
  },
  buyer_demo: {
    id: 31,
    username: "buyer_demo",
    nickname: "买家同学",
    roles: ["REGISTERED_USER", "VERIFIED_STUDENT"],
    verificationStatus: "APPROVED",
    canTrade: true
  },
  seller_demo: {
    id: 11,
    username: "seller_demo",
    nickname: "小林同学",
    roles: ["REGISTERED_USER", "VERIFIED_STUDENT"],
    verificationStatus: "APPROVED",
    canTrade: true
  },
  student_demo: {
    id: 21,
    username: "student_demo",
    nickname: "示例同学",
    roles: ["REGISTERED_USER", "VERIFIED_STUDENT"],
    verificationStatus: "APPROVED",
    canTrade: true
  }
};

const payments = new Map();
const completionRequests = new Map();
const reviews = new Map();
const orders = [{
  id: 76,
  orderNo: "O202606050000",
  goodsId: 1,
  goodsTitle: goods[0].title,
  primaryImageFileId: null,
  buyer: { id: 31, nickname: "买家同学" },
  seller: { id: 11, nickname: "小林同学" },
  frozenAmount: goods[0].listPrice,
  status: "PENDING_SELLER_CONFIRM",
  tradePlaceId: 1,
  tradePlaceName: places[0].name,
  tradePlaceDetail: places[0].detail,
  meetupTime: "2026-06-10T18:30:00",
  buyerNote: "图书馆门口见",
  createdAt: "2026-06-05T10:00:00",
  updatedAt: "2026-06-05T10:00:00",
  closedAt: null
}];

let notifications = [{
  id: 1,
  type: "PAYMENT_ESCROWED",
  title: "订单已进入托管",
  content: "订单 O202606050000 已支付成功，等待线下面交。",
  relatedType: "ORDER",
  relatedId: 76,
  read: false,
  readAt: null,
  createdAt: "2026-06-05T10:05:00"
}, {
  id: 2,
  type: "ORDER_CREATED",
  title: "你有新的订单",
  content: "买家已提交订单，请在后台演示前确认订单流转状态。",
  relatedType: "ORDER",
  relatedId: 76,
  read: true,
  readAt: "2026-06-05T10:10:00",
  createdAt: "2026-06-05T10:00:00"
}];

const operationLogs = [{
  id: 1,
  adminId: 1,
  action: "VERIFY_APPROVE",
  targetType: "CAMPUS_VERIFICATION",
  targetId: 18,
  ipAddress: "127.0.0.1",
  userAgent: "mock-api",
  requestPath: "/api/admin/verifications/18/approve",
  httpMethod: "POST",
  result: "SUCCESS",
  operatorType: "ADMIN",
  createdAt: "2026-06-05T10:20:00"
}, {
  id: 2,
  adminId: 1,
  action: "GOODS_APPROVE",
  targetType: "GOODS",
  targetId: 4,
  ipAddress: "127.0.0.1",
  userAgent: "mock-api",
  requestPath: "/api/admin/goods/4/approve",
  httpMethod: "POST",
  result: "SUCCESS",
  operatorType: "ADMIN",
  createdAt: "2026-06-05T10:18:00"
}];

const sensitiveAccessLogs = [{
  id: 1,
  adminId: 1,
  targetType: "CAMPUS_AUTH_MATERIAL",
  targetId: 800,
  reason: "审核校园认证材料",
  result: "ALLOWED",
  ipAddress: "127.0.0.1",
  createdAt: "2026-06-05T10:16:00"
}];

function send(response, status, payload) {
  response.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
  response.end(JSON.stringify(payload));
}

function readJson(request) {
  return new Promise((resolve) => {
    let body = "";
    request.on("data", (chunk) => { body += chunk; });
    request.on("end", () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch {
        resolve({});
      }
    });
  });
}

function page(items) {
  return { items, page: 0, pageSize: 20, total: items.length };
}

function dashboardStats() {
  const completed = orders.filter((order) => order.status.startsWith("COMPLETED")).length;
  const pendingSellerConfirm = orders.filter((order) => order.status === "PENDING_SELLER_CONFIRM").length;
  const pendingPayment = orders.filter((order) => order.status === "PENDING_PAYMENT").length;
  const paidPendingMeetup = orders.filter((order) => order.status === "PAID_PENDING_MEETUP").length;
  return {
    orders: {
      totalOrders: orders.length,
      pendingSellerConfirm,
      pendingPayment,
      paidPendingMeetup,
      completedPendingSettlement: orders.filter((order) => order.status === "COMPLETED_PENDING_SETTLEMENT").length,
      completed,
      cancelled: orders.filter((order) => order.status === "CANCELLED").length,
      closed: orders.filter((order) => order.closedAt).length,
      disputeProcessing: 0,
      refundProcessing: 0,
      activeFrozenAmount: "258.00",
      completedAmount: "129.00"
    },
    payments: {
      totalPayments: payments.size,
      pending: 0,
      processing: 0,
      escrowed: [...payments.values()].filter((payment) => payment.status === "ESCROWED").length,
      failed: 0,
      closed: 0,
      escrowedAmount: "129.00",
      totalProcessedAmount: "129.00"
    },
    settlements: {
      totalSettlements: 1,
      pending: 1,
      processing: 0,
      settled: 0,
      failed: 0,
      closed: 0,
      totalSettledAmount: "0.00",
      pendingSettlementAmount: "129.00"
    },
    goods: {
      totalGoods: goods.length,
      draft: goods.filter((item) => item.status === "DRAFT").length,
      pendingReview: goods.filter((item) => item.status === "PENDING_REVIEW").length,
      onSale: goods.filter((item) => item.status === "ON_SALE").length,
      reserved: goods.filter((item) => item.status === "RESERVED").length,
      sold: goods.filter((item) => item.status === "SOLD").length,
      offShelf: goods.filter((item) => item.status === "OFF_SHELF").length,
      deleted: 0,
      auditPending: goods.filter((item) => item.auditStatus === "PENDING").length
    },
    reviews: {
      totalReviews: [...reviews.values()].flat().length,
      submitted: [...reviews.values()].flat().length,
      visible: [...reviews.values()].flat().length,
      hidden: 0,
      excluded: 0,
      avgRating: 5,
      fiveStar: [...reviews.values()].flat().length,
      fourStar: 0,
      threeStar: 0,
      lowRating: 0
    },
    users: {
      totalUsers: Object.keys(users).length,
      activeUsers: Object.keys(users).length,
      lockedUsers: 0,
      disabledUsers: 0,
      newThisMonth: 4,
      newToday: 1
    },
    campusAuths: {
      totalVerifications: 1,
      draft: 0,
      accumulating: 0,
      pendingReview: verification.status === "PENDING_REVIEW" ? 1 : 0,
      approved: verification.status === "APPROVED" ? 1 : 0,
      rejected: verification.status === "REJECTED" ? 1 : 0,
      invalid: 0
    },
    operationLogs: {
      totalLogs: operationLogs.length,
      successCount: operationLogs.filter((log) => log.result === "SUCCESS").length,
      failureCount: 0,
      partialCount: 0,
      todayCount: operationLogs.length,
      thisMonthCount: operationLogs.length
    }
  };
}

function userFor(username) {
  return users[username] ?? registeredUserFor(username);
}

function registeredUserFor(username) {
  return {
    id: 21,
    username,
    nickname: "示例同学",
    roles: ["REGISTERED_USER"],
    verificationStatus: "NONE",
    canTrade: false
  };
}

const server = http.createServer(async (request, response) => {
  const url = new URL(request.url, `http://${request.headers.host}`);
  const path = url.pathname;

  if (request.method === "GET" && path === "/api/categories") return send(response, 200, categories);
  if (request.method === "GET" && path === "/api/tags") return send(response, 200, tags);
  if (request.method === "GET" && path === "/api/campus-places") return send(response, 200, places);
  if (request.method === "GET" && path === "/api/goods") return send(response, 200, page(goods.filter((item) => item.status === "ON_SALE" && item.auditStatus === "APPROVED")));
  if (request.method === "GET" && path === "/api/auth/me") return currentUser ? send(response, 200, currentUser) : send(response, 401, { code: "AUTH_REQUIRED", message: "请先登录" });

  if (request.method === "POST" && path === "/api/auth/login") {
    const body = await readJson(request);
    currentUser = userFor(body.username || "student_demo");
    return send(response, 200, currentUser);
  }

  if (request.method === "POST" && path === "/api/auth/register") {
    const body = await readJson(request);
    currentUser = registeredUserFor(body.username || "new_student");
    return send(response, 200, currentUser);
  }

  if (request.method === "POST" && path === "/api/auth/logout") {
    currentUser = null;
    return send(response, 200, { success: true });
  }

  if (request.method === "GET" && path === "/api/goods/mine") return send(response, 200, page(goods.slice(0, 1)));
  if (request.method === "GET" && /^\/api\/goods\/\d+$/.test(path)) return send(response, 200, goods.find((item) => item.id === Number(path.split("/").at(-1))) ?? goods[0]);

  if (request.method === "GET" && path === "/api/orders") {
    if (!currentUser) return send(response, 401, { code: "AUTH_REQUIRED", message: "请先登录" });
    const visible = orders.filter((order) => order.buyer.id === currentUser.id || order.seller.id === currentUser.id);
    return send(response, 200, page(visible));
  }

  if (request.method === "POST" && path === "/api/orders") {
    if (!currentUser?.canTrade) return send(response, 403, { code: "TRADE_ELIGIBILITY_REQUIRED", message: "完成校园认证后才能交易" });
    const body = await readJson(request);
    const item = goods.find((entry) => entry.id === Number(body.goodsId));
    if (!item) return send(response, 404, { code: "NOT_FOUND", message: "商品不存在" });
    if (item.seller.id === currentUser.id) return send(response, 409, { code: "SELF_TRADE_FORBIDDEN", message: "不能购买自己发布的商品" });
    const place = places.find((entry) => entry.id === Number(body.tradePlaceId));
    const order = {
      id: nextOrderId++,
      orderNo: `O${Date.now()}`,
      goodsId: item.id,
      goodsTitle: item.title,
      primaryImageFileId: item.primaryImage?.id ?? null,
      buyer: { id: currentUser.id, nickname: currentUser.nickname },
      seller: item.seller,
      frozenAmount: item.listPrice,
      status: "PENDING_SELLER_CONFIRM",
      tradePlaceId: place?.id ?? null,
      tradePlaceName: place?.name ?? null,
      tradePlaceDetail: body.tradePlaceDetail || place?.detail || null,
      meetupTime: body.meetupTime,
      buyerNote: body.note || null,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      closedAt: null
    };
    orders.unshift(order);
    return send(response, 200, order);
  }

  if (request.method === "GET" && /^\/api\/orders\/\d+$/.test(path)) {
    const order = findOrder(path);
    return order ? send(response, 200, order) : send(response, 404, { code: "NOT_FOUND", message: "订单不存在" });
  }

  if (request.method === "POST" && /^\/api\/orders\/\d+\/seller-confirm$/.test(path)) {
    const order = findOrder(path);
    if (!order) return send(response, 404, { code: "NOT_FOUND", message: "订单不存在" });
    order.status = "PENDING_PAYMENT";
    order.updatedAt = new Date().toISOString();
    return send(response, 200, order);
  }

  if (request.method === "POST" && /^\/api\/orders\/\d+\/seller-reject$/.test(path)) {
    const order = findOrder(path);
    if (!order) return send(response, 404, { code: "NOT_FOUND", message: "订单不存在" });
    order.status = "CLOSED";
    order.closedAt = new Date().toISOString();
    return send(response, 200, order);
  }

  if (request.method === "POST" && /^\/api\/orders\/\d+\/buyer-cancel$/.test(path)) {
    const order = findOrder(path);
    if (!order) return send(response, 404, { code: "NOT_FOUND", message: "订单不存在" });
    order.status = "CANCELLED";
    order.closedAt = new Date().toISOString();
    return send(response, 200, order);
  }

  if (request.method === "POST" && /^\/api\/orders\/\d+\/payments\/simulate$/.test(path)) {
    const order = findOrder(path);
    if (!order) return send(response, 404, { code: "NOT_FOUND", message: "订单不存在" });
    order.status = "PAID_PENDING_MEETUP";
    order.updatedAt = new Date().toISOString();
    const payment = {
      id: order.id + 500,
      paymentNo: `PAY${Date.now()}`,
      orderId: order.id,
      amount: order.frozenAmount,
      status: "ESCROWED",
      provider: "SIMULATED_ESCROW",
      createdAt: new Date().toISOString(),
      paidAt: new Date().toISOString(),
      closedAt: null
    };
    payments.set(order.id, payment);
    return send(response, 200, payment);
  }

  if (request.method === "GET" && /^\/api\/orders\/\d+\/payment$/.test(path)) {
    const order = findOrder(path);
    const payment = order ? payments.get(order.id) : null;
    return payment ? send(response, 200, payment) : send(response, 404, { code: "NOT_FOUND", message: "支付单不存在" });
  }

  if (request.method === "POST" && /^\/api\/orders\/\d+\/completion-requests$/.test(path)) {
    const order = findOrder(path);
    if (!order) return send(response, 404, { code: "NOT_FOUND", message: "订单不存在" });
    const now = new Date();
    const requestRecord = {
      id: nextCompletionId++,
      orderId: order.id,
      status: "PENDING",
      windowStartsAt: now.toISOString(),
      windowEndsAt: new Date(now.getTime() + 24 * 60 * 60 * 1000).toISOString(),
      confirmedAt: null,
      createdAt: now.toISOString()
    };
    completionRequests.set(order.id, requestRecord);
    return send(response, 200, requestRecord);
  }

  if (request.method === "POST" && /^\/api\/orders\/\d+\/completion-requests\/\d+\/confirm$/.test(path)) {
    const order = findOrder(path);
    if (!order) return send(response, 404, { code: "NOT_FOUND", message: "订单不存在" });
    const requestRecord = completionRequests.get(order.id);
    if (requestRecord) {
      requestRecord.status = "CONFIRMED";
      requestRecord.confirmedAt = new Date().toISOString();
    }
    order.status = "COMPLETED_PENDING_SETTLEMENT";
    order.updatedAt = new Date().toISOString();
    return send(response, 200, order);
  }

  if (request.method === "GET" && /^\/api\/orders\/\d+\/reviews$/.test(path)) {
    const order = findOrder(path);
    return send(response, 200, order ? reviews.get(order.id) ?? [] : []);
  }

  if (request.method === "POST" && /^\/api\/orders\/\d+\/reviews$/.test(path)) {
    const order = findOrder(path);
    if (!order) return send(response, 404, { code: "NOT_FOUND", message: "订单不存在" });
    const body = await readJson(request);
    const review = {
      id: nextReviewId++,
      orderId: order.id,
      reviewerId: currentUser?.id ?? order.buyer.id,
      reviewedUserId: order.seller.id,
      rating: Number(body.rating) || 5,
      content: body.content || "交易顺利，物品与描述一致。",
      status: "SUBMITTED",
      submittedAt: new Date().toISOString(),
      modifiedUntil: null,
      visibleAt: null
    };
    reviews.set(order.id, [review, ...(reviews.get(order.id) ?? [])]);
    return send(response, 200, review);
  }

  if (request.method === "POST" && path === "/api/goods/drafts") {
    const body = await readJson(request);
    const item = {
      id: nextGoodsId++,
      ...body,
      status: "DRAFT",
      auditStatus: "PENDING",
      seller: { id: currentUser?.id ?? 21, nickname: currentUser?.nickname ?? "示例同学" },
      category: categories.find((category) => category.id === Number(body.categoryId)) ?? categories[0],
      primaryImage: null,
      publishedAt: null
    };
    goods.unshift(item);
    return send(response, 200, item);
  }

  if (request.method === "POST" && /^\/api\/goods\/\d+\/submit$/.test(path)) {
    const item = goods.find((entry) => entry.id === Number(path.split("/")[3])) ?? goods[0];
    item.auditStatus = "PENDING";
    return send(response, 200, item);
  }

  if (request.method === "POST" && path === "/api/files") {
    return send(response, 200, {
      id: nextFileId++,
      originalName: "mock-upload.png",
      contentType: "image/png",
      byteSize: 245760,
      fileKind: "LOCAL_MOCK",
      visibilityScope: "PRIVATE",
      auditStatus: "PENDING",
      createdAt: new Date().toISOString()
    });
  }

  if (request.method === "GET" && path === "/api/verifications/me") return send(response, 200, verification);
  if (request.method === "PUT" && path === "/api/verifications/me") {
    const body = await readJson(request);
    verification = {
      ...verification,
      ...body,
      factors: [{ factorType: body.documentType, status: "VERIFIED", scoreValue: 80, fileIds: body.documentFileIds }]
    };
    return send(response, 200, verification);
  }
  if (request.method === "POST" && path === "/api/verifications/me/submit") {
    verification.status = "PENDING_REVIEW";
    return send(response, 200, verification);
  }

  if (request.method === "GET" && path === "/api/notifications") {
    const unreadOnly = url.searchParams.get("unreadOnly") === "true";
    const visible = unreadOnly ? notifications.filter((item) => !item.read) : notifications;
    return send(response, 200, page(visible));
  }
  if (request.method === "GET" && path === "/api/notifications/unread-count") {
    return send(response, 200, { unreadCount: notifications.filter((item) => !item.read).length });
  }
  if (request.method === "POST" && path === "/api/notifications/read-all") {
    let updatedCount = 0;
    notifications = notifications.map((item) => {
      if (item.read) return item;
      updatedCount += 1;
      return { ...item, read: true, readAt: new Date().toISOString() };
    });
    return send(response, 200, { updatedCount });
  }

  if (request.method === "GET" && path === "/api/admin/stats/dashboard") {
    return send(response, 200, dashboardStats());
  }
  if (request.method === "GET" && path === "/api/admin/stats/order-trend") {
    return send(response, 200, [
      { statDate: "2026-06-03", totalCreated: 1, completedCount: 0, cancelledCount: 0 },
      { statDate: "2026-06-04", totalCreated: 2, completedCount: 1, cancelledCount: 0 },
      { statDate: "2026-06-05", totalCreated: 3, completedCount: 1, cancelledCount: 0 }
    ]);
  }
  if (request.method === "GET" && path === "/api/admin/operation-logs") {
    const action = url.searchParams.get("action");
    const result = url.searchParams.get("result");
    return send(response, 200, page(operationLogs.filter((item) => (!action || item.action === action) && (!result || item.result === result))));
  }
  if (request.method === "GET" && path === "/api/admin/sensitive-access-logs") {
    const targetType = url.searchParams.get("targetType");
    return send(response, 200, page(sensitiveAccessLogs.filter((item) => !targetType || item.targetType === targetType)));
  }

  if (request.method === "GET" && path === "/api/admin/verifications") {
    const status = url.searchParams.get("status");
    return send(response, 200, page(!status || verification.status === status ? [verification] : []));
  }
  if (request.method === "GET" && path === "/api/admin/goods") {
    const status = url.searchParams.get("status");
    const auditStatus = url.searchParams.get("auditStatus");
    return send(response, 200, page(goods.filter((item) => (!status || item.status === status) && (!auditStatus || item.auditStatus === auditStatus))));
  }
  if (request.method === "POST" && /^\/api\/admin\/verifications\/\d+\/(approve|reject)$/.test(path)) {
    verification.status = path.endsWith("/approve") ? "APPROVED" : "REJECTED";
    return send(response, 200, verification);
  }
  if (request.method === "POST" && /^\/api\/admin\/goods\/\d+\/(approve|reject)$/.test(path)) {
    const item = goods.find((entry) => entry.id === Number(path.split("/")[4])) ?? goods[0];
    item.auditStatus = path.endsWith("/approve") ? "APPROVED" : "REJECTED";
    item.status = path.endsWith("/approve") ? "ON_SALE" : "DRAFT";
    return send(response, 200, item);
  }

  return send(response, 404, { message: `Mock API 未覆盖 ${request.method} ${path}` });
});

function findOrder(path) {
  const id = Number(path.split("/")[3]);
  return orders.find((order) => order.id === id);
}

server.listen(port, "127.0.0.1", () => {
  console.log(`Campus resale mock API running at http://127.0.0.1:${port}`);
});
