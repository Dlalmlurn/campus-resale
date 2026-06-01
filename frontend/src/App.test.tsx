import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";

describe("App", () => {
  beforeEach(() => {
    window.location.hash = "#/market";
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url === "/api/auth/me") {
        return response({ code: "AUTH_REQUIRED", message: "请先登录", details: {} }, 401);
      }
      if (url === "/api/categories") return response([{ id: 1, code: "BOOKS", name: "教材资料", parentId: null }]);
      if (url === "/api/tags") return response([{ id: 1, name: "可议价", description: "接受合理议价" }]);
      if (url === "/api/campus-places") return response([{ id: 1, campus: "主校区", name: "图书馆正门", detail: "入口处" }]);
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
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("renders the public market from backend responses", async () => {
    render(<App />);

    await waitFor(() => expect(screen.getByText("数据库原理教材")).toBeInTheDocument());
    expect(screen.getByText("1")).toBeInTheDocument();
    expect(screen.getByText("件在售商品")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "登录" })).toBeInTheDocument();
  });

  it("guards seller publishing behind login", async () => {
    render(<App />);
    fireEvent.click(screen.getByRole("button", { name: "发布" }));
    await waitFor(() => expect(screen.getByText("登录账号")).toBeInTheDocument());
    expect(screen.getByText("请先登录后继续")).toBeInTheDocument();
  });
});

function response(body: unknown, status = 200) {
  return Promise.resolve({
    ok: status >= 200 && status < 300,
    status,
    json: async () => body
  } as Response);
}
