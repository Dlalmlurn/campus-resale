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
