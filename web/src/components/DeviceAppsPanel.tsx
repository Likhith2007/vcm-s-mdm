import { useEffect, useMemo, useState } from 'react';
import { getLatestScan, scanApps, type AppInfo } from '../api/deviceApps';
import { useToast } from '../ui/toast';
import { fmtRelative } from '../ui/format';

/** The device's installed-app inventory — reads the last saved `apps.scan` result instantly (if
 *  any), and "Scan now" queues a fresh one (same command the kiosk app picker uses). */
export function DeviceAppsPanel({ device }: { device: { number: string } }) {
  const toast = useToast();
  const [apps, setApps] = useState<AppInfo[] | null>(null);
  const [scannedAt, setScannedAt] = useState<number | undefined>();
  const [loading, setLoading] = useState(true);
  const [scanning, setScanning] = useState(false);
  const [q, setQ] = useState('');

  useEffect(() => {
    let on = true;
    setLoading(true);
    getLatestScan(device.number)
      .then((snap) => {
        if (!on) return;
        setApps(snap?.apps ?? null);
        setScannedAt(snap?.scannedAt);
      })
      .catch(() => undefined)
      .finally(() => { if (on) setLoading(false); });
    return () => { on = false; };
  }, [device.number]);

  async function scan() {
    setScanning(true);
    try {
      const result = await scanApps(device.number);
      setApps(result);
      setScannedAt(Date.now());
      toast.push('ok', 'Scan complete', `${result.length} app${result.length === 1 ? '' : 's'} found.`);
    } catch (e) {
      toast.push('err', 'Scan failed', e instanceof Error ? e.message : 'Is the device online?');
    } finally {
      setScanning(false);
    }
  }

  const shown = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return (apps ?? [])
      .filter((a) => !needle || `${a.label} ${a.pkg}`.toLowerCase().includes(needle))
      .sort((a, b) => a.label.localeCompare(b.label));
  }, [apps, q]);

  return (
    <div className="panel">
      <div className="panel-head">
        <h2 className="panel-title">Installed apps</h2>
        <button className="btn btn-sm" disabled={scanning} onClick={() => void scan()}>
          {scanning ? 'Scanning…' : 'Scan now'}
        </button>
      </div>

      {loading ? (
        <div className="empty"><span className="spin" /> Loading…</div>
      ) : apps == null ? (
        <div className="empty">Never scanned yet. Click "Scan now" to fetch the device's app list.</div>
      ) : (
        <>
          <div className="dv-search" style={{ margin: '0 20px 12px' }}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="11" cy="11" r="7" />
              <path d="M21 21l-4-4" />
            </svg>
            <input
              type="search"
              placeholder="Search apps"
              value={q}
              onChange={(e) => setQ(e.target.value)}
            />
          </div>
          <p className="note" style={{ padding: '0 20px 12px' }}>
            {apps.length} app{apps.length === 1 ? '' : 's'}
            {scannedAt ? ` · scanned ${fmtRelative(scannedAt)}` : ''}
          </p>
          <div className="picker-list" style={{ maxHeight: 'none', margin: '0 20px 20px' }}>
            {shown.length === 0 ? (
              <div className="cfg-empty" style={{ padding: 20 }}>No apps match "{q}".</div>
            ) : (
              shown.map((a) => (
                <div key={a.pkg} className="picker-row" style={{ cursor: 'default' }}>
                  <span className="picker-ic" aria-hidden="true">
                    {(a.label.trim()[0] ?? '?').toUpperCase()}
                  </span>
                  <span className="picker-meta">
                    <span className="picker-nm">
                      {a.label}
                      {a.system && <span className="note" style={{ marginLeft: 6 }}>System</span>}
                    </span>
                    <span className="picker-pkg mono">{a.pkg}</span>
                  </span>
                  {a.versionName && <span className="picker-ver">v{a.versionName}</span>}
                </div>
              ))
            )}
          </div>
        </>
      )}
    </div>
  );
}
