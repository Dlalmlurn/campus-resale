// 文件功能：封装前端统一 API 请求、错误对象和查询字符串生成逻辑。
export interface ApiErrorPayload {
  code: string;
  message: string;
  details: Record<string, unknown>;
  traceId?: string | null;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: Record<string, unknown>;

  constructor(status: number, payload: ApiErrorPayload) {
    super(payload.message);
    this.name = "ApiError";
    this.status = status;
    this.code = payload.code;
    this.details = payload.details ?? {};
  }
}

// 统一携带同源 Cookie 调用后端，并把后端标准错误响应转换为 ApiError。
export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (typeof init.body === "string" && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  headers.set("Accept", "application/json");

  const response = await fetch(path, {
    ...init,
    headers,
    credentials: "same-origin"
  });

  if (!response.ok) {
    let payload: ApiErrorPayload = {
      code: "REQUEST_FAILED",
      message: `请求失败（${response.status}）`,
      details: {}
    };
    try {
      payload = (await response.json()) as ApiErrorPayload;
    } catch {
      // Keep the fallback when an upstream proxy returns a non-JSON error page.
    }
    throw new ApiError(response.status, payload);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

// 过滤空值后生成 URL 查询串，避免把未选择的筛选项传给后端。
export function queryString(params: Record<string, string | number | null | undefined>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== "") {
      search.set(key, String(value));
    }
  });
  const value = search.toString();
  return value ? `?${value}` : "";
}
