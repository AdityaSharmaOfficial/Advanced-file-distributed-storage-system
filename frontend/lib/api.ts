export type NodeStatus = 'ONLINE' | 'SUSPECT' | 'OFFLINE' | 'DRAINING';

export interface StorageNode {
  id: string;
  name: string;
  baseUrl: string;
  capacityBytes: number;
  status: NodeStatus;
  registeredAt: string;
  lastSeenAt: string | null;
}

const COORDINATOR_URL = process.env.NEXT_PUBLIC_COORDINATOR_URL ?? 'http://localhost:8080';

export async function fetchNodes(): Promise<StorageNode[]> {
  const res = await fetch(`${COORDINATOR_URL}/api/nodes`, { cache: 'no-store' });
  if (!res.ok) {
    throw new Error(`coordinator responded with HTTP ${res.status}`);
  }
  return (await res.json()) as StorageNode[];
}
