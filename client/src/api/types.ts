// api/types.ts: short names for the generated types (schema.ts is generated, never edited by hand).
import type { components } from "./schema";

type Schemas = components["schemas"];
// springdoc doesn't mark Kotlin non-null fields as required, so every response field comes out optional.
// The backend always sends them (they're non-null in Kotlin); Required<> says so. Fields that can be
// missing are `| null` in the schema and stay that way.
export type RegisterPersonRequest = Schemas["RegisterPersonRequest"];
export type EditPersonRequest = Schemas["EditPersonRequest"];
export type PersonView = Required<Schemas["PersonView"]>;
export type SlipResponse = Required<Schemas["SlipResponse"]>;
export type LoginRequest = Required<Schemas["LoginRequest"]>;
export type ShelterOption = Required<Schemas["ShelterOption"]>;
export type Me = Required<Schemas["Me"]>;

// Not in the backend yet (B4 adds the searcher-facing types); kept by hand until then. No location fields.
export type SearcherStatus =
  | { type: "Active" }
  | { type: "Safe" }
  | { type: "Connected"; contactPlan: string };
