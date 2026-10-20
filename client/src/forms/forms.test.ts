import { normalizeCode } from "../components/CodeKeypad";
import { registerSchema } from "./registerSchema";
import { toRegisterRequest } from "./toRequest";

const base = { preferredName: "Test Person" };

test("a slip code from the keypad (4 characters) or typed in full is sent as HW-XXXX", () => {
  expect(registerSchema.parse({ ...base, slipCode: "7k3p" }).slipCode).toBe("HW-7K3P");
  expect(registerSchema.parse({ ...base, slipCode: " hw-7k3p " }).slipCode).toBe("HW-7K3P");
  expect(registerSchema.safeParse({ ...base, slipCode: "HW-0O1I" }).success).toBe(false);
});

test("codes that start with H and W are not cut short", () => {
  expect(normalizeCode("HWABC")).toBe("HWAB");
  expect(normalizeCode("HW7K3P")).toBe("7K3P");
  expect(normalizeCode("hw-hwab")).toBe("HWAB");
});

test("the paper date stays the same calendar day", () => {
  const f = registerSchema.parse({ ...base, fromPaper: true, paperRef: "Sheet 3, line 12", paperDate: "2026-03-05" });
  expect(toRegisterRequest("00000000-0000-0000-0000-000000000001", f).recordedAt).toBe("2026-03-05T12:00:00Z");
});
