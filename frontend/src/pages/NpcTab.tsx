import { useEffect, useState } from 'react';
import {
  Alert,
  Autocomplete,
  Button,
  CircularProgress,
  Divider,
  Grid,
  IconButton,
  List,
  ListItemButton,
  ListItemText,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import SaveIcon from '@mui/icons-material/Save';
import CloseIcon from '@mui/icons-material/Close';
import TornCard from '../components/TornCard';
import type { ChronicleDto, NpcDto, ServiceStatusDto } from '../types';
import {
  generateNpc,
  getAllNpcs,
  getChronicleNpcs,
  getServiceStatus,
  importNpcIntoChronicle,
  removeNpcFromChronicle,
  saveNpcInChronicle,
} from '../api/client';

interface Props {
  chronicle: ChronicleDto;
}

const emptyDraft: NpcDto = {
  id: null,
  name: '',
  firstImpression: '',
  goal: '',
  attitude: '',
  rulesAndTaboos: '',
  quirks: '',
  originChronicleId: null,
  createdAt: null,
};

export default function NpcTab({ chronicle }: Props) {
  const [chronicleNpcs, setChronicleNpcs] = useState<NpcDto[]>([]);
  const [allNpcs, setAllNpcs] = useState<NpcDto[]>([]);
  const [selected, setSelected] = useState<NpcDto | null>(null);
  const [draft, setDraft] = useState<NpcDto>(emptyDraft);
  const [importTarget, setImportTarget] = useState<NpcDto | null>(null);
  const [description, setDescription] = useState('');
  const [isGenerating, setIsGenerating] = useState(false);
  const [generationError, setGenerationError] = useState<string | null>(null);
  const [serviceStatus, setServiceStatus] = useState<ServiceStatusDto | null>(null);

  const refreshChronicleNpcs = () => getChronicleNpcs(chronicle.id).then(setChronicleNpcs);
  const refreshAllNpcs = () => getAllNpcs().then(setAllNpcs);

  useEffect(() => {
    refreshChronicleNpcs();
    refreshAllNpcs();
    let cancelled = false;
    void getServiceStatus()
      .then(status => {
        if (!cancelled) setServiceStatus(status);
      })
      .catch(() => {
        if (!cancelled) setServiceStatus({ whisperXReachable: false, ollamaReachable: false });
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chronicle.id]);

  const selectNpc = (npc: NpcDto) => {
    setSelected(npc);
    setDraft(npc);
  };

  const startNewDraft = () => {
    setSelected(null);
    setDraft(emptyDraft);
    setDescription('');
    setGenerationError(null);
  };

  const handleGenerate = async () => {
    if (!description.trim()) return;
    setIsGenerating(true);
    setGenerationError(null);
    try {
      const npc = await generateNpc(draft.name, description.trim());
      setSelected(null);
      setDraft(npc);
    } catch {
      setGenerationError('NPC-Generierung fehlgeschlagen. Ist Ollama erreichbar?');
    } finally {
      setIsGenerating(false);
    }
  };

  const handleSave = async () => {
    if (!draft.name.trim() || !draft.firstImpression.trim()) return;
    await saveNpcInChronicle(chronicle.id, {
      name: draft.name.trim(),
      firstImpression: draft.firstImpression,
      goal: draft.goal,
      attitude: draft.attitude,
      rulesAndTaboos: draft.rulesAndTaboos,
      quirks: draft.quirks,
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
  const canGenerate = serviceStatus?.ollamaReachable === true;

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
              <ListItemText primary={npc.name} secondary={npc.goal || npc.attitude} />
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
        <Typography variant="h5" gutterBottom>
          🎭 Conjure an NPC
        </Typography>

        <TornCard rotate={-0.5} sx={{ maxWidth: 560 }}>
          <Stack spacing={2}>
            <TextField label="Name" value={draft.name} onChange={event => setDraft(d => ({ ...d, name: event.target.value }))} fullWidth />

            <Stack direction="row" spacing={1} alignItems="flex-start">
              <TextField
                label="Kurzbeschreibung (z.B. freundlicher Händler)"
                value={description}
                onChange={event => setDescription(event.target.value)}
                size="small"
                fullWidth
              />
              <Button
                variant="contained"
                color="secondary"
                onClick={handleGenerate}
                disabled={!description.trim() || !canGenerate || isGenerating}
                startIcon={isGenerating ? <CircularProgress size={16} color="inherit" /> : <AutoAwesomeIcon />}
                sx={{ whiteSpace: 'nowrap', boxShadow: 'none' }}
              >
                Mit KI generieren
              </Button>
            </Stack>

            {serviceStatus && !canGenerate && (
              <Alert severity="warning">Ollama ist gerade nicht erreichbar — KI-Generierung nicht möglich.</Alert>
            )}
            {generationError && <Alert severity="error">{generationError}</Alert>}

            <TextField
              label="Erster Eindruck (Optik/Stimme)"
              value={draft.firstImpression}
              onChange={event => setDraft(d => ({ ...d, firstImpression: event.target.value }))}
              multiline
              minRows={2}
              fullWidth
            />
            <TextField
              label="Ziel"
              value={draft.goal}
              onChange={event => setDraft(d => ({ ...d, goal: event.target.value }))}
              multiline
              minRows={2}
              fullWidth
            />
            <TextField
              label="Haltung"
              value={draft.attitude}
              onChange={event => setDraft(d => ({ ...d, attitude: event.target.value }))}
              fullWidth
            />
            <TextField
              label="Regeln und Tabus"
              value={draft.rulesAndTaboos}
              onChange={event => setDraft(d => ({ ...d, rulesAndTaboos: event.target.value }))}
              multiline
              minRows={2}
              fullWidth
            />
            <TextField
              label="Eigenheiten und Marotten"
              value={draft.quirks}
              onChange={event => setDraft(d => ({ ...d, quirks: event.target.value }))}
              multiline
              minRows={2}
              fullWidth
            />

            <Stack direction="row" spacing={1}>
              <Button
                variant="contained"
                startIcon={<SaveIcon />}
                onClick={handleSave}
                disabled={!draft.name.trim() || !draft.firstImpression.trim()}
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
