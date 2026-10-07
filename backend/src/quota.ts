/** Free solves per device per UTC day. In-memory for now; move to Redis/Firestore before scaling out. */
export interface Quota {
  /** Records a solve and returns true, or returns false when the device is out of free solves. */
  tryConsume(deviceId: string): boolean;
  /** Gives one consumed solve back, e.g. when the AI call failed. */
  refund(deviceId: string): void;
}

/** Subscription check. Stubbed until Google Play purchase-token verification lands. */
export interface Entitlements {
  isSubscribed(deviceId: string): Promise<boolean>;
}

export const noSubscriptions: Entitlements = { isSubscribed: async () => false };

export class InMemoryDailyQuota implements Quota {
  private day = "";
  private readonly used = new Map<string, number>();

  constructor(
    private readonly freeDailyLimit: number,
    private readonly now: () => Date = () => new Date(),
  ) {}

  tryConsume(deviceId: string): boolean {
    this.rollOver();
    const used = this.used.get(deviceId) ?? 0;
    if (used >= this.freeDailyLimit) return false;
    this.used.set(deviceId, used + 1);
    return true;
  }

  refund(deviceId: string): void {
    const used = this.used.get(deviceId) ?? 0;
    if (used > 0) this.used.set(deviceId, used - 1);
  }

  private rollOver() {
    const today = this.now().toISOString().slice(0, 10);
    if (today !== this.day) {
      this.day = today;
      this.used.clear();
    }
  }
}
