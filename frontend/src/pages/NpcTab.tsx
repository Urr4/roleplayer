import { useEffect, useState } from 'react';
import {
  Autocomplete,
  Button,
  Chip,
  Divider,
  Grid,
  IconButton,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import CasinoIcon from '@mui/icons-material/Casino';
import SaveIcon from '@mui/icons-material/Save';
import CloseIcon from '@mui/icons-material/Close';
import TornCard from '../components/TornCard';
import type { AttributePools, NpcDto, NpcStatus, SessionDto } from '../types';
import {
  getAllNpcs,
  getAttributePools,
  getRandomNpc,
  getSessionNpcs,
  importNpcIntoSession,
  removeNpcFromSession,
  saveNpcInSession,
} from '../api/client';

interface Props {
  session: SessionDto;
}

const STATUS_LABEL: Record<NpcStatus, string> = {
  HIGHER: 'Higher Standing',
  EQUAL: 'Equal Standing',
  LOWER: 'Lower Standing',
};

const emptyDraft: NpcDto = {
  id: null,
  name: '',
  motive: '',
  status: 'EQUAL',
  mood: '',
  originSessionId: null,
  createdAt: null,
};

export default function NpcTab({ session }: Props) {
  const [pools, setPools] = useState<AttributePools>({ motives: [], moods: [], statuses: ['HIGHER', 'EQUAL', 'LOWER'] });
  const [sessionNpcs, setSessionNpcs] = useState<NpcDto[]>([]);
  const [allNpcs, setAllNpcs] = useState<NpcDto[]>([]);
  const [selected, setSelected] = useState<NpcDto | null>(null);
  const [draft, setDraft] = useState<NpcDto>(emptyDraft);
  const [importTarget, setImportTarget] = useState<NpcDto | null>(null);

  const refreshSessionNpcs = () => getSessionNpcs(session.id).then(setSessionNpcs);
  const refreshAllNpcs = () => getAllNpcs().then(setAllNpcs);

  useEffect(() => {
    getAttributePools().then(setPools);
    refreshSessionNpcs();
    refreshAllNpcs();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session.id]);

  const selectNpc = (npc: NpcDto) => {
    setSelected(npc);
    setDraft(npc);
  };

  const startNewDraft = () => {
    setSelected(null);
    setDraft(emptyDraft);
  };

  const rollRandomNpc = async () => {
    const npc = await getRandomNpc();
    setSelected(null);
    setDraft(npc);
  };

  const rollField = (field: 'motive' | 'mood' | 'status') => {
    if (field === 'motive') {
      setDraft(d => ({ ...d, motive: pools.motives[Math.floor(Math.random() * pools.motives.length)] }));
    } else if (field === 'mood') {
      setDraft(d => ({ ...d, mood: pools.moods[Math.floor(Math.random() * pools.moods.length)] }));
    } else {
      setDraft(d => ({ ...d, status: pools.statuses[Math.floor(Math.random() * pools.statuses.length)] }));
    }
  };

  const handleSave = async () => {
    if (!draft.name.trim() || !draft.motive.trim() || !draft.mood.trim()) return;
    await saveNpcInSession(session.id, {
      name: draft.name.trim(),
      motive: draft.motive,
      status: draft.status,
      mood: draft.mood,
    });
    startNewDraft();
    refreshSessionNpcs();
    refreshAllNpcs();
  };

  const handleImport = async () => {
    if (!importTarget?.id) return;
    await importNpcIntoSession(session.id, importTarget.id);
    setImportTarget(null);
    refreshSessionNpcs();
  };

  const handleRemove = async (npcId: string) => {
    await removeNpcFromSession(session.id, npcId);
    if (selected?.id === npcId) startNewDraft();
    refreshSessionNpcs();
  };

  const importableNpcs = allNpcs.filter(n => !sessionNpcs.some(sn => sn.id === n.id));

  return (
    <Grid container spacing={3}>
      {/* ── Rogues' gallery ─────────────────────────────────────────────── */}
      <Grid size={{ xs: 12, md: 4 }}>
        <Typography variant="h5" gutterBottom>
          🖼️ Rogues' Gallery
        </Typography>
        <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
          <Autocomplete
            sx={{ flexGrow: 1 }}
            options={importableNpcs}
            getOptionLabel={n => n.name}
            value={importTarget}
            onChange={(_, v) => setImportTarget(v)}
            renderInput={params => <TextField {...params} label="Import NPC from another session" size="small" />}
          />
        </Stack>
        <Button
          fullWidth
          size="small"
          variant="outlined"
          onClick={handleImport}
          disabled={!importTarget}
          sx={{ mb: 2 }}
        >
          Pin to this Board
        </Button>

        <List>
          {sessionNpcs.map(npc => (
            <ListItemButton
              key={npc.id}
              selected={selected?.id === npc.id}
              onClick={() => selectNpc(npc)}
              sx={{ border: '1px solid rgba(58,36,22,0.3)', mb: 0.5 }}
            >
              <ListItemText primary={npc.name} secondary={`${npc.motive} · ${npc.mood}`} />
              <IconButton
                size="small"
                onClick={e => {
                  e.stopPropagation();
                  handleRemove(npc.id!);
                }}
              >
                <CloseIcon fontSize="small" />
              </IconButton>
            </ListItemButton>
          ))}
          {sessionNpcs.length === 0 && (
            <Typography color="text.secondary" sx={{ fontStyle: 'italic' }}>
              No NPCs pinned to this session's board yet.
            </Typography>
          )}
        </List>
      </Grid>

      <Grid size={{ xs: 12, md: 1 }} sx={{ display: { xs: 'none', md: 'block' } }}>
        <Divider orientation="vertical" sx={{ height: '100%' }} />
      </Grid>

      {/* ── NPC creation / view form ───────────────────────────────────── */}
      <Grid size={{ xs: 12, md: 7 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
          <Typography variant="h5">🎭 Conjure an NPC</Typography>
          <Button
            variant="contained"
            color="secondary"
            size="large"
            startIcon={<CasinoIcon />}
            onClick={rollRandomNpc}
            sx={{
              borderRadius: '50% / 40%',
              px: 3,
              boxShadow: '0 0 0 3px #7a2e1d, 0 4px 10px rgba(0,0,0,0.4)',
            }}
          >
            Random NPC
          </Button>
        </Stack>

        <TornCard rotate={-0.5} sx={{ maxWidth: 520 }}>
          <Stack spacing={2}>
            <TextField
              label="Name"
              value={draft.name}
              onChange={e => setDraft(d => ({ ...d, name: e.target.value }))}
              fullWidth
            />

            <Stack direction="row" spacing={1} alignItems="center">
              <Autocomplete
                freeSolo
                sx={{ flexGrow: 1 }}
                options={pools.motives}
                value={draft.motive}
                onInputChange={(_, v) => setDraft(d => ({ ...d, motive: v }))}
                renderInput={params => <TextField {...params} label="Motive" size="small" />}
              />
              <IconButton onClick={() => rollField('motive')} title="Roll a random motive">
                <CasinoIcon />
              </IconButton>
            </Stack>

            <Stack direction="row" spacing={1} alignItems="center">
              <TextField
                select
                label="Standing"
                value={draft.status}
                onChange={e => setDraft(d => ({ ...d, status: e.target.value as NpcStatus }))}
                size="small"
                fullWidth
              >
                {pools.statuses.map(s => (
                  <MenuItem key={s} value={s}>
                    {STATUS_LABEL[s]}
                  </MenuItem>
                ))}
              </TextField>
              <IconButton onClick={() => rollField('status')} title="Roll a random standing">
                <CasinoIcon />
              </IconButton>
            </Stack>

            <Stack direction="row" spacing={1} alignItems="center">
              <Autocomplete
                freeSolo
                sx={{ flexGrow: 1 }}
                options={pools.moods}
                value={draft.mood}
                onInputChange={(_, v) => setDraft(d => ({ ...d, mood: v }))}
                renderInput={params => <TextField {...params} label="Mood" size="small" />}
              />
              <IconButton onClick={() => rollField('mood')} title="Roll a random mood">
                <CasinoIcon />
              </IconButton>
            </Stack>

            {draft.motive && draft.mood && (
              <Stack direction="row" spacing={1}>
                <Chip label={draft.motive} color="primary" variant="outlined" />
                <Chip label={STATUS_LABEL[draft.status]} color="secondary" variant="outlined" />
                <Chip label={draft.mood} variant="outlined" />
              </Stack>
            )}

            <Stack direction="row" spacing={1}>
              <Button
                variant="contained"
                startIcon={<SaveIcon />}
                onClick={handleSave}
                disabled={!draft.name.trim() || !draft.motive.trim() || !draft.mood.trim()}
              >
                Pin to Board
              </Button>
              <Button onClick={startNewDraft}>Clear</Button>
            </Stack>
          </Stack>
        </TornCard>
      </Grid>
    </Grid>
  );
}
