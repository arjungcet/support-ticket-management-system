import { stopAll } from "./servers";

export default async function globalTeardown() {
  await stopAll();
}
