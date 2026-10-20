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

// Searches and matching (staff-facing: match cards never carry a location).
export type StartSearchRequest = Schemas["StartSearchRequest"];
export type MatchCard = Required<Schemas["MatchCard"]>;
export type SearchResult = Required<Omit<Schemas["SearchResult"], "matches">> & { matches: MatchCard[] };
export type ContactRequestIn = Schemas["ContactRequestIn"];

// The ONLY answer a searcher ever sees: Active, Safe, or Connected with a contact plan. Built from the
// backend's three subtypes, so a new one would have to be added here (and in SearcherView) on purpose.
export type SearcherStatus = Schemas["Active"] | Required<Schemas["Connected"]> | Schemas["Safe"];
export type SearchAnswer = Required<Omit<Schemas["SearchAnswer"], "status">> & { status: SearcherStatus };
export type LookupOut = Required<Omit<Schemas["LookupOut"], "searches">> & { searches: SearchAnswer[] };

// Answers waiting and the lead page.
export type WaitingRequest = Required<Schemas["WaitingRequest"]>;
export type DecisionIn = Schemas["DecisionIn"];
export type MinorReleaseIn = Schemas["MinorReleaseIn"];
export type FlagIn = Schemas["FlagIn"];
export type ChildAtShelter = Required<Schemas["ChildAtShelter"]>;
export type ActiveFlag = Required<Schemas["ActiveFlag"]>;
export type LeadOverview = { childrenWaiting: WaitingRequest[]; unaccompaniedChildren: ChildAtShelter[]; flags: ActiveFlag[] };
