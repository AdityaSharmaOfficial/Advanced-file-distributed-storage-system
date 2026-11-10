'use client';

import { useEffect, useState } from 'react';
import { fetchNodes, type NodeStatus, type StorageNode } from '@/lib/api';

const REFRESH_INTERVAL_MS = 5000;

const STATUS_STYLES: Record<NodeStatus, string> = {
  ONLINE: 'bg-emerald-100 text-emerald-800 ring-emerald-600/20',
  SUSPECT: 'bg-amber-100 text-amber-800 ring-amber-600/20',
  OFFLINE: 'bg-rose-100 text-rose-800 ring-rose-600/20',
  DRAINING: 'bg-sky-100 text-sky-800 ring-sky-600/20',
};

function formatBytes(bytes: number): string {
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`;
}

function formatTime(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString() : 'never';
}

export default function DashboardPage() {
  const [nodes, setNodes] = useState<StorageNode[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [checkedAt, setCheckedAt] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      try {
        const data = await fetchNodes();
        if (!active) return;
        setNodes(data);
        setError(null);
        setCheckedAt(new Date().toLocaleTimeString());
      } catch (err) {
        if (!active) return;
        setError(err instanceof Error ? err.message : 'request failed');
      }
    }

    load();
    const timer = setInterval(load, REFRESH_INTERVAL_MS);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, []);

  return (
    <main className="mx-auto max-w-5xl px-6 py-10">
      <header className="flex flex-wrap items-baseline justify-between gap-2 border-b border-stone-200 pb-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Storage nodes</h1>
          <p className="text-sm text-stone-600">Nodes registered with the coordinator. Refreshes every 5 seconds.</p>
        </div>
        {checkedAt && <p className="text-xs text-stone-500">Last checked {checkedAt}</p>}
      </header>

      {error && (
        <div role="alert" className="mt-6 rounded-md border border-rose-300 bg-rose-50 p-4 text-sm text-rose-800">
          Could not load nodes: {error}
        </div>
      )}

      {nodes === null && !error && <p className="mt-6 text-sm text-stone-600">Loading nodes.</p>}

      {nodes !== null && nodes.length === 0 && (
        <p className="mt-6 text-sm text-stone-600">No storage nodes have registered yet.</p>
      )}

      {nodes !== null && nodes.length > 0 && (
        <div className="mt-6 overflow-x-auto rounded-md border border-stone-200 bg-white">
          <table className="min-w-full divide-y divide-stone-200 text-sm">
            <thead className="bg-stone-100 text-left text-xs uppercase tracking-wide text-stone-600">
              <tr>
                <th className="px-4 py-3 font-medium">Name</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium">Address</th>
                <th className="px-4 py-3 font-medium">Capacity</th>
                <th className="px-4 py-3 font-medium">Last seen</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-stone-100">
              {nodes.map((node) => (
                <tr key={node.id}>
                  <td className="px-4 py-3 font-medium">{node.name}</td>
                  <td className="px-4 py-3">
                    <span
                      className={`inline-flex rounded-md px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${STATUS_STYLES[node.status]}`}
                    >
                      {node.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 font-mono text-xs">{node.baseUrl}</td>
                  <td className="px-4 py-3">{formatBytes(node.capacityBytes)}</td>
                  <td className="px-4 py-3">{formatTime(node.lastSeenAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </main>
  );
}
