-- Post-seed repairs for MDMesh, shared by BOTH installers (setup.sh Docker + install-native.sh).
-- Run on EVERY install/upgrade — not just after a fresh seed — so older deployments pick up these
-- fixes too. Idempotent, and a safe no-op on an empty database: every statement's WHERE clause
-- matches nothing until the seed/migrations have created rows. Requires the schema to exist
-- (run it after Liquibase has finished first boot).

-- QR/token enrollment creates the device row on demand — that needs the settings flag ON and a
-- default configuration (devices.configurationid is NOT NULL; the init seed sets neither, and
-- without them every /agent/v1/enroll fails). COALESCE keeps a previously chosen default config.
UPDATE settings SET createnewdevices=true, newdeviceconfigurationid=COALESCE(newdeviceconfigurationid, (SELECT MIN(id) FROM configurations));

-- Remove the auxiliary Headwind seed apps (not used by our agent). The launcher (com.hmdm.launcher)
-- is left in place — the default configurations reference it as their main app.
DELETE FROM configurationapplications WHERE applicationid IN (SELECT id FROM applications WHERE pkg IN ('com.hmdm.pager','com.hmdm.phoneproxy','com.hmdm.emuilauncherrestarter'));
DELETE FROM applicationversions      WHERE applicationid IN (SELECT id FROM applications WHERE pkg IN ('com.hmdm.pager','com.hmdm.phoneproxy','com.hmdm.emuilauncherrestarter'));
DELETE FROM applications             WHERE pkg IN ('com.hmdm.pager','com.hmdm.phoneproxy','com.hmdm.emuilauncherrestarter');
