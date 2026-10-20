// forms/toRequest.ts
// The one place where the form becomes the backend's request.
// `id` is made once when the form opens and kept in the draft, so every retry re-sends the SAME id.
import type { RegisterPersonRequest } from "../api/types";
import type { RegisterForm } from "./registerSchema";

export function toRegisterRequest(id: string, f: RegisterForm): RegisterPersonRequest {
  return {
    id,
    preferredName: f.preferredName,
    otherNames: f.otherNames,
    approxAge: f.approxAge,
    isMinor: f.isMinor,
    unaccompanied: f.unaccompanied,
    homeArea: f.homeArea,
    description: f.description,
    phone: f.phone,
    visibility: f.visibility,
    source: f.fromPaper ? "paper" : "app",
    paperRef: f.fromPaper ? f.paperRef : undefined,
    recordedAt: f.fromPaper && f.paperDate ? new Date(f.paperDate).toISOString() : undefined,
    slipCode: f.slipCode,
  };
}

export const newRecordId = () => crypto.randomUUID();
