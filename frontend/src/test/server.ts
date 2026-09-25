import { setupServer } from "msw/node";

/** Network-level API mock. Handlers are registered per test with server.use(...). */
export const server = setupServer();
