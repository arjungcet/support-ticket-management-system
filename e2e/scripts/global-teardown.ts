import { stopAll, stopDatabase } from "./servers";

export default async function globalTeardown() {
  await stopAll();
  stopDatabase();
}
