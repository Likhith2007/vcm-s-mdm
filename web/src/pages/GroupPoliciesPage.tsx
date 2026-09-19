import { useEffect, useMemo, useState } from 'react';
import { AppShell } from '../ui/AppShell';
import { useToast } from '../ui/toast';
import {
  listGroupPolicies,
  saveGroupPolicy,
  deleteGroupPolicy,
  type GroupPolicyView,
  type PolicyType,
} from '../api/groupPolicies';
import { searchDevices, type DeviceView } from '../api/devices';

function summarize(g: GroupPolicyView): string {
  if (g.policyType === 'allowAll') return 'Enable all apps';
  const n = g.packages.length;
  const win = g.startTime && g.endTime ? ` · ${g.startTime}–${g.endTime}` : '';
  return `Blocks ${n} app${n === 1 ? '' : 's'}${win}`;
}

export function GroupPoliciesPage() {
  const toast = useToast();
  const [groups, setGroups] = useState<GroupPolicyView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<GroupPolicyView | 'new' | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = () =>
    listGroupPolicies()
      .then(setGroups)
      .catch(() => {
        setGroups([]);
        setError('Could not load group policies.');
      });

  useEffect(() => {
    void load();
  }, []);

  async function remove(g: GroupPolicyView) {
    if (!window.confirm(`Delete "${g.name}"? This can't be undone.`)) return;
    setBusyId(g.id);
    try {
      await deleteGroupPolicy(g.id);
      toast.push('ok', 'Group deleted', g.name);
      await load();
    } catch (e) {
      const msg = e instanceof Error ? e.message : '';
      toast.push(
        'err',
        'Delete failed',
        /notempty/i.test(msg)
          ? `"${g.name}" still has ${g.deviceCount} device${g.deviceCount === 1 ? '' : 's'} assigned — remove them in Edit first.`
          : msg,
      );
    } finally {
      setBusyId(null);
    }
  }

  if (editing) {
    return (
      <AppShell title="Group Policies">
        <GroupPolicyEditor
          initial={editing === 'new' ? null : editing}
          onCancel={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            void load();
          }}
        />
      </AppShell>
    );
  }

  return (
    <AppShell title="Group Policies">
      <div className="page-head">
        <h1>Group Policies</h1>
        <button className="btn btn-dark" onClick={() => setEditing('new')}>
          New group
        </button>
      </div>

      {error && <div className="banner banner-alert">{error}</div>}

      {groups === null ? (
        <div className="panel"><div className="empty"><span className="spin" /> Loading…</div></div>
      ) : groups.length === 0 ? (
        <div className="panel"><div className="empty"><span className="label">No groups</span>Create one to restrict apps for a set of devices.</div></div>
      ) : (
        <div className="cfg-grid">
          {groups.map((g) => (
            <div className="cfg-card" key={g.id}>
              <div className="cfg-top">
                <div className="cfg-nm">{g.name}</div>
                {g.policyType === 'blockScheduled' && (
                  <span
                    className="cfg-badge"
                    style={
                      g.blockedNow
                        ? { color: '#fff', background: 'var(--danger, #e5484d)' }
                        : { color: 'var(--online, #3fd08a)', background: 'var(--surface-2)' }
                    }
                  >
                    {g.blockedNow ? 'Currently blocking' : 'Currently allowed'}
                  </span>
                )}
              </div>
              <div className="cfg-desc">{summarize(g)}</div>
              <div className="cfg-meta">
                <span><span className="k">Devices</span><span className="v">{g.deviceCount}</span></span>
              </div>
              <div className="cfg-actions">
                <button className="btn btn-sm btn-primary" onClick={() => setEditing(g)}>Edit</button>
                <button
                  className="btn btn-sm btn-danger"
                  disabled={busyId === g.id}
                  onClick={() => void remove(g)}
                >
                  {busyId === g.id ? '…' : 'Delete'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </AppShell>
  );
}

function GroupPolicyEditor({
  initial,
  onCancel,
  onSaved,
}: {
  initial: GroupPolicyView | null;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const toast = useToast();
  const isNew = initial == null;
  const [name, setName] = useState(initial?.name ?? '');
  const [policyType, setPolicyType] = useState<PolicyType>(initial?.policyType ?? 'allowAll');
  const [packages, setPackages] = useState<string[]>(initial?.packages ?? []);
  const [pkgInput, setPkgInput] = useState('');
  const [startTime, setStartTime] = useState(initial?.startTime ?? '22:00');
  const [endTime, setEndTime] = useState(initial?.endTime ?? '06:00');
  const [deviceIds, setDeviceIds] = useState<Set<number>>(new Set(initial?.deviceIds ?? []));
  const [devices, setDevices] = useState<DeviceView[]>([]);
  const [deviceQuery, setDeviceQuery] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    searchDevices({ pageSize: 500 })
      .then((r) => setDevices(r.devices.items))
      .catch(() => undefined);
  }, []);

  const shownDevices = useMemo(() => {
    const needle = deviceQuery.trim().toLowerCase();
    return devices
      .filter((d) => !needle || `${d.description ?? ''} ${d.number}`.toLowerCase().includes(needle))
      .sort((a, b) => (a.description ?? a.number).localeCompare(b.description ?? b.number));
  }, [devices, deviceQuery]);

  function addPackage() {
    const pkg = pkgInput.trim();
    if (!pkg || packages.includes(pkg)) {
      setPkgInput('');
      return;
    }
    setPackages((p) => [...p, pkg]);
    setPkgInput('');
  }

  function removePackage(pkg: string) {
    setPackages((p) => p.filter((x) => x !== pkg));
  }

  function toggleDevice(id: number) {
    setDeviceIds((d) => {
      const next = new Set(d);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  async function save() {
    if (!name.trim()) {
      toast.push('err', 'Name required', 'Give the group a name.');
      return;
    }
    if (policyType === 'blockScheduled' && (!startTime || !endTime)) {
      toast.push('err', 'Time window required', 'Set both a start and end time.');
      return;
    }
    setBusy(true);
    try {
      await saveGroupPolicy({
        id: initial?.id,
        name: name.trim(),
        policyType,
        packages: policyType === 'blockScheduled' ? packages : [],
        startTime: policyType === 'blockScheduled' ? startTime : null,
        endTime: policyType === 'blockScheduled' ? endTime : null,
        deviceIds: Array.from(deviceIds),
      });
      toast.push('ok', isNew ? 'Group created' : 'Group saved', name.trim());
      onSaved();
    } catch (e) {
      const msg = e instanceof Error ? e.message : '';
      toast.push('err', 'Save failed', /duplicate/i.test(msg) ? 'A group with that name already exists.' : msg);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <div className="crumb">
        <a href="/group-policies" onClick={(e) => { e.preventDefault(); onCancel(); }}>Group Policies</a>
        {' / '}{isNew ? 'New' : name || initial!.name}
      </div>

      <div className="cfg-editbar">
        <h1 style={{ fontSize: 22, fontWeight: 700, letterSpacing: '-0.02em', margin: 0 }}>
          {isNew ? 'New group' : name || initial!.name}
        </h1>
        <div style={{ flex: 1 }} />
        <button className="btn" onClick={onCancel} disabled={busy}>Cancel</button>
        <button className="btn btn-primary" onClick={() => void save()} disabled={busy}>
          {busy ? 'Saving…' : 'Save'}
        </button>
      </div>

      <section className="panel cfg-panel">
        <div className="cfg-field">
          <div className="cfg-field-label"><label>Name</label></div>
          <div className="cfg-field-ctl">
            <input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="Group name" />
          </div>
        </div>

        <div className="cfg-field">
          <div className="cfg-field-label">
            <label>Policy</label>
            <span className="cfg-field-help">What this group does to its devices' apps.</span>
          </div>
          <div className="cfg-field-ctl">
            <span className="seg" role="tablist" aria-label="Policy type">
              <button
                type="button"
                className={policyType === 'allowAll' ? 'on' : ''}
                onClick={() => setPolicyType('allowAll')}
              >
                Enable all apps
              </button>
              <button
                type="button"
                className={policyType === 'blockScheduled' ? 'on' : ''}
                onClick={() => setPolicyType('blockScheduled')}
              >
                Disable these apps on a schedule
              </button>
            </span>
          </div>
        </div>

        {policyType === 'blockScheduled' && (
          <>
            <div className="cfg-field">
              <div className="cfg-field-label">
                <label>Restricted apps</label>
                <span className="cfg-field-help">Package names blocked during the window below.</span>
              </div>
              <div className="cfg-field-ctl">
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: packages.length ? 8 : 0 }}>
                  {packages.map((pkg) => (
                    <span className="chip" key={pkg}>
                      <span className="mono">{pkg}</span>
                      <button
                        type="button"
                        className="btn-ghost"
                        style={{ border: 0, background: 'none', cursor: 'pointer', padding: 0, lineHeight: 1 }}
                        onClick={() => removePackage(pkg)}
                        aria-label={`Remove ${pkg}`}
                      >
                        ✕
                      </button>
                    </span>
                  ))}
                </div>
                <input
                  className="input"
                  value={pkgInput}
                  onChange={(e) => setPkgInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ',') {
                      e.preventDefault();
                      addPackage();
                    }
                  }}
                  onBlur={addPackage}
                  placeholder="com.example.app — press Enter to add"
                />
              </div>
            </div>

            <div className="cfg-field">
              <div className="cfg-field-label">
                <label>Time window</label>
                <span className="cfg-field-help">Daily — can cross midnight (e.g. 22:00 to 06:00).</span>
              </div>
              <div className="cfg-field-ctl" style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
                <input
                  className="input"
                  type="time"
                  value={startTime}
                  onChange={(e) => setStartTime(e.target.value)}
                  style={{ maxWidth: 140 }}
                />
                <span className="note">to</span>
                <input
                  className="input"
                  type="time"
                  value={endTime}
                  onChange={(e) => setEndTime(e.target.value)}
                  style={{ maxWidth: 140 }}
                />
              </div>
            </div>
          </>
        )}
      </section>

      <section className="panel cfg-panel">
        <div className="cfg-sec-h" style={{ display: 'flex', alignItems: 'center' }}>
          <span>Devices in this group</span>
          <span className="note" style={{ marginLeft: 'auto' }}>{deviceIds.size} selected</span>
        </div>
        <div className="dv-search" style={{ margin: '0 20px 12px' }}>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="11" cy="11" r="7" />
            <path d="M21 21l-4-4" />
          </svg>
          <input
            type="search"
            placeholder="Search devices"
            value={deviceQuery}
            onChange={(e) => setDeviceQuery(e.target.value)}
          />
        </div>
        <div className="picker-list" style={{ maxHeight: '40vh', margin: '0 20px 20px' }}>
          {shownDevices.length === 0 ? (
            <div className="cfg-empty" style={{ padding: 20 }}>No devices match.</div>
          ) : (
            shownDevices.map((d) => {
              const on = deviceIds.has(d.id);
              return (
                <label key={d.id} className={`picker-row ${on ? 'on' : ''}`}>
                  <input type="checkbox" className="dev-check" checked={on} onChange={() => toggleDevice(d.id)} />
                  <span className="picker-meta">
                    <span className="picker-nm">{d.description || d.number}</span>
                    <span className="picker-pkg mono">{d.number}</span>
                  </span>
                </label>
              );
            })
          )}
        </div>
      </section>
    </>
  );
}
