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
import type { AttributePools, ChronicleDto, NpcDto, NpcStatus } from '../types';
import {
  getAllNpcs,
  getAttributePools,
  getChronicleNpcs,
  getRandomNpc,
  importNpcIntoChronicle,
  removeNpcFromChronicle,
  saveNpcInChronicle,
} from '../api/client';

interface Props {
  chronicle: ChronicleDto;
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
  originChronicleId: null,
  createdAt: null,
};

export default function NpcTab({ chronicle }: Props) {
  const [pools, setPools] = useState<AttributePools>({ motives: [], moods: [], statuses: ['HIGHER', 'EQUAL', 'LOWER'] });
  const [chronicleNpcs, setChronicleNpcs] = useState<NpcDto[]>([]);
  const [allNpcs, setAllNpcs] = useState<NpcDto[]>([]);
  const [selected, setSelected] = useState<NpcDto | null>(null);
  const [draft, setDraft] = useState<NpcDto>(emptyDraft);
  const [importTarget, setImportTarget] = useState<NpcDto | null>(null);

  const refreshChronicleNpcs = () => getChronicleNpcs(chronicle.id).then(setChronicleNpcs);
  const refreshAllNpcs = () => getAllNpcs().then(setAllNpcs);

  useEffect(() => {
    getAttributePools().then(setPools);
    refreshChronicleNpcs();
    refreshAllNpcs();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chronicle.id]);

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
    await saveNpcInChronicle(chronicle.id, {
      name: draft.name.trim(),
      motive: draft.motive,
      status: draft.status,
      mood: draft.mood,
    });
    startNewDraft();
    refreshChronicleNpcs();
    refreshAllNpcs();
  };

  const handleImport = async () => {
    if (!importTarget?.id) return;
    await importNpcIntoChronicle(chronicle.id, importTarget.id);
    setImportTarget(null);
    refreshChronicleNpcs();
  };

  const handleRemove = async (npcId: string) => {
    await removeNpcFromChronicle(chronicle.id, npcId);
    if (selected?.id === npcId) startNewDraft();
    refreshChronicleNpcs();
  };

  const importableNpcs = allNpcs.filter(npc => !chronicleNpcs.some(linkedNpc => linkedNpc.id === npc.id));

  return (
    <Grid container spacing={3}>
      <Grid size={{ xs: 12, md: 4 }}>
        <Typography variant="h5" gutterBottom>
          🖼️ Rogues' Gallery
        </Typography>
        <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
          <Autocomplete
            sx={{ flexGrow: 1 }}
            options={importableNpcs}
            getOptionLabel={npc => npc.name}
            value={importTarget}
            onChange={(_, value) => setImportTarget(value)}
            renderInput={params => <TextField {...params} label="Import NPC from another chronicle" size="small" />}
          />
        </Stack>
        <Button fullWidth size="small" variant="outlined" onClick={handleImport} disabled={!importTarget} sx={{ mb: 2 }}>
          Pin to this board
        </Button>

        <List>
          {chronicleNpcs.map(npc => (
            <ListItemButton
              key={npc.id}
              selected={selected?.id === npc.id}
              onClick={() => selectNpc(npc)}
              sx={{ border: '1px solid rgba(58,36,22,0.3)', mb: 0.5 }}
            >
              <ListItemText primary={npc.name} secondary={`${npc.motive} · ${npc.mood}`} />
              <IconButton
                size="small"
                onClick={event => {
                  event.stopPropagation();
                  void handleRemove(npc.id!);
                }}
              >
                <CloseIcon fontSize="small" />
              </IconButton>
            </ListItemButton>
          ))}
          {chronicleNpcs.length === 0 && (
            <Typography color="text.secondary" sx={{ fontStyle: 'italic' }}>
              No NPCs pinned to this chronicle yet.
            </Typography>
          )}
        </List>
      </Grid>

      <Grid size={{ xs: 12, md: 1 }} sx={{ display: { xs: 'none', md: 'block' } }}>
        <Divider orientation="vertical" sx={{ height: '100%' }} />
      </Grid>

      <Grid size={{ xs: 12, md: 7 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
          <Typography variant="h5">🎭 Conjure an NPC</Typography>
          <Button variant="contained" color="secondary" size="large" startIcon={<CasinoIcon />} onClick={rollRandomNpc} sx={{ px: 3, boxShadow: 'none' }}>
            Random NPC
          </Button>
        </Stack>

        <TornCard rotate={-0.5} sx={{ maxWidth: 520 }}>
          <Stack spacing={2}>
            <TextField label="Name" value={draft.name} onChange={event => setDraft(d => ({ ...d, name: event.target.value }))} fullWidth />

            <Stack direction="row" spacing={1} alignItems="center">
              <Autocomplete
                freeSolo
                sx={{ flexGrow: 1 }}
                options={pools.motives}
                value={draft.motive}
                onInputChange={(_, value) => setDraft(d => ({ ...d, motive: value }))}
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
                onChange={event => setDraft(d => ({ ...d, status: event.target.value as NpcStatus }))}
                size="small"
                fullWidth
              >
                {pools.statuses.map(status => (
                  <MenuItem key={status} value={status}>
                    {STATUS_LABEL[status]}
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
                onInputChange={(_, value) => setDraft(d => ({ ...d, mood: value }))}
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
                Pin to board
              </Button>
              <Button onClick={startNewDraft}>Clear</Button>
            </Stack>
          </Stack>
        </TornCard>
      </Grid>
    </Grid>
  );
}
