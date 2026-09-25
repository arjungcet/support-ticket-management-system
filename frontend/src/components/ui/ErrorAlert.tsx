import { isApiError, userMessage } from "@/lib/api/errors";

interface ErrorAlertProps {
  error: unknown;
  /** Extra messages (e.g. object-level validation errors) shown under the main message. */
  details?: string[];
  onRetry?: () => void;
  onReload?: () => void;
}

/** Shows an error by its `code`; never renders raw response text. 5xx/network errors include the correlation id. */
export function ErrorAlert({ error, details = [], onRetry, onReload }: ErrorAlertProps) {
  const apiError = isApiError(error) ? error : null;
  const showReference = apiError?.isServerOrNetwork && apiError.correlationId;
  return (
    <div role="alert" className="alert">
      <p>{userMessage(error)}</p>
      {details.map((detail) => (
        <p key={detail}>{detail}</p>
      ))}
      {showReference && <p className="muted">Reference: {apiError.correlationId}</p>}
      {onReload && (
        <button type="button" onClick={onReload}>
          Reload ticket
        </button>
      )}
      {onRetry && (
        <button type="button" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}
