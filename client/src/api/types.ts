// api/types.ts: short names for the generated types (schema.ts is generated, never edited by hand).
import type { components } from "./schema";

type Schemas = components["schemas"];
export type RegisterPersonRequest = Schemas["RegisterPersonRequest"];
export type EditPersonRequest = Schemas["EditPersonRequest"];
export type PersonView = Schemas["PersonView"];
export type SlipResponse = Schemas["SlipResponse"];
export type LoginRequest = Schemas["LoginRequest"];
export type ShelterOption = Schemas["ShelterOption"];
export type Me = Schemas["Me"];

// Not in the backend yet (B4 adds the searcher-facing types); kept by hand until then. No location fields.
export type SearcherStatus =
  | { type: "Active" }
  | { type: "Safe" }
  | { type: "Connected"; contactPlan: string };
