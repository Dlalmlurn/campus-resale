import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";

describe("App", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          status: "UP",
          service: "campus-resale-api-test",
          checkedAt: "2026-05-25T00:00:00Z",
          modules: [
            { code: "M01", name: "identity", packageName: "com.campusresale.identity", status: "PLANNED" }
          ]
        })
      })
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("renders workspace entries and health status", async () => {
    render(<App />);

    expect(screen.getByText("学生前台")).toBeInTheDocument();
    expect(screen.getByText("卖家工作台")).toBeInTheDocument();
    expect(screen.getByText("管理后台")).toBeInTheDocument();

    await waitFor(() => expect(screen.getByText("campus-resale-api-test")).toBeInTheDocument());
    expect(screen.getByText("M01")).toBeInTheDocument();
  });
});
