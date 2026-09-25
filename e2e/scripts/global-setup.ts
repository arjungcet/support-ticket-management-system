import { backendKind, resetStubData, startBackend, startDatabase, startFrontend, stopAll } from "./servers";

export default async function globalSetup() {
  await stopAll();
  if (backendKind() === "stub") {
    resetStubData();
  }
  await startDatabase();
  await startBackend();
  await startFrontend();
}
