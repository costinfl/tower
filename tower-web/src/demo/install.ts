import { ApiFailure, handle } from "./server";

// Installs the demo backend as a `fetch` shim.
//
// Shimming fetch rather than swapping the API client is deliberate: every page,
// component, loading state and error path then runs exactly the code that runs
// against the real backend. A separate demo client would demonstrate a
// different program from the one that ships.
export function installDemoBackend(): void {
  const real = window.fetch.bind(window);

  window.fetch = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const url = typeof input === "string" ? input : input instanceof URL ? input.pathname : input.url;
    const pathname = url.startsWith("http") ? new URL(url).pathname : url;

    // Anything that is not the Tower API still goes to the network.
    if (!pathname.startsWith("/api/")) return real(input as RequestInfo, init);

    const method = (init?.method ?? "GET").toUpperCase();
    const body = init?.body ? (JSON.parse(String(init.body)) as Record<string, unknown>) : null;

    // A small delay so loading states are visible rather than skipped past,
    // which is part of the look and feel this demo exists to show.
    await new Promise((r) => setTimeout(r, 90));

    try {
      const result = handle(pathname, method, body);

      if (result === null) return new Response(null, { status: 204 });

      if (typeof result === "string") {
        return new Response(result, { status: 200, headers: { "Content-Type": "text/plain" } });
      }
      return new Response(JSON.stringify(result), {
        status: method === "POST" && !pathname.includes("/import") ? 201 : 200,
        headers: { "Content-Type": "application/json" },
      });
    } catch (e) {
      const failure = e instanceof ApiFailure ? e : new ApiFailure(500, String(e));
      // The same error envelope the real API returns, so ErrorNote and the 409
      // handling in the pages behave identically.
      return new Response(
        JSON.stringify({
          timestamp: new Date().toISOString(),
          status: failure.status,
          message: failure.message,
          path: pathname,
        }),
        { status: failure.status, headers: { "Content-Type": "application/json" } },
      );
    }
  };
}

export const DEMO_MODE = import.meta.env.VITE_DEMO === "true";
