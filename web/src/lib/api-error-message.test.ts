import { describe, expect, it } from "vitest";
import { ApiClientError, getApiErrorMessage } from "@/lib/api-client";

/**
 * The API answers a validation failure with a fixed sentence and a per-field map:
 *
 *   { code: "VALIDATION_FAILED", message: "Some fields need attention",
 *     fieldErrors: { newPassword: "size must be between 10 and 128" } }
 *
 * The map used to be discarded, so every rejected form said only "Some fields need attention".
 * Changing a password to something too short was unexplainable from the screen even though the
 * server had said exactly what was wrong.
 */
describe("getApiErrorMessage", () => {
  it("names the offending field and the reason", () => {
    const err = new ApiClientError(422, {
      code: "VALIDATION_FAILED",
      message: "Some fields need attention",
      fieldErrors: { newPassword: "size must be between 10 and 128" },
    });

    expect(getApiErrorMessage(err)).toBe(
      "Some fields need attention (newPassword: size must be between 10 and 128)",
    );
  });

  it("lists every field when more than one failed", () => {
    const err = new ApiClientError(422, {
      code: "VALIDATION_FAILED",
      message: "Some fields need attention",
      fieldErrors: { email: "must be a well-formed email address", password: "must not be blank" },
    });

    const message = getApiErrorMessage(err);
    expect(message).toContain("email: must be a well-formed email address");
    expect(message).toContain("password: must not be blank");
  });

  it("leaves errors that carry no field map untouched", () => {
    const err = new ApiClientError(403, {
      code: "PERMISSION_DENIED",
      message: "You do not have permission to perform this action",
    });

    expect(getApiErrorMessage(err)).toBe("You do not have permission to perform this action");
  });

  it("does not append empty parentheses for an empty field map", () => {
    const err = new ApiClientError(422, {
      code: "VALIDATION_FAILED",
      message: "Some fields need attention",
      fieldErrors: {},
    });

    expect(getApiErrorMessage(err)).toBe("Some fields need attention");
  });

  it("falls back for a plain error and for a non-error", () => {
    expect(getApiErrorMessage(new Error("network down"))).toBe("network down");
    expect(getApiErrorMessage("not an error")).toBe("Something went wrong");
  });
});
