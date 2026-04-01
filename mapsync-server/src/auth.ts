export abstract class AuthState {
    protected constructor(public readonly logName: string | null) {}
}

export class AwaitingHandshake extends AuthState {
    public constructor() {
        super(null);
    }
}

export class AwaitingIdentityResponse extends AuthState {
    public constructor(public readonly serverSalt: Buffer) {
        super(null);
    }
}

export class Welcomed extends AuthState {
    public constructor(
        public readonly name: string,
        public readonly uuid: string,
        public readonly authed: boolean,
    ) {
        super(name + (authed ? "" : "?"));
    }
}
