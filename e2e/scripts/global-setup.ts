import { backendKind, resetStubData, startBackend, startFrontend, stopAll } from "./servers";

export default async function globalSetup() {
  await stopAll();
  if (backendKind() === "stub") {
    resetStubData();
  }
  await startBackend();
  await startFrontend();
}
