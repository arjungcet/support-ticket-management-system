import type { ReactElement } from "react";
import { cloneElement } from "react";

interface FieldProps {
  id: string;
  label: string;
  error?: string;
  hint?: string;
  /** A single input/textarea/select; it receives id, aria-invalid and aria-describedby. */
  children: ReactElement<Record<string, unknown>>;
}

/** Labelled form control with an error message linked through aria-describedby. */
export function Field({ id, label, error, hint, children }: FieldProps) {
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(" ") || undefined;
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {cloneElement(children, { id, "aria-invalid": error ? true : undefined, "aria-describedby": describedBy })}
      {hint && (
        <span id={hintId} className="muted">
          {hint}
        </span>
      )}
      {error && (
        <span id={errorId} className="field-error">
          {error}
        </span>
      )}
    </div>
  );
}
