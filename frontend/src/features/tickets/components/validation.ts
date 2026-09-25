/**
 * Client-side hints mirroring the contract limits (api-contract §2.3) for fast feedback only. The backend remains the
 * source of truth; its errors are shown whenever it disagrees.
 */
/** Control characters (U+0000–U+001F); multi-line fields may contain tab, line feed and carriage return (§1.1). */
const CONTROL_CHARACTERS = /[\u0000-\u001f]/;
const CONTROL_CHARACTERS_EXCEPT_LINE_BREAKS = /[\u0000-\u0008\u000b\u000c\u000e-\u001f]/;

export function textRules(maxLength: number, required: boolean, multiLine = false) {
  const forbidden = multiLine ? CONTROL_CHARACTERS_EXCEPT_LINE_BREAKS : CONTROL_CHARACTERS;
  return {
    validate: (value: string) => {
      const trimmed = value.trim();
      if (required && trimmed.length === 0) {
        return value.length === 0 ? "This field is required." : "This field can't be blank.";
      }
      if (forbidden.test(trimmed)) {
        return "Must not contain control characters.";
      }
      if (trimmed.length > maxLength) {
        return `Must be at most ${maxLength} characters.`;
      }
      return true;
    },
  };
}
