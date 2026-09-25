"use client";

import { useState } from "react";
import { isApiError } from "@/lib/api/errors";

/**
 * The last failure of an action on a ticket. A version-conflict failure ("changed by someone else — reload")
 * expires once the ticket has a different version, i.e. it has been reloaded: its advice no longer applies.
 * Other failures (validation, rejected transition) stay until the next action.
 */
export function useActionFailure<T>(
  ticketVersion: number,
  errorOf: (failure: T) => unknown,
): [T | null, (failure: T | null) => void] {
  const [state, setState] = useState<{ failure: T; version: number } | null>(null);
  const error = state ? errorOf(state.failure) : null;
  const expired =
    state !== null &&
    state.version !== ticketVersion &&
    isApiError(error) &&
    error.code === "TICKET_CONCURRENT_MODIFICATION";
  const setFailure = (failure: T | null) =>
    setState(failure === null ? null : { failure, version: ticketVersion });
  return [state && !expired ? state.failure : null, setFailure];
}
