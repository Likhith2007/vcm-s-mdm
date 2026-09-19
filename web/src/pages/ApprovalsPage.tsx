import { useEffect, useMemo, useState } from 'react';
import { AppShell } from '../ui/AppShell';
import { useToast } from '../ui/toast';
import { fmtRelative } from '../ui/format';
import { listApprovals, approveInstall, denyInstall, type PendingApproval } from '../api/approvals';
import { searchDevices } from '../api/devices';

const POLL_MS = 5000;

export function ApprovalsPage() {
  const toast = useToast();
  const [rows, setRows] = useState<PendingApproval[]>([]);
  const [names, setNames] = useState<Record<string, string>>({});
  const [busyId, setBusyId] = useState<number | null>(null);

  // Self-scheduling poll — the next tick is armed only after the current one finishes (same
  // pattern as EventTimeline), so a slow response never overlaps the next request.
  useEffect(() => {
    let on = true;
    let t: ReturnType<typeof setTimeout>;
    const load = async () => {
      await listApprovals()
        .then((r) => { if (on) setRows(r); })
        .catch(() => undefined);
      if (!on) return;
      t = setTimeout(() => void load(), POLL_MS);
    };
    void load();
    return () => { on = false; clearTimeout(t); };
  }, []);

  // Friendly device names — fetched once; the device number is shown as a fallback/detail either way.
  useEffect(() => {
    searchDevices({ pageSize: 500 })
      .then((r) => {
        const map: Record<string, string> = {};
        for (const d of r.devices.items) {
          if (d.description) map[d.number] = d.description;
        }
        setNames(map);
      })
      .catch(() => undefined);
  }, []);

  const pending = useMemo(() => rows.filter((r) => r.status === 'pending'), [rows]);

  async function act(row: PendingApproval, approve: boolean) {
    setBusyId(row.id);
    try {
      if (approve) await approveInstall(row.id);
      else await denyInstall(row.id);
      toast.push('ok', approve ? 'Approved' : 'Denied',
        approve ? `${row.packageName} can now be used.` : `${row.packageName} was uninstalled.`);
      setRows((rs) => rs.filter((r) => r.id !== row.id));
    } catch (e) {
      toast.push('err', 'Action failed', e instanceof Error ? e.message : 'Try again.');
    } finally {
      setBusyId(null);
    }
  }

  return (
    <AppShell title="Approvals">
      <div className="crumb">Approvals</div>
      <h1 style={{ fontSize: 24, fontWeight: 700, letterSpacing: '-0.02em', margin: '0 0 16px' }}>
        Pending app installs
      </h1>
      <p className="note" style={{ margin: '0 0 16px' }}>
        Apps the device's own user installed (Play Store or sideload) outside MDM control. Each
        one was suspended the moment it was detected — <b>Approve</b> lets it be used,{' '}
        <b>Deny</b> silently uninstalls it. Apps the console pushes itself never show up here.
      </p>
      <section className="panel">
        {pending.length === 0 ? (
          <div className="empty">No pending approvals.</div>
        ) : (
          <ul className="timeline">
            {pending.map((r) => (
              <li className="timeline-item" key={r.id} style={{ alignItems: 'center', gap: 12 }}>
                <span className="t-status">{fmtRelative(r.createdAt)}</span>
                <span className="t-type">
                  {names[r.deviceNumber] ?? r.deviceNumber}
                  <span className="sub mono" style={{ display: 'block' }}>{r.packageName}</span>
                </span>
                <span style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
                  <button
                    className="btn btn-primary btn-sm"
                    disabled={busyId === r.id}
                    onClick={() => void act(r, true)}
                  >
                    {busyId === r.id ? '…' : 'Approve'}
                  </button>
                  <button
                    className="btn btn-danger btn-sm"
                    disabled={busyId === r.id}
                    onClick={() => void act(r, false)}
                  >
                    {busyId === r.id ? '…' : 'Deny'}
                  </button>
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </AppShell>
  );
}
