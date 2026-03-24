import { describe, expect, it } from "vitest";
import { PACKAGE_VERSION } from "./index.js";

describe("shared package", () => {
  it("exports PACKAGE_VERSION", () => {
    expect(PACKAGE_VERSION).toBe("1.0.0");
  });
});
