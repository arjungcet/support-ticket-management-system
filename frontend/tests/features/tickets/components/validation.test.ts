import { textRules } from "@/features/tickets/components/validation";

describe("textRules (client-side hints for api-contract §1.1, §2.3)", () => {
  const singleLine = textRules(10, true).validate;
  const multiLine = textRules(10, true, true).validate;

  it("flags missing, blank and too-long values", () => {
    expect(singleLine("")).toBe("This field is required.");
    expect(singleLine("   ")).toBe("This field can't be blank.");
    expect(singleLine("x".repeat(11))).toBe("Must be at most 10 characters.");
    expect(singleLine("  ok  ")).toBe(true);
  });

  it("rejects control characters in single-line fields, including tab and line breaks", () => {
    for (const value of ["a\u0000b", "a\u0007b", "a\tb", "a\nb", "a\u001fb"]) {
      expect(singleLine(value)).toBe("Must not contain control characters.");
    }
  });

  it("allows tab and line breaks in multi-line fields but still rejects other control characters", () => {
    expect(multiLine("a\tb\nc\r\nd")).toBe(true);
    expect(multiLine("a\u0000b")).toBe("Must not contain control characters.");
  });
});
