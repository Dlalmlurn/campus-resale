export interface ModuleDescriptor {
  code: string;
  name: string;
  packageName: string;
  status: string;
}

export interface HealthResponse {
  status: string;
  service: string;
  checkedAt: string;
  modules: ModuleDescriptor[];
}

export async function getHealth(): Promise<HealthResponse> {
  const response = await fetch("/api/health", {
    headers: {
      Accept: "application/json"
    }
  });

  if (!response.ok) {
    throw new Error(`Health check failed with ${response.status}`);
  }

  return response.json() as Promise<HealthResponse>;
}
