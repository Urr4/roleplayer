import { useEffect, useMemo, useState } from 'react';
import { Box, Tab, Tabs, Typography } from '@mui/material';
import CastleIcon from '@mui/icons-material/Castle';
import ExploreIcon from '@mui/icons-material/Explore';
import ChronicleTab from './pages/ChronicleTab';
import InitiativeTab from './pages/InitiativeTab';
import NpcTab from './pages/NpcTab';
import PlayerTab from './pages/PlayerTab';
import type { AdventureDto, ChronicleDto } from './types';
import { getChronicles } from './api/client';

const ACTIVE_CHRONICLE_KEY = 'roleplayer.activeChronicleId';

export default function App() {
  const [tab, setTab] = useState(0);
  const [adventureTab, setAdventureTab] = useState(0);
  const [chronicles, setChronicles] = useState<ChronicleDto[]>([]);
  const [activeChronicleId, setActiveChronicleId] = useState<string | null>(() => localStorage.getItem(ACTIVE_CHRONICLE_KEY));
  const [activeAdventure, setActiveAdventure] = useState<AdventureDto | null>(null);

  const refreshChronicles = () => {
    getChronicles().then(list => {
      setChronicles(list);
      if (activeChronicleId && !list.some(chronicle => chronicle.id === activeChronicleId)) {
        setActiveChronicleId(null);
      }
    });
  };

  useEffect(() => {
    refreshChronicles();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (activeChronicleId) {
      localStorage.setItem(ACTIVE_CHRONICLE_KEY, activeChronicleId);
    } else {
      localStorage.removeItem(ACTIVE_CHRONICLE_KEY);
    }
  }, [activeChronicleId]);

  const activeChronicle = useMemo(
    () => chronicles.find(chronicle => chronicle.id === activeChronicleId) ?? null,
    [chronicles, activeChronicleId],
  );

  // The active-adventure sub-bar (Character / Initiative Tracker / NPC Helper)
  // only makes sense while an adventure is running; fall back to the
  // Chronicle tab whenever it disappears so we don't get stuck on a hidden tab.
  const effectiveTab = !activeAdventure && tab === 1 ? 0 : tab;

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', py: { xs: 2, sm: 4 } }}>
      <Box sx={{ width: '100%', maxWidth: 1100, px: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 2, justifyContent: 'center' }}>
          <CastleIcon sx={{ color: 'primary.light', fontSize: 34 }} />
          <Typography variant="h4" sx={{ color: 'text.primary' }}>
            Roleplayer
          </Typography>
        </Box>

        {activeChronicle && (
          <Typography variant="subtitle1" align="center" sx={{ color: 'text.secondary', mb: 1 }}>
            Current chronicle: <strong>{activeChronicle.name}</strong>
            {activeAdventure && (
              <>
                {' '}
                · Active adventure: <strong>{activeAdventure.name}</strong>
              </>
            )}
          </Typography>
        )}

        <Tabs value={effectiveTab} onChange={(_, value) => setTab(value)} variant="fullWidth">
          <Tab label="Chronicles" />
          <Tab
            icon={<ExploreIcon fontSize="small" />}
            iconPosition="start"
            label={activeAdventure ? activeAdventure.name : 'Adventure'}
            disabled={!activeAdventure}
          />
          <Tab label="Players" />
        </Tabs>

        {effectiveTab === 1 && activeAdventure && (
          <Tabs
            value={adventureTab}
            onChange={(_, value) => setAdventureTab(value)}
            variant="fullWidth"
            sx={{ mt: 1, mb: 1 }}
          >
            <Tab label="Initiative Tracker" />
            <Tab label="NPC Helper" />
          </Tabs>
        )}

        <Box
          className="torn-edge"
          sx={{
            bgcolor: 'background.paper',
            p: { xs: 2, sm: 3 },
            minHeight: 480,
            borderRadius: 3,
            boxShadow: '0 16px 40px rgba(15, 23, 42, 0.24)',
          }}
        >
          {effectiveTab === 0 && (
            <ChronicleTab
              chronicles={chronicles}
              activeChronicleId={activeChronicleId}
              onSelectChronicle={setActiveChronicleId}
              onChroniclesChanged={refreshChronicles}
              onActiveAdventureChanged={setActiveAdventure}
            />
          )}
          {effectiveTab === 1 && activeChronicle && activeAdventure && (
            <>
              {adventureTab === 0 && <InitiativeTab chronicle={activeChronicle} />}
              {adventureTab === 1 && <NpcTab chronicle={activeChronicle} />}
            </>
          )}
          {effectiveTab === 2 && <PlayerTab />}
        </Box>
      </Box>
    </Box>
  );
}
