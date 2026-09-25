/**
 * Client-side hints mirroring the contract limits (api-contract §2.3) for fast feedback only. The backend remains the
 * source of truth; its errors are shown whenever it disagrees.
 */
export function textRules(maxLength: number, required: boolean) {
  return {
    validate: (value: string) => {
      const trimmed = value.trim();
      if (required && trimmed.length === 0) {
        return value.length === 0 ? "This field is required." : "This field can't be blank.";
      }
      if (trimmed.length > maxLength) {
        return `Must be at most ${maxLength} characters.`;
      }
      return true;
    },
  };
}
