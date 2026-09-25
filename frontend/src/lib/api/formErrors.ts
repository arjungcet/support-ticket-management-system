import type { FieldValues, Path, UseFormSetError } from "react-hook-form";
import { fieldMessage, isApiError } from "./errors";

/**
 * Puts each `errors[]` entry from a 400 VALIDATION_FAILED next to its form field and returns the messages that belong
 * to no field (object-level errors such as NO_CHANGES_REQUESTED, or fields this form doesn't show).
 */
export function applyServerFieldErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  formFields: readonly Path<T>[],
): string[] {
  if (!isApiError(error) || error.code !== "VALIDATION_FAILED") {
    return [];
  }
  const unplaced: string[] = [];
  for (const fieldError of error.fieldErrors) {
    const field = fieldError.field as Path<T> | null;
    if (fieldError.location === "body" && field && formFields.includes(field)) {
      setError(field, { type: fieldError.code, message: fieldMessage(fieldError) });
    } else {
      unplaced.push(fieldMessage(fieldError));
    }
  }
  return unplaced;
}
