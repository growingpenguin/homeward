// api/http.ts
// A failed request remembers its HTTP status, so we only retry when retrying can help.
export class HttpError extends Error {
  readonly status: number;
  locked = false;   // a 401 because the screen is locked, not because nobody is logged in

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}
