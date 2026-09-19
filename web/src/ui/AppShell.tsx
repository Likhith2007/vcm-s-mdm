import { useEffect, useState, type ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useTheme } from './theme';
import { UpdateBanner } from '../components/UpdateBanner';
import { ReloadPrompt } from '../components/ReloadPrompt';
import { listApprovals } from '../api/approvals';
import {
  IconDashboard,
  IconDevices,
  IconConfig,
  IconApps,
  IconGroupPolicy,
  IconApproval,
  IconEnroll,
  IconSettings,
  IconSignOut,
  IconMenu,
  IconSun,
  IconMoon,
} from './icons';

interface NavEntry {
  to: string;
  label: string;
  Icon: (p: { className?: string }) => ReactNode;
}

const NAV: NavEntry[] = [
  { to: '/dashboard', label: 'Overview', Icon: IconDashboard },
  { to: '/devices', label: 'Devices', Icon: IconDevices },
  { to: '/configs', label: 'Configurations', Icon: IconConfig },
  { to: '/apps', label: 'Apps', Icon: IconApps },
  { to: '/group-policies', label: 'Group Policies', Icon: IconGroupPolicy },
  { to: '/approvals', label: 'Approvals', Icon: IconApproval },
  { to: '/enroll', label: 'Enroll', Icon: IconEnroll },
  { to: '/settings', label: 'Settings', Icon: IconSettings },
];

/** Live count of pending app-install approvals, fleet-wide — self-polling like EventTimeline,
 *  so the nav badge stays current no matter which page the admin is looking at. */
function usePendingApprovalCount(): number {
  const [count, setCount] = useState(0);
  useEffect(() => {
    let on = true;
    let t: ReturnType<typeof setTimeout>;
    const load = async () => {
      await listApprovals()
        .then((rows) => { if (on) setCount(rows.filter((r) => r.status === 'pending').length); })
        .catch(() => undefined);
      if (!on) return;
      t = setTimeout(() => void load(), 5000);
    };
    void load();
    return () => { on = false; clearTimeout(t); };
  }, []);
  return count;
}

export function AppShell({
  title,
  children,
}: {
  /** Page label, shown only in the mobile top bar. */
  title?: string;
  children: ReactNode;
}) {
  const { user, signOut } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const [open, setOpen] = useState(false);
  const close = () => setOpen(false);
  const pendingApprovals = usePendingApprovalCount();

  return (
    <div className="shell">
      <div
        className={`scrim ${open ? 'show' : ''}`}
        onClick={close}
        aria-hidden="true"
      />
      <aside className={`sidebar ${open ? 'open' : ''}`}>
        <div className="sidebar-brand">
          <span className="wordmark" aria-label="MDMesh">
            <span className="bullet" aria-hidden="true" />
            <span>
              <span className="mdm">MDM</span>
              <span className="esh">esh</span>
            </span>
          </span>
        </div>
        <nav className="nav">
          {NAV.map(({ to, label, Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
              onClick={close}
            >
              <Icon className="ico" />
              <span>{label}</span>
              {to === '/approvals' && pendingApprovals > 0 && (
                <span
                  style={{
                    marginLeft: 'auto',
                    background: 'var(--danger, #e5484d)',
                    color: '#fff',
                    borderRadius: 999,
                    fontSize: 11,
                    fontWeight: 700,
                    lineHeight: 1,
                    padding: '3px 6px',
                    minWidth: 16,
                    textAlign: 'center',
                  }}
                >
                  {pendingApprovals}
                </span>
              )}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-foot">
          <div className="sidebar-user">
            <span className="who">
              {user?.login || user?.name || 'admin@localhost'}
            </span>
          </div>
          <button
            className="btn btn-ghost"
            onClick={toggleTheme}
            aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`}
          >
            {theme === 'dark' ? <IconSun className="ico" /> : <IconMoon className="ico" />}
            <span style={{ marginLeft: 8 }}>{theme === 'dark' ? 'Light' : 'Dark'}</span>
          </button>
          <button className="btn btn-ghost" onClick={() => void signOut()}>
            <IconSignOut className="ico" />
            <span style={{ marginLeft: 8 }}>Sign out</span>
          </button>
        </div>
      </aside>

      <div className="main">
        <div className="rail-mobilebar">
          <button
            className="btn btn-ghost menu-btn"
            onClick={() => setOpen((v) => !v)}
            aria-label="Toggle navigation"
          >
            <IconMenu />
          </button>
          <span style={{ fontWeight: 600 }}>{title ?? 'MDMesh'}</span>
        </div>
        <main className="content route-enter"><ReloadPrompt /><UpdateBanner />{children}</main>
      </div>
    </div>
  );
}
