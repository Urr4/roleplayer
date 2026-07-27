import { useEffect, useMemo, useState } from 'react';
import { Box, Tab, Tabs, Typography } from '@mui/material';
import CastleIcon from '@mui/icons-material/Castle';
import SessionTab from './pages/SessionTab';
import InitiativeTab from './pages/InitiativeTab';
import NpcTab from './pages/NpcTab';
import type { SessionDto } from './types';
import { getSessions } from './api/client';

const ACTIVE_SESSION_KEY = 'roleplayer.activeSessionId';

export default function App() {
  const [tab, setTab] = useState(0);
  const [sessions, setSessions] = useState<SessionDto[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(
    () => localStorage.getItem(ACTIVE_SESSION_KEY),
  );

  const refreshSessions = () => {
    getSessions().then(list => {
      setSessions(list);
      // If the previously active session no longer exists, drop the selection.
      if (activeSessionId && !list.some(s => s.id === activeSessionId)) {
        setActiveSessionId(null);
      }
    });
  };

  useEffect(() => {
    refreshSessions();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (activeSessionId) {
      localStorage.setItem(ACTIVE_SESSION_KEY, activeSessionId);
    } else {
      localStorage.removeItem(ACTIVE_SESSION_KEY);
    }
  }, [activeSessionId]);

  const activeSession = useMemo(
    () => sessions.find(s => s.id === activeSessionId) ?? null,
    [sessions, activeSessionId],
  );

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', py: { xs: 2, sm: 4 } }}>
      <Box sx={{ width: '100%', maxWidth: 1100, px: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 2, justifyContent: 'center' }}>
          <CastleIcon sx={{ color: '#e8d9b5', fontSize: 34 }} />
          <Typography variant="h4" sx={{ color: '#e8d9b5', textShadow: '2px 2px 0 #2b1a10' }}>
            Roleplayer &mdash; GM's Board
          </Typography>
        </Box>

        {activeSession && (
          <Typography variant="subtitle1" align="center" sx={{ color: '#d4ac66', mb: 1, fontStyle: 'italic' }}>
            Current session: <strong>{activeSession.name}</strong>
          </Typography>
        )}

        <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="fullWidth">
          <Tab label="Session" />
          <Tab label="Initiative Tracker" disabled={!activeSession} />
          <Tab label="NPC Helper" disabled={!activeSession} />
        </Tabs>

        <Box
          className="torn-edge"
          sx={{
            bgcolor: 'background.paper',
            p: { xs: 2, sm: 3 },
            minHeight: 480,
            boxShadow: '0 6px 20px rgba(0,0,0,0.45)',
          }}
        >
          {tab === 0 && (
            <SessionTab
              sessions={sessions}
              activeSessionId={activeSessionId}
              onSelectSession={setActiveSessionId}
              onSessionsChanged={refreshSessions}
            />
          )}
          {tab === 1 && activeSession && <InitiativeTab session={activeSession} />}
          {tab === 2 && activeSession && <NpcTab session={activeSession} />}
          {tab !== 0 && !activeSession && (
            <Typography align="center" sx={{ mt: 4 }}>
              Select or create a session in the Session tab first.
            </Typography>
          )}
        </Box>
      </Box>
    </Box>
  );
}
